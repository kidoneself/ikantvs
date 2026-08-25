package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.service.EpisodeExtractor;
import com.jyinshi.transfer.config.TransferProperties;
import com.jyinshi.transfer.dto.JobEnqueueRequest;
import com.jyinshi.transfer.dto.ProbeSnapshot;
import com.jyinshi.transfer.entity.TransferJob;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.event.JobReportedEvent;
import com.jyinshi.transfer.mapper.TransferMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控转存闭环：到点检查源 → 无固定夹则「创建」、有更新则「更新」。
 *
 * <p>产品只有「监控转存」一种行为；内部步骤：check(检查) → create(创建) / update(更新)。
 * 账号永远取该盘当前「追更号」，不选手动号。</p>
 *
 * <p>调度：每 5 分钟 tick；活跃时段按间隔检查；只有源快照变大才动账号做更新。</p>
 */
@Slf4j
@Service
public class TransferMonitorService {

    private static final int MAX_FAIL = 3;

    private final TransferMonitorMapper monitorMapper;
    private final TransferJobService jobService;
    private final TransferAccountService accountService;
    private final TransferProperties props;
    private final ObjectMapper objectMapper;
    private final com.jyinshi.transfer.notify.NotifyPort notify;
    private final org.springframework.context.ApplicationEventPublisher events;

    public TransferMonitorService(TransferMonitorMapper monitorMapper,
                                  TransferJobService jobService,
                                  TransferAccountService accountService,
                                  TransferProperties props,
                                  ObjectMapper objectMapper,
                                  com.jyinshi.transfer.notify.NotifyPort notify,
                                  org.springframework.context.ApplicationEventPublisher events) {
        this.monitorMapper = monitorMapper;
        this.jobService = jobService;
        this.accountService = accountService;
        this.props = props;
        this.objectMapper = objectMapper;
        this.notify = notify;
        this.events = events;
    }

    // ==================== 启用 / 查询 / 手动补扫 ====================

    /** 启用（或更新）一条链接的监控转存。 */
    public TransferMonitor enable(Long mediaLinkId, String panType, String shareUrl, String sharePwd) {
        return enable(mediaLinkId, panType, shareUrl, sharePwd, null, null);
    }

    /**
     * 启用（或更新）一条链接的监控转存。
     *
     * @param accountNameIgnored 已废弃：监控固定走该盘追更号，忽略传入值。
     */
    public TransferMonitor enable(Long mediaLinkId, String panType, String shareUrl, String sharePwd,
                                  String accountNameIgnored) {
        return enable(mediaLinkId, panType, shareUrl, sharePwd, accountNameIgnored, null);
    }

    /**
     * @param mediaId 所属剧 id（保留入参兼容调用方；当前不再绑定落地夹）
     */
    public TransferMonitor enable(Long mediaLinkId, String panType, String shareUrl, String sharePwd,
                                  String accountNameIgnored, Long mediaId) {
        if (mediaLinkId == null) {
            throw new BizException("mediaLinkId 不能为空");
        }
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(shareUrl)) {
            throw new BizException("panType / shareUrl 不能为空");
        }
        String pan = panType.toLowerCase();
        String monitorAcct = accountService.monitorAccountName(pan);
        if (!StringUtils.hasText(monitorAcct)) {
            throw new BizException("「" + pan + "」未配置追更号，请到转存 → 网盘账号页指定，无法开启监控转存");
        }
        TransferMonitor m = findByMediaLink(mediaLinkId);
        boolean isNew = (m == null);
        if (isNew) {
            m = new TransferMonitor();
            m.setMediaLinkId(mediaLinkId);
        }
        // 换源：源链变了，之前的固定夹/我方链/进度作废，重新创建
        boolean sourceChanged = !isNew && StringUtils.hasText(m.getShareUrl()) && !m.getShareUrl().equals(shareUrl);
        // 账号纠正：历史上误绑转存号时，旧夹不在监控号上，必须作废重建
        boolean accountCorrected = !isNew && StringUtils.hasText(m.getAccountName())
                && !monitorAcct.equals(m.getAccountName());
        if (sourceChanged || accountCorrected) {
            m.setTargetFolderId(null);
            m.setMyShareUrl(null);
            m.setLastUpdatedAt(null);
            m.setLastFileCount(null);
            m.setLatestEpisode(null);
        }
        m.setPanType(pan);
        m.setShareUrl(shareUrl);
        m.setAccountName(monitorAcct);
        // 提取码留空时不覆盖已有值（编辑态前端不回填提取码）
        if (StringUtils.hasText(sharePwd) || isNew) {
            m.setSharePwd(sharePwd);
        }
        m.setEnabled(true);
        m.setStatus("active");

