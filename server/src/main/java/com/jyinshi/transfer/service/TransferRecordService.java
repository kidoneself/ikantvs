package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.service.MediaLinkService;
import com.jyinshi.search.util.LinkEncryptUtil;
import com.jyinshi.transfer.config.TransferProperties;
import com.jyinshi.transfer.dto.JobEnqueueRequest;
import com.jyinshi.transfer.dto.TransferExecuteRequest;
import com.jyinshi.transfer.dto.TransferResultVO;
import com.jyinshi.transfer.entity.TransferJob;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.entity.TransferRecord;
import com.jyinshi.transfer.event.JobReportedEvent;
import com.jyinshi.transfer.mapper.TransferMonitorMapper;
import com.jyinshi.transfer.mapper.TransferRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户转存：点转存 → 有有效缓存则直接返回我方链；否则真正转存，写记录供复用与到期清理。
 *
 * <p>缓存过期清理后，下次再点又走转存。迅雷等可永久保留；监控转存的固定夹不参与用户清理。</p>
 */
@Slf4j
@Service
public class TransferRecordService {

    private final TransferRecordMapper recordMapper;
    private final TransferMonitorMapper monitorMapper;
    private final TransferJobService jobService;
    private final MediaLinkService mediaLinkService;
    private final TransferProperties props;
    private final ObjectMapper objectMapper;
    /** @Lazy 打破与 TransferAccountService 的构造循环（后者 requestRemove 依赖本服务）。 */
    private final TransferAccountService accountService;

    public TransferRecordService(TransferRecordMapper recordMapper,
                                 TransferMonitorMapper monitorMapper,
                                 TransferJobService jobService,
                                 MediaLinkService mediaLinkService,
                                 TransferProperties props,
                                 ObjectMapper objectMapper,
                                 @org.springframework.context.annotation.Lazy TransferAccountService accountService) {
        this.recordMapper = recordMapper;
        this.monitorMapper = monitorMapper;
        this.jobService = jobService;
        this.mediaLinkService = mediaLinkService;
        this.props = props;
        this.objectMapper = objectMapper;
        this.accountService = accountService;
    }

    // ==================== 用户侧：点击转存 / 轮询结果 ====================

    /** 转存支持的网盘（其余各盘只展示、不转存）。 */
    private static final java.util.Set<String> TRANSFERABLE = java.util.Set.of("quark", "baidu", "xunlei");

    /**
     * 用户转存：命中缓存秒返回；否则发起转存，返回 jobId 供轮询。
     *
     * <p>支持站内 {@code mediaLinkId}，或流式搜索外源的加密 {@code encryptUrl}（对齐老站）。
     */
    public TransferResultVO execute(TransferExecuteRequest request) {
        if (request == null || !request.isValidTarget()) {
            throw new BizException("请提供转存目标");
        }
        if (request.getMediaLinkId() != null) {
            return executeByLinkId(request.getMediaLinkId());
        }
        return executeByEncrypt(request.getEncryptUrl());
    }

    /** @deprecated 保留给内部/测试；新入口走 {@link #execute(TransferExecuteRequest)}。 */
    public TransferResultVO execute(Long mediaLinkId) {
        return executeByLinkId(mediaLinkId);
    }

    private TransferResultVO executeByLinkId(Long mediaLinkId) {
        MediaLinkService.TransferSource src = mediaLinkService.resolveTransferSource(mediaLinkId);
        String shareUrl = src.shareUrl();
        String password = src.password();
        if ("self".equalsIgnoreCase(src.source())) {
            // 赚钱铁律：站长精选只出我方稳定链。写入侧保证 self 的 url 只存我方分享，
            // 未首转前留空（绝不写上游），故空即视为不可用。
            if (!StringUtils.hasText(shareUrl)) {
                throw new BizException("链接不存在或已失效");
            }
            return TransferResultVO.done(shareUrl, password);
        }
        if (!StringUtils.hasText(shareUrl)) {
            throw new BizException("链接不存在或已失效");
        }
        String pan = StringUtils.hasText(src.panType()) ? src.panType().toLowerCase(Locale.ROOT) : "";
        return enqueueOrReuse(pan, shareUrl, password, mediaLinkId);
    }

