package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.config.TransferProperties;
import com.jyinshi.transfer.dto.JobEnqueueRequest;
import com.jyinshi.transfer.dto.WorkerJobView;
import com.jyinshi.transfer.dto.WorkerReportRequest;
import com.jyinshi.transfer.entity.TransferJob;
import com.jyinshi.transfer.event.JobEnqueuedEvent;
import com.jyinshi.transfer.event.JobReportedEvent;
import com.jyinshi.transfer.mapper.TransferJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

/**
 * 转存/追更任务编排：入队、派发（claim）、回报（report）、租约回收。
 *
 * <p>合并后主站与执行同进程：{@code PanJobRunner} 轮询领任务执行后回报，无网络往返。</p>
 */
@Slf4j
@Service
public class TransferJobService {

    private static final List<String> JOB_TYPES = List.of("check", "create", "update", "transfer", "delete");

    private final TransferJobMapper jobMapper;
    private final TransferProperties props;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public TransferJobService(TransferJobMapper jobMapper,
                              TransferProperties props,
                              ApplicationEventPublisher events,
                              ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.props = props;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** 某 media_link 是否已有同类型未完成任务（供追更去重）。 */
    public boolean hasActiveJob(Long mediaLinkId, String jobType) {
        return mediaLinkId != null && jobMapper.countActive(mediaLinkId, jobType) > 0;
    }

    /** 入队一个任务。 */
    public TransferJob enqueue(JobEnqueueRequest req) {
        if (!StringUtils.hasText(req.getJobType()) || !JOB_TYPES.contains(req.getJobType())) {
            throw new BizException("非法 jobType，仅支持 " + JOB_TYPES);
        }
        if (!StringUtils.hasText(req.getPanType())) {
            throw new BizException("panType 不能为空");
        }
        boolean isDelete = "delete".equals(req.getJobType());
        // delete 靠 targetFolderId 定位，不需要 shareUrl
        if (!isDelete && !StringUtils.hasText(req.getShareUrl())) {
            throw new BizException("shareUrl 不能为空");
        }
        if ("update".equals(req.getJobType()) && !StringUtils.hasText(req.getTargetFolderId())) {
            throw new BizException("update(监控更新) 任务必须带 targetFolderId");
        }
        if (isDelete && !StringUtils.hasText(req.getTargetFolderId())) {
            throw new BizException("delete(清理) 任务必须带 targetFolderId");
        }
        TransferJob job = new TransferJob();
        job.setJobType(req.getJobType());
        job.setPanType(req.getPanType().toLowerCase());
        job.setAccountName(req.getAccountName());
        job.setShareUrl(req.getShareUrl());
        job.setSharePwd(req.getSharePwd());
        job.setMediaLinkId(req.getMediaLinkId());
        job.setTargetFolderId(req.getTargetFolderId());
        job.setLandingDir(req.getLandingDir());
        job.setStatus("pending");
        job.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setAvailableAt(LocalDateTime.now());
        jobMapper.insert(job);
        log.info("[转存耗时] 任务入队 job={} jobType={} pan={}", job.getId(), job.getJobType(), job.getPanType());
        events.publishEvent(new JobEnqueuedEvent(job.getPanType()));
        return job;
    }

    /**
     * 入队一个「批量清理」任务：同网盘多个落地夹合并成一个 delete 任务一起删。
     * <p>ids 装在 result_json 的 {@code {"ids":[...]}} 里下发给 worker，worker 一次批量删除接口调用
     * （夸克 filelist / 百度 filemanager / 迅雷 batchDelete）——既省任务数，又天然规避频繁删除风控。</p>
     *
     * @param panType     quark/baidu/xunlei
     * @param accountName 落地夹所属账号（用回同一个号删，避免删错号）；为空则池选
     * @param folderIds   待删落地夹 id 列表（百度为路径）
     * @return 入队的 delete 任务（含 id，供 record 对账绑定）
     */
    public TransferJob enqueueDeleteBatch(String panType, String accountName, List<String> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return null;
        }
        TransferJob job = new TransferJob();
        job.setJobType("delete");
        job.setPanType(panType.toLowerCase());
        job.setAccountName(accountName);
        // share_url 非空约束：delete 无源链，占位便于排查
        job.setShareUrl("delete-batch:" + folderIds.size());
        // target_folder_id 存首个便于肉眼排查；完整列表在 result_json.ids
        job.setTargetFolderId(folderIds.get(0));
        job.setResultJson(writeIds(folderIds));
        job.setStatus("pending");
        job.setPriority(1);
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setAvailableAt(LocalDateTime.now());
        jobMapper.insert(job);
        events.publishEvent(new JobEnqueuedEvent(job.getPanType()));
        return job;
    }