        if (isNew) {
            m.setFailCount(0);
            monitorMapper.insert(m);
        } else {
            monitorMapper.updateById(m);
        }
        // 启用即刻检查一次：无固定夹会走「创建」，有更新会走「更新」
        enqueueProbe(m);
        return m;
    }

    /** 删除某链接的追更（每日更新移除上游链时用）。仅停监控、删记录，不动已转存文件。 */
    public void removeByMediaLink(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return;
        }
        monitorMapper.delete(new LambdaQueryWrapper<TransferMonitor>()
                .eq(TransferMonitor::getMediaLinkId, mediaLinkId));
    }

    /**
     * 暂停追更（每日更新标「完结」用）：停巡检，保留固定夹/我方链/账号绑定。
     * invalid 状态保留不动，便于之后仍能看出源挂了。
     */
    public void pauseByMediaLink(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return;
        }
        TransferMonitor m = findByMediaLink(mediaLinkId);
        if (m == null) {
            return;
        }
        m.setEnabled(false);
        if (!"invalid".equals(m.getStatus())) {
            m.setStatus("paused");
        }
        monitorMapper.updateById(m);
    }

    /**
     * 恢复追更（取消完结用）：重新启用巡检；原先 paused 的改回 active，invalid 保持。
     * 不自动入队 probe，由运营点「立即检查」或等巡检。
     */
    public void resumeByMediaLink(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return;
        }
        TransferMonitor m = findByMediaLink(mediaLinkId);
        if (m == null) {
            return;
        }
        m.setEnabled(true);
        if ("paused".equals(m.getStatus())) {
            m.setStatus("active");
        }
        monitorMapper.updateById(m);
    }

    /** 跨域只读：按 media_link id 批量取追更链视图（content 每日更新看板展示用）。 */
    public java.util.Map<Long, com.jyinshi.transfer.dto.MonitorLinkView> viewsByMediaLinkIds(
            java.util.Collection<Long> mediaLinkIds) {
        if (mediaLinkIds == null || mediaLinkIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<TransferMonitor> list = monitorMapper.selectList(new LambdaQueryWrapper<TransferMonitor>()
                .in(TransferMonitor::getMediaLinkId, mediaLinkIds));
        java.util.Map<Long, com.jyinshi.transfer.dto.MonitorLinkView> out = new java.util.LinkedHashMap<>();
        for (TransferMonitor m : list) {
            out.put(m.getMediaLinkId(), com.jyinshi.transfer.dto.MonitorLinkView.of(m));
        }
        return out;
    }

    /** 分页查看监控。 */
    public PageResult<TransferMonitor> page(long page, long size, String status) {
        LambdaQueryWrapper<TransferMonitor> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(TransferMonitor::getStatus, status);
        }
        qw.orderByDesc(TransferMonitor::getId);
        Page<TransferMonitor> p = monitorMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }

    /**
     * 设置某条链接追更的每剧节奏（每日更新看板保存/编辑时用）。空值表示沿用全局巡检。
     *
     * @param checkDays 0-6 周日到周六逗号分隔；null/空=每天
     * @param checkHours "起-止" 小时（止不含），如 "18-23"；null/空=用全局时段
     * @param checkInterval 检查间隔分钟；null=用全局间隔
     */
    public void updateSchedule(Long mediaLinkId, String checkDays, String checkHours, Integer checkInterval) {
        if (mediaLinkId == null) {
            return;
        }
        TransferMonitor m = findByMediaLink(mediaLinkId);
        if (m == null) {
            return;
        }
        m.setCheckDays(StringUtils.hasText(checkDays) ? checkDays.trim() : null);
        m.setCheckHours(StringUtils.hasText(checkHours) ? checkHours.trim() : null);
        m.setCheckInterval(checkInterval);
        monitorMapper.updateById(m);
    }

    /**
     * 立即给某部剧的一条追更链入队一轮 probe（看板「立即检查」用，无视时段）。
     * 已有固定夹时再入队 update：即便源未变也会整夹重算 latest_episode（校正误识别）。
     */
    public boolean probeByMediaLink(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return false;
        }
        TransferMonitor m = findByMediaLink(mediaLinkId);
        if (m == null) {
            return false;
        }
        boolean ok = enqueueProbe(m);
        if (StringUtils.hasText(m.getTargetFolderId())
                && !jobService.hasActiveJob(m.getMediaLinkId(), "update")) {
            enqueueSave(m, "update", m.getTargetFolderId());
        }
        return ok;
    }

    /** 立即给所有启用监控入队一轮 probe（"想补一下"手动触发，无视时段）。 */
    public int sweep() {
        List<TransferMonitor> list = activeMonitors();
        int n = 0;
        for (TransferMonitor m : list) {
            if (enqueueProbe(m)) {
                n++;
            }
        }
        log.info("[追更] 手动补扫入队 {} 条 probe", n);
        return n;
    }

    // ==================== 定时巡检 ====================

    @Scheduled(cron = "${jyinshi.transfer.monitor.tick-cron:0 */5 * * * *}")
    public void tick() {
        TransferProperties.Monitor cfg = props.getMonitor();
        if (!cfg.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean extra = isExtraWindow(now, cfg);
        int enq = 0;
        for (TransferMonitor m : activeMonitors()) {
            if (enq >= cfg.getBatchLimit()) {
                break;
            }
            if (due(m, now, extra, cfg) && enqueueProbe(m)) {
                enq++;
            }
        }
        if (enq > 0) {
            log.info("[追更] 本轮入队 {} 条 probe (extra={})", enq, extra);
        }
    }

    private boolean due(TransferMonitor m, LocalDateTime now, boolean extra, TransferProperties.Monitor cfg) {
        long sinceProbe = m.getLastProbeAt() == null
                ? Long.MAX_VALUE
                : Duration.between(m.getLastProbeAt(), now).toMinutes();
        if (extra) {
            return sinceProbe >= cfg.getExtraCheckMinIntervalMinutes();
        }
        // 每剧自定义节奏优先：设了 checkHours 就只按它来（到点才查）；没设则沿用全局巡检时段。
        if (StringUtils.hasText(m.getCheckHours())) {
            if (!inMonitorDays(m.getCheckDays(), now) || !inHourRange(m.getCheckHours(), now)) {
                return false;
            }
            int interval = m.getCheckInterval() != null && m.getCheckInterval() > 0
                    ? m.getCheckInterval() : cfg.getWindowIntervalMinutes();
            return sinceProbe >= interval;
        }
        if (inAnyWindow(now, cfg)) {
            return sinceProbe >= cfg.getWindowIntervalMinutes();
        }
        return false;
    }

    /** 每剧检查日（0-6 周日到周六，逗号分隔）；空=每天。 */
    private boolean inMonitorDays(String checkDays, LocalDateTime now) {
        if (!StringUtils.hasText(checkDays)) {
            return true;
        }
        int dow = now.getDayOfWeek().getValue(); // 1-7 (周一~周日)
        int idx = dow == 7 ? 0 : dow;            // 转 0-6（周日=0）
        for (String d : checkDays.split(",")) {
            try {
                if (Integer.parseInt(d.trim()) == idx) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
                // 忽略非法项
            }
        }
        return false;
    }

    /** 单个 "起-止" 时段判断（止不含）。 */
    private boolean inHourRange(String range, LocalDateTime now) {
        try {
            String[] p = range.split("-");
            int start = Integer.parseInt(p[0].trim());
            int end = Integer.parseInt(p[1].trim());
            int hour = now.getHour();
            return hour >= start && hour < end;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExtraWindow(LocalDateTime now, TransferProperties.Monitor cfg) {
        return cfg.getExtraCheckHours() != null
                && cfg.getExtraCheckHours().contains(now.getHour())
                && now.getMinute() < 5;
    }

    private boolean inAnyWindow(LocalDateTime now, TransferProperties.Monitor cfg) {
        int hour = now.getHour();
        if (cfg.getWindows() == null) {
            return false;
        }
        for (String w : cfg.getWindows()) {
            try {
                String[] p = w.split("-");
                int start = Integer.parseInt(p[0].trim());
                int end = Integer.parseInt(p[1].trim());
                if (hour >= start && hour < end) {
                    return true;
                }
            } catch (Exception ignore) {
                // 忽略非法配置项
            }
        }
        return false;
    }

    /** 入队 probe（去重：已有未完成 probe 则跳过）；成功入队则刷新 lastProbeAt。 */
    private boolean enqueueProbe(TransferMonitor m) {
        if (jobService.hasActiveJob(m.getMediaLinkId(), "check")) {
            return false;
        }
        JobEnqueueRequest req = new JobEnqueueRequest();
        req.setJobType("check");
        req.setPanType(m.getPanType());
        req.setShareUrl(m.getShareUrl());
        req.setSharePwd(m.getSharePwd());
        req.setMediaLinkId(m.getMediaLinkId());
        req.setPriority(0);
        jobService.enqueue(req);
        m.setLastProbeAt(LocalDateTime.now());
        monitorMapper.updateById(m);
        return true;
    }

    // ==================== 任务回报闭环 ====================

    @EventListener
    public void onJobReported(JobReportedEvent evt) {
        TransferJob job = evt.job();
        if (job.getMediaLinkId() == null) {
            return;
        }
        // 检查失败计入失效；落地夹不可用则清空固定夹，下次检查重新「创建」
        if (!"done".equals(job.getStatus())) {
            TransferMonitor fm = findByMediaLink(job.getMediaLinkId());
            if (fm == null) {
                return;
            }
            if ("failed".equals(job.getStatus()) && "check".equals(job.getJobType())) {
                // check 任务执行失败（非业务 bad）仍累计，避免网络抖动误杀
                registerProbeFailure(fm, false);
            } else if ("failed".equals(job.getStatus()) && "update".equals(job.getJobType())
                    && StringUtils.hasText(job.getErrorMsg())
                    && job.getErrorMsg().contains("落地夹不可用")) {
                log.warn("[监控转存] 落地夹失效，清空后待重新创建 mediaLinkId={} folder={} err={}",
                        fm.getMediaLinkId(), fm.getTargetFolderId(), job.getErrorMsg());
                fm.setTargetFolderId(null);
                fm.setMyShareUrl(null);
                monitorMapper.updateById(fm);
            }
            return;
        }
        TransferMonitor m = findByMediaLink(job.getMediaLinkId());
        if (m == null) {
            return;
        }
        switch (job.getJobType()) {
            case "check" -> applyProbe(m, job.getResultJson());
            case "create" -> {
                m.setTargetFolderId(job.getResultFolderId());
                m.setMyShareUrl(job.getResultShareUrl());
                // 创建落在哪个追更号上，记下来供展示；下次任务仍按指针解析，不依赖此字段选号
                String acct = readString(job.getResultJson(), "accountName");
                if (StringUtils.hasText(acct)) {
                    m.setAccountName(acct);
                }
                String latest = readString(job.getResultJson(), "latestFileName");
                if (EpisodeExtractor.shouldAdvanceFile(m.getLatestEpisode(), latest)) {
                    m.setLatestEpisode(latest);
                    m.setLastContentAt(LocalDateTime.now());
                }
                monitorMapper.updateById(m);
                if (StringUtils.hasText(latest)) {
                    notify.syncUpdated(m, latest);
                }
                if (StringUtils.hasText(m.getMyShareUrl())) {
                    events.publishEvent(new com.jyinshi.transfer.event.AnchorLinkReadyEvent(
                            m.getMediaLinkId(), m.getMyShareUrl()));
                }
                log.info("[监控转存] 创建完成，固定夹={}, 账号={}, 我方链={}, 最新={}",
                        job.getResultFolderId(), m.getAccountName(), job.getResultShareUrl(), latest);
            }
            case "update" -> {
                String latest = readString(job.getResultJson(), "latestFileName");
                if (EpisodeExtractor.shouldAdvanceFile(m.getLatestEpisode(), latest)) {
                    m.setLatestEpisode(latest);
                    m.setLastContentAt(LocalDateTime.now());
                    monitorMapper.updateById(m);
                    notify.syncUpdated(m, latest);
                }
                // 我方链若已有，每次更新都回写自营展示 url（防运营重录上游时盖回大佬链）
                if (StringUtils.hasText(m.getMyShareUrl())) {
                    events.publishEvent(new com.jyinshi.transfer.event.AnchorLinkReadyEvent(
                            m.getMediaLinkId(), m.getMyShareUrl()));
                }
            }
            default -> { /* 无关任务 */ }
        }
    }

    private void applyProbe(TransferMonitor m, String resultJson) {
        ProbeSnapshot snap = parseProbe(resultJson);
        if (snap == null) {
            return;
        }
        // 死链：明确 bad 一次即标失效（换源提示）；其它失败连续多次再判
        if ("bad".equals(snap.getCheckState())) {
            registerProbeFailure(m, true);
            return;
        }
        m.setFailCount(0);
        // 之前被判失效，这次探测又通了 → 自动恢复为追更中（改好分享/换号后立即检查即可复活）
        if ("invalid".equals(m.getStatus())) {
            m.setStatus("active");
        }

        boolean changed = (snap.getUpdatedAt() != null && m.getLastUpdatedAt() != null
                && snap.getUpdatedAt() > m.getLastUpdatedAt())
                || (snap.getFileCount() != null && m.getLastFileCount() != null
                && snap.getFileCount() > m.getLastFileCount());

        if (snap.getUpdatedAt() != null) {
            m.setLastUpdatedAt(snap.getUpdatedAt());
        }
        if (snap.getFileCount() != null) {
            m.setLastFileCount(snap.getFileCount());
        }
        if (StringUtils.hasText(snap.getTitle())) {
            m.setLastTitle(snap.getTitle());
        }

        // 已暂停/完结：只更新探测快照，不再创建/更新
        if (!Boolean.TRUE.equals(m.getEnabled())) {
            monitorMapper.updateById(m);
            return;
        }
        if (!StringUtils.hasText(m.getTargetFolderId())) {
            // 还没固定夹 → 监控转存·创建
            if (!jobService.hasActiveJob(m.getMediaLinkId(), "create")) {
                enqueueSave(m, "create", null);
            }
        } else if (changed) {
            // 有固定夹且源有更新 → 监控转存·更新
            if (!jobService.hasActiveJob(m.getMediaLinkId(), "update")) {
                enqueueSave(m, "update", m.getTargetFolderId());
            }
        }
        monitorMapper.updateById(m);
    }

    /**
     * 检查失败：累计失败数。
     *
     * @param immediate true=明确死链（checkState=bad），一次即标 invalid，便于运营换源；
     *                  false=执行异常/不确定，连续 {@link #MAX_FAIL} 次才判失效。
     */
    private void registerProbeFailure(TransferMonitor m, boolean immediate) {
        m.setFailCount((m.getFailCount() == null ? 0 : m.getFailCount()) + 1);
        boolean justInvalid = false;
        int threshold = immediate ? 1 : MAX_FAIL;
        if (m.getFailCount() >= threshold && !"invalid".equals(m.getStatus())) {
            m.setStatus("invalid");
            justInvalid = true;
            log.warn("[追更] 源判失效 mediaLinkId={} pan={} failCount={} immediate={}",
                    m.getMediaLinkId(), m.getPanType(), m.getFailCount(), immediate);
        }
        monitorMapper.updateById(m);
        if (justInvalid) {
            notify.monitorInvalid(m);
        }
    }

    private void enqueueSave(TransferMonitor m, String jobType, String targetFolderId) {
        // 监控转存永远走该盘唯一的 monitor 号，不读运营选手动号、不沿用误绑的转存号
        String acct = accountService.monitorAccountName(m.getPanType());
        if (!StringUtils.hasText(acct)) {
            log.warn("[监控转存] 跳过 {}：{} 未配置追更号 mediaLinkId={}",
                    jobType, m.getPanType(), m.getMediaLinkId());
            return;
        }
        JobEnqueueRequest req = new JobEnqueueRequest();
        req.setJobType(jobType);
        req.setPanType(m.getPanType());
        req.setAccountName(acct);
        // 创建落进监控顶层夹；更新用已固定的 targetFolderId
        if ("create".equals(jobType)) {
            req.setLandingDir(props.getMonitor().getLandingDir());
        }
        req.setShareUrl(m.getShareUrl());
        req.setSharePwd(m.getSharePwd());
        req.setMediaLinkId(m.getMediaLinkId());
        req.setTargetFolderId(targetFolderId);
        req.setPriority(5);
        jobService.enqueue(req);
        String action = "create".equals(jobType) ? "创建" : "更新";
        log.info("[监控转存] {} mediaLinkId={} pan={} account={}",
                action, m.getMediaLinkId(), m.getPanType(), acct);
    }

    // ==================== 内部 ====================

    private List<TransferMonitor> activeMonitors() {
        return monitorMapper.selectList(new LambdaQueryWrapper<TransferMonitor>()
                .eq(TransferMonitor::getEnabled, true)
                .eq(TransferMonitor::getStatus, "active"));
    }

    private TransferMonitor findByMediaLink(Long mediaLinkId) {
        return monitorMapper.selectOne(new LambdaQueryWrapper<TransferMonitor>()
                .eq(TransferMonitor::getMediaLinkId, mediaLinkId)
                .last("LIMIT 1"));
    }

    private ProbeSnapshot parseProbe(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProbeSnapshot.class);
        } catch (Exception e) {
            log.warn("[追更] 解析 probe 结果失败: {}", e.getMessage());
            return null;
        }
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