    private TransferResultVO executeByEncrypt(String encryptUrl) {
        String[] decrypted;
        try {
            decrypted = LinkEncryptUtil.decrypt(encryptUrl.trim());
        } catch (Exception e) {
            throw new BizException("链接无效或已过期");
        }
        String pan = decrypted[0] != null ? decrypted[0].toLowerCase(Locale.ROOT) : "";
        String shareUrl = decrypted[1];
        String password = decrypted.length > 2 && StringUtils.hasText(decrypted[2]) ? decrypted[2] : null;
        if (!StringUtils.hasText(shareUrl)) {
            throw new BizException("链接无效或已过期");
        }
        return enqueueOrReuse(pan, shareUrl, password, null);
    }

    private TransferResultVO enqueueOrReuse(String pan, String shareUrl, String password, Long mediaLinkId) {
        if (!TRANSFERABLE.contains(pan)) {
            throw new BizException("暂不支持「" + PanUrlDetector.label(pan) + "」网盘转存，目前仅支持夸克/百度/迅雷");
        }
        String shareId = ShareIdExtractor.extract(shareUrl);

        TransferRecord rec = findActive(pan, shareId);
        if (rec != null && isReusable(rec)) {
            return TransferResultVO.done(rec.getMyShareUrl(), rec.getMySharePwd());
        }

        TransferJob inflight = jobService.findActiveByShareUrl(shareUrl, "transfer");
        if (inflight != null) {
            return TransferResultVO.transferring(inflight.getId());
        }

        if (!accountService.hasUsableTransferAccount(pan)) {
            throw new BizException("「" + PanUrlDetector.label(pan) + "」转存服务正在维护，请稍后再试");
        }

        JobEnqueueRequest req = new JobEnqueueRequest();
        req.setJobType("transfer");
        req.setPanType(pan);
        req.setShareUrl(shareUrl);
        req.setSharePwd(password);
        req.setMediaLinkId(mediaLinkId);
        req.setLandingDir(props.getUserTransfer().getLandingDir());
        req.setPriority(8);
        TransferJob job = jobService.enqueue(req);
        log.info("[转存耗时] 用户点击转存 job={} pan={} shareId={}", job.getId(), pan, shareId);
        return TransferResultVO.transferring(job.getId());
    }

    /** 轮询转存结果。 */
    public TransferResultVO result(Long jobId) {
        TransferJob job = jobService.getById(jobId);
        if (job == null) {
            throw new BizException("转存任务不存在");
        }
        return switch (job.getStatus()) {
            case "done" -> TransferResultVO.done(job.getResultShareUrl(),
                    readString(job.getResultJson(), "myPassword"));
            case "failed" -> TransferResultVO.failed(
                    StringUtils.hasText(job.getErrorMsg()) ? job.getErrorMsg() : "转存失败，链接可能已失效");
            default -> TransferResultVO.transferring(jobId);
        };
    }

    // ==================== 转存完成 → 写记录（缓存/复用/清理依据） ====================

    @EventListener
    public void onJobReported(JobReportedEvent evt) {
        TransferJob job = evt.job();
        if ("transfer".equals(job.getJobType()) || "create".equals(job.getJobType())) {
            if ("done".equals(job.getStatus()) && StringUtils.hasText(job.getResultShareUrl())) {
                upsertRecord(job);
            } else if ("failed".equals(job.getStatus()) && "transfer".equals(job.getJobType())) {
                markSourceLinkInvalidIfNeeded(job);
            }
            return;
        }
        if ("delete".equals(job.getJobType())) {
            applyDeleteResult(job);
        }
    }