    private String writeIds(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("ids", ids));
        } catch (Exception e) {
            throw new BizException("清理任务 ids 序列化失败: " + e.getMessage());
        }
    }

    /** 查按分享链在途的同类型任务（用户转存去重）；无则返回 null。 */
    public TransferJob findActiveByShareUrl(String shareUrl, String jobType) {
        if (!StringUtils.hasText(shareUrl)) {
            return null;
        }
        return jobMapper.findActiveByShareUrl(shareUrl, jobType);
    }

    /** 按 id 查任务（用户轮询转存结果用）。 */
    public TransferJob getById(Long id) {
        return id == null ? null : jobMapper.selectById(id);
    }

    /**
     * worker 领取一条任务。加事务保证 {@code FOR UPDATE SKIP LOCKED} 行锁在更新前不释放。
     *
     * @return 领到的任务视图；无可领任务返回 null
     */
    @Transactional
    public WorkerJobView claim(String workerId, List<String> panTypes) {
        if (panTypes == null || panTypes.isEmpty()) {
            return null;
        }
        String csv = String.join(",", panTypes.stream().map(String::toLowerCase).toList());
        LocalDateTime now = LocalDateTime.now();
        TransferJob job = jobMapper.selectClaimable(csv, now);
        if (job == null) {
            return null;
        }
        job.setStatus("running");
        job.setWorkerId(workerId);
        job.setAttempts(job.getAttempts() + 1);
        job.setLeaseUntil(now.plusSeconds(props.getLeaseSeconds()));
        jobMapper.updateById(job);
        if (job.getCreatedAt() != null) {
            long queueMs = Duration.between(job.getCreatedAt(), now).toMillis();
            log.info("[转存耗时] worker领任务 job={} jobType={} pan={} worker={} queue={}ms attempt={}",
                    job.getId(), job.getJobType(), job.getPanType(), workerId, queueMs, job.getAttempts());
        }
        return WorkerJobView.of(job);
    }

    /** worker 回报结果。成功置 done；失败按重试次数决定重派或 failed。 */
    public void report(WorkerReportRequest req) {
        if (req.getJobId() == null) {
            throw new BizException("jobId 不能为空");
        }
        TransferJob job = jobMapper.selectById(req.getJobId());
        if (job == null) {
            throw new BizException("任务不存在: " + req.getJobId());
        }
        if (!"running".equals(job.getStatus())) {
            // 可能已被租约回收/重派；忽略过期回报，避免覆盖新状态
            log.warn("[transfer] 忽略非 running 任务的回报: jobId={}, status={}", job.getId(), job.getStatus());
            return;
        }
        job.setResultJson(req.getResultJson());
        job.setLeaseUntil(null);
        if (req.isSuccess()) {
            job.setStatus("done");
            job.setResultShareUrl(req.getResultShareUrl());
            job.setResultFolderId(req.getResultFolderId());
            job.setErrorMsg(null);
        } else {
            job.setErrorMsg(truncate(req.getErrorMsg(), 500));
            boolean terminal = isTerminalError(req.getResultJson());
            if (terminal || job.getAttempts() >= job.getMaxAttempts()) {
                // 确定性失败（死链/提取码错/分享失效/无文件/授权失效…）重试也是同样结果，直接置失败，
                // 别让用户干等 3 次退避（原来最坏 ~3 分钟）。
                job.setStatus("failed");
            } else {
                job.setStatus("pending");
                job.setWorkerId(null);
                job.setAvailableAt(LocalDateTime.now().plusSeconds(retryBackoffSeconds(job)));
            }
        }
        jobMapper.updateById(job);
        if (job.getCreatedAt() != null) {
            long totalMs = Duration.between(job.getCreatedAt(), LocalDateTime.now()).toMillis();
            log.info("[转存耗时] 主站闭环 job={} jobType={} pan={} worker={} ok={} total={}ms attempts={} err={}",
                    job.getId(), job.getJobType(), job.getPanType(), job.getWorkerId(),
                    req.isSuccess(), totalMs, job.getAttempts(),
                    req.isSuccess() ? "-" : truncate(req.getErrorMsg(), 120));
        }
        // 通知监控转存闭环（check→比对→create/update）
        events.publishEvent(new JobReportedEvent(job, req.isSuccess()));
    }

    /** 后台分页查任务。 */
    public PageResult<TransferJob> pageJobs(long page, long size, String status, String panType) {
        LambdaQueryWrapper<TransferJob> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(TransferJob::getStatus, status);
        }
        if (StringUtils.hasText(panType)) {
            qw.eq(TransferJob::getPanType, panType.toLowerCase());
        }
        qw.orderByDesc(TransferJob::getId);
        Page<TransferJob> p = jobMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }

    /** 每 30 秒回收租约超时的任务。 */
    @Scheduled(fixedDelay = 30_000)
    public void reapExpiredLeases() {
        int n = jobMapper.requeueExpired(LocalDateTime.now());
        if (n > 0) {
            log.warn("[transfer] 回收租约超时任务 {} 条", n);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 确定性错误码：重试结果必然相同（链接/分享/凭据层面的硬失败），直接失败不退避重试。
     * 其余（网络抖动、建夹/转存请求失败等）才走退避重试。
     */
    private static final java.util.Set<String> TERMINAL_ERROR_CODES = java.util.Set.of(
            "INVALID_URL", "VERIFY_FAILED", "SHARE_INVALID", "NO_FILES",
            "LIST_FAILED", "TOKEN_FAILED", "DETAIL_FAILED",
            "AUTH_FAILED", "NO_COOKIE", "NO_ACCOUNT", "UNSUPPORTED");

    private boolean isTerminalError(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    objectMapper.readTree(resultJson).get("errorCode");
            return node != null && !node.isNull()
                    && TERMINAL_ERROR_CODES.contains(node.asText());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重试退避秒数：用户转存 / 监控创建要快，瞬时抖动用短退避（3s×次）；
     * 检查/更新/清理用配置的长退避。
     */
    private long retryBackoffSeconds(TransferJob job) {
        int attempts = job.getAttempts() == null ? 1 : job.getAttempts();
        String t = job.getJobType();
        if ("transfer".equals(t) || "create".equals(t)) {
            return 3L * attempts;
        }
        return (long) props.getRetryBackoffSeconds() * attempts;
    }
}