    /**
     * 批量清理(delete)任务回报：按 delete_job_id 找到这批记录一起回写。
     * 整批成功置 deleted；彻底失败置 delete_failed（文件仍在，供人工处理，不再自动重试）。
     */
    private void applyDeleteResult(TransferJob job) {
        boolean done = "done".equals(job.getStatus());
        boolean failed = "failed".equals(job.getStatus());
        if (!done && !failed) {
            return; // 仍在重试中（pending），先不动记录
        }
        List<TransferRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<TransferRecord>()
                .eq(TransferRecord::getDeleteJobId, job.getId()));
        if (recs.isEmpty()) {
            return;
        }
        String status = done ? "deleted" : "delete_failed";
        for (TransferRecord rec : recs) {
            rec.setStatus(status);
            recordMapper.updateById(rec);
        }
        if (failed) {
            log.warn("[转存] 清理批次失败(网盘文件仍在，需人工处理) jobId={}, pan={}, 文件数={}, 原因={}",
                    job.getId(), job.getPanType(), recs.size(), job.getErrorMsg());
        } else {
            log.info("[转存] 清理批次完成 jobId={}, pan={}, 删除 {} 个", job.getId(), job.getPanType(), recs.size());
        }
    }

    private void upsertRecord(TransferJob job) {
        String pan = job.getPanType();
        String shareId = ShareIdExtractor.extract(job.getShareUrl());
        // 永久保留：迅雷等；或已启用监控转存的固定夹（不参与用户转存清理）。
        // 详情页转存也会带 mediaLinkId，不能据此判永久——须查 transfer_monitor。
        boolean permanent = props.getUserTransfer().getPermanentPanTypes().contains(pan)
                || isMonitorManaged(job.getMediaLinkId());
        LocalDateTime now = LocalDateTime.now();

        TransferRecord rec = findActive(pan, shareId);
        boolean isNew = (rec == null);
        if (isNew) {
            rec = new TransferRecord();
            rec.setPanType(pan);
            rec.setShareId(shareId);
        }
        rec.setShareUrl(job.getShareUrl());
        rec.setSharePwd(job.getSharePwd());
        rec.setMyShareUrl(job.getResultShareUrl());
        rec.setMySharePwd(readString(job.getResultJson(), "myPassword"));
        rec.setFolderId(job.getResultFolderId());
        // 记住转存用的账号：结果里的 accountName 优先，兜底 job 指定的
        String acct = readString(job.getResultJson(), "accountName");
        rec.setAccountName(StringUtils.hasText(acct) ? acct : job.getAccountName());
        rec.setStatus("active");
        rec.setIsPermanent(permanent);
        rec.setTransferTime(now);
        rec.setExpireTime(permanent ? null
                : now.plusMinutes(props.getUserTransfer().getRetentionMinutes()));
        try {
            if (isNew) {
                recordMapper.insert(rec);
            } else {
                recordMapper.updateById(rec);
            }
        } catch (DuplicateKeyException dup) {
            // 并发转存竞态：另一条已写入，改为更新
            TransferRecord exist = findActive(pan, shareId);
            if (exist != null) {
                rec.setId(exist.getId());
                recordMapper.updateById(rec);
            }
        }
        log.info("[用户转存] 记录写入 pan={}, shareId={}, 我方链={}, 永久={}", pan, shareId,
                job.getResultShareUrl(), permanent);
    }

    /** 转存因链接/分享本身失效而失败 → 标记 media_link.invalid，详情页不再展示。 */
    private void markSourceLinkInvalidIfNeeded(TransferJob job) {
        String errorCode = TransferLinkFailureCodes.extractErrorCode(objectMapper, job.getResultJson());
        if (!TransferLinkFailureCodes.isLinkFailure(errorCode)) {
            return;
        }
        // 传原始 shareUrl，由 content 域用它自己的 share_id 规则匹配，避免两域算法不一致导致漏标。
        mediaLinkService.markInvalidFromTransferFailure(
                job.getMediaLinkId(), job.getPanType(), job.getShareUrl(), job.getErrorMsg());
    }

    /**
     * 放弃某账号名下所有「还没删掉」的记录（active / deleting / delete_failed）→ 置 abandoned。
     *
     * <p>用于账号被封/删除：云端资源已无法通过该号删除，标记收尾即可——既不再入队清理任务，
     * 也不留 delete_failed 噪音，命中缓存时也不会再复用这些已失效的记录。返回受影响条数。</p>
     */
    public int abandonByAccount(String panType, String accountName) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(accountName)) {
            return 0;
        }
        List<TransferRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<TransferRecord>()
                .eq(TransferRecord::getPanType, panType)
                .eq(TransferRecord::getAccountName, accountName)
                .in(TransferRecord::getStatus, "active", "deleting", "delete_failed"));
        for (TransferRecord rec : recs) {
            rec.setStatus("abandoned");
            recordMapper.updateById(rec);
        }
        if (!recs.isEmpty()) {
            log.info("[转存] 账号 {}/{} 已删除，放弃其 {} 条资源记录（不再尝试删除）",
                    panType, accountName, recs.size());
        }
        return recs.size();
    }

    // ==================== 定时清理：删过期非永久记录的网盘文件 ====================

    @Scheduled(cron = "${jyinshi.transfer.user-transfer.cleanup-cron:0 */30 * * * *}")
    public void cleanupExpired() {
        reclaimStuckDeleting();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<TransferRecord> qw = new LambdaQueryWrapper<TransferRecord>()
                .eq(TransferRecord::getStatus, "active")
                .eq(TransferRecord::getIsPermanent, false)
                .isNotNull(TransferRecord::getFolderId)
                .le(TransferRecord::getExpireTime, now)
                .last("LIMIT " + props.getUserTransfer().getCleanupBatchLimit());
        List<TransferRecord> expired = recordMapper.selectList(qw);
        if (expired.isEmpty()) {
            return;
        }
        // 按「网盘 + 账号」攒批：一个 delete 任务只删一个号名下的夹，避免用别的号去删（删不掉）
        Map<String, List<TransferRecord>> byPanAccount = expired.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getPanType() + "\u0000" + (r.getAccountName() == null ? "" : r.getAccountName())));
        int batches = 0;
        for (List<TransferRecord> group : byPanAccount.values()) {
            String panType = group.get(0).getPanType();
            String accountName = group.get(0).getAccountName();
            List<String> folderIds = group.stream().map(TransferRecord::getFolderId).toList();
            TransferJob job = jobService.enqueueDeleteBatch(panType, accountName, folderIds);
            if (job == null) {
                continue;
            }
            // 先置 deleting + 绑定 job（防重复入队）；回报后按 job 批量回写 deleted / delete_failed
            for (TransferRecord rec : group) {
                rec.setStatus("deleting");
                rec.setDeleteJobId(job.getId());
                recordMapper.updateById(rec);
            }
            batches++;
            log.info("[转存] 清理入队批次 pan={}, account={}, 文件数={}, jobId={}",
                    panType, accountName, folderIds.size(), job.getId());
        }
        log.info("[转存] 本轮清理 {} 批, 共 {} 个过期文件", batches, expired.size());
    }

    // ==================== 内部 ====================

    /**
     * 把卡住的 deleting 记录退回 active，让下一轮清理重新入队。
     * 常见原因：进程挂掉 / 调度停摆时 delete 任务已结束但记录没回写，或 job 丢了。
     */
    private void reclaimStuckDeleting() {
        LocalDateTime staleBefore = LocalDateTime.now().minusHours(1);
        List<TransferRecord> stuck = recordMapper.selectList(new LambdaQueryWrapper<TransferRecord>()
                .eq(TransferRecord::getStatus, "deleting")
                .eq(TransferRecord::getIsPermanent, false)
                .le(TransferRecord::getUpdatedAt, staleBefore)
                .last("LIMIT 200"));
        if (stuck.isEmpty()) {
            return;
        }
        int n = 0;
        for (TransferRecord rec : stuck) {
            TransferJob job = rec.getDeleteJobId() != null ? jobService.getById(rec.getDeleteJobId()) : null;
            // job 还在跑（pending/running）→ 再等等；否则退回 active 重清
            if (job != null && ("pending".equals(job.getStatus()) || "running".equals(job.getStatus()))) {
                continue;
            }
            rec.setStatus("active");
            rec.setDeleteJobId(null);
            recordMapper.updateById(rec);
            n++;
        }
        if (n > 0) {
            log.warn("[转存] 回收卡住的 deleting 记录 {} 条，将重新入队清理", n);
        }
    }

    /** 该 media_link 是否已启用监控转存（固定夹由监控管生命周期，不参与用户转存清理）。 */
    private boolean isMonitorManaged(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return false;
        }
        Long n = monitorMapper.selectCount(new LambdaQueryWrapper<TransferMonitor>()
                .eq(TransferMonitor::getMediaLinkId, mediaLinkId)
                .eq(TransferMonitor::getEnabled, true)
                .eq(TransferMonitor::getStatus, "active"));
        return n != null && n > 0;
    }

    private TransferRecord findActive(String panType, String shareId) {
        return recordMapper.selectOne(new LambdaQueryWrapper<TransferRecord>()
                .eq(TransferRecord::getPanType, panType)
                .eq(TransferRecord::getShareId, shareId)
                .last("LIMIT 1"));
    }

    private boolean isReusable(TransferRecord rec) {
        if (!"active".equals(rec.getStatus()) || !StringUtils.hasText(rec.getMyShareUrl())) {
            return false;
        }
        if (Boolean.TRUE.equals(rec.getIsPermanent())) {
            return true;
        }
        return rec.getExpireTime() == null || rec.getExpireTime().isAfter(LocalDateTime.now());
    }

    private String readString(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
