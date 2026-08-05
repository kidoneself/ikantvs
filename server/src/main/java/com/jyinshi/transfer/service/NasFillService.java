package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.transfer.dto.NasFileEntry;
import com.jyinshi.transfer.entity.NasLanding;
import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.mapper.TransferMonitorMapper;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.account.AccountPool;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.baidu.BaiduDriver;
import com.jyinshi.transfer.pan.driver.xunlei.XunleiDriver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百度 / 迅雷监控变动后：按「百度有 − 迅雷已有 − 基线」算差集入队千云。
 *
 * <p>跨盘比对用 {@link NasFileMatcher}：综艺「日期+期题」、剧集「集数+国粤语」，
 * 忽略 {@code -} / 空格 / {@code 4K} / 书名号等cosmetic差异，避免换源后整季重传。</p>
 *
 * <p>迅雷换源或追更补齐后会再次重算：差集变空则自动取消 pending 整包任务；
 * 仍缺则替换为更小的差集。不必人工判断要不要传。</p>
 */
@Slf4j
@Service
public class NasFillService {

    /** 百度刚创建时夹内可能尚未落盘，空夹时延迟再扫一次。 */
    private static final long EMPTY_RETRY_DELAY_MS = 45_000L;

    private final NasJobService nasJobService;
    private final NasLandingService landingService;
    private final TransferAccountService accountService;
    private final AccountPool accountPool;
    private final BaiduDriver baiduDriver;
    private final XunleiDriver xunleiDriver;
    private final MediaLinkMapper mediaLinkMapper;
    private final MediaMapper mediaMapper;
    private final TransferMonitorMapper monitorMapper;
    private final TaskScheduler taskScheduler;
    private final Set<Long> emptyRetryScheduled = ConcurrentHashMap.newKeySet();

    public NasFillService(NasJobService nasJobService,
                          NasLandingService landingService,
                          TransferAccountService accountService,
                          AccountPool accountPool,
                          BaiduDriver baiduDriver,
                          XunleiDriver xunleiDriver,
                          MediaLinkMapper mediaLinkMapper,
                          MediaMapper mediaMapper,
                          TransferMonitorMapper monitorMapper,
                          TaskScheduler taskScheduler) {
        this.nasJobService = nasJobService;
        this.landingService = landingService;
        this.accountService = accountService;
        this.accountPool = accountPool;
        this.baiduDriver = baiduDriver;
        this.xunleiDriver = xunleiDriver;
        this.mediaLinkMapper = mediaLinkMapper;
        this.mediaMapper = mediaMapper;
        this.monitorMapper = monitorMapper;
        this.taskScheduler = taskScheduler;
    }

    /** 百度监控转存创建/更新成功后调用。 */
    public void afterBaiduDone(TransferMonitor m) {
        if (m == null || !"baidu".equalsIgnoreCase(m.getPanType())) {
            return;
        }
        if (!nasJobService.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(m.getTargetFolderId())) {
            log.debug("[NAS] 跳过：百度尚无固定夹 mediaLinkId={}", m.getMediaLinkId());
            return;
        }
        try {
            doFill(m, true);
        } catch (Exception e) {
            log.warn("[NAS] 灌盘编排失败 mediaLinkId={}: {}", m.getMediaLinkId(), e.getMessage(), e);
        }
    }

    /**
     * 运营手动重算差集（换匹配规则/换源后）：不跑网盘 sync，只列两边夹入队。
     *
     * @return 入队文件数；无需灌盘返回 0；失败/跳过返回 -1
     */
    public int recomputeByMediaLinkId(Long mediaLinkId) {
        if (mediaLinkId == null || !nasJobService.isEnabled()) {
            return -1;
        }
        TransferMonitor m = monitorMapper.selectOne(new LambdaQueryWrapper<TransferMonitor>()
                .eq(TransferMonitor::getMediaLinkId, mediaLinkId)
                .last("LIMIT 1"));
        if (m == null || !"baidu".equalsIgnoreCase(m.getPanType())
                || !StringUtils.hasText(m.getTargetFolderId())) {
            log.warn("[NAS] 重算跳过：无百度监控夹 mediaLinkId={}", mediaLinkId);
            return -1;
        }
        try {
            return doFill(m, false);
        } catch (Exception e) {
            log.warn("[NAS] 手动重算失败 mediaLinkId={}: {}", mediaLinkId, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 迅雷监控创建/更新成功后调用：同剧百度侧按最新迅雷夹内容重算差集。
     * 换源灌进一批后，会自动取消过时的整部首灌 pending。
     */
    public void afterXunleiSynced(TransferMonitor xunleiMon) {
        if (xunleiMon == null || !"xunlei".equalsIgnoreCase(xunleiMon.getPanType())) {
            return;
        }
        if (!nasJobService.isEnabled()) {
            return;
        }
        try {
            MediaLink xlLink = mediaLinkMapper.selectById(xunleiMon.getMediaLinkId());
            if (xlLink == null || xlLink.getMediaId() == null) {
                return;
            }
            TransferMonitor baidu = findBaiduMonitor(xlLink.getMediaId());
            if (baidu == null || !StringUtils.hasText(baidu.getTargetFolderId())) {
                log.debug("[NAS] 迅雷已同步但同剧无百度监控夹 mediaId={}", xlLink.getMediaId());
                return;
            }
            log.info("[NAS] 迅雷夹有变动，重算差集 mediaId={} xlLink={} baiduLink={}",
                    xlLink.getMediaId(), xunleiMon.getMediaLinkId(), baidu.getMediaLinkId());
            doFill(baidu, false);
        } catch (Exception e) {
            log.warn("[NAS] 迅雷同步后重算失败 mediaLinkId={}: {}",
                    xunleiMon.getMediaLinkId(), e.getMessage(), e);
        }
    }

    private TransferMonitor findBaiduMonitor(Long mediaId) {
        List<MediaLink> links = mediaLinkMapper.selectList(new LambdaQueryWrapper<MediaLink>()
                .eq(MediaLink::getMediaId, mediaId)
                .eq(MediaLink::getPanType, "baidu"));
        if (links == null || links.isEmpty()) {
            return null;
        }
        for (MediaLink link : links) {
            TransferMonitor m = monitorMapper.selectOne(new LambdaQueryWrapper<TransferMonitor>()
                    .eq(TransferMonitor::getMediaLinkId, link.getId())
                    .last("LIMIT 1"));
            if (m != null && StringUtils.hasText(m.getTargetFolderId())) {
                return m;
            }
        }
        return null;
    }

    /** @return 入队文件数；无需灌/跳过返回 0 */
    private int doFill(TransferMonitor m, boolean allowEmptyRetry) {
        MediaLink link = mediaLinkMapper.selectById(m.getMediaLinkId());
        if (link == null || link.getMediaId() == null) {
            log.warn("[NAS] media_link 不存在 id={}", m.getMediaLinkId());
            return 0;
        }
        Long mediaId = link.getMediaId();
        Media media = mediaMapper.selectById(mediaId);
        String title = media != null && StringUtils.hasText(media.getTitle())
                ? media.getTitle() : ("media-" + mediaId);

        TransferAccount baiduAcc = accountService.findMonitorAccount("baidu");
        if (baiduAcc == null) {
            log.warn("[NAS] 无百度监控号");
            return 0;
        }
        Account baidu = accountPool.pickByName(PanType.BAIDU, baiduAcc.getAccountName());
        if (baidu == null) {
            log.warn("[NAS] 百度监控号未装入内存: {}", baiduAcc.getAccountName());
            return 0;
        }
        TransferAccount xlAcc = accountService.findMonitorAccount("xunlei");
        Account xunlei = xlAcc == null ? null
                : accountPool.pickByName(PanType.XUNLEI, xlAcc.getAccountName());
        if (xunlei == null) {
            log.warn("[NAS] 迅雷监控号未装入内存");
            return 0;
        }

        List<NasFileEntry> baiduFiles = baiduDriver.walkFolderForNas(baidu, m.getTargetFolderId());
        if (baiduFiles == null) {
            log.warn("[NAS] 百度固定夹不可列: {}", m.getTargetFolderId());
            return 0;
        }
        Set<String> baiduKeys = new LinkedHashSet<>();
        for (NasFileEntry f : baiduFiles) {
            baiduKeys.add(relKey(f));
        }

        NasLanding landing = landingService.ensure(mediaId, m.getMediaLinkId(), title, baiduKeys);
        if (landing == null || !StringUtils.hasText(landing.getXunleiFolderId())) {
            return 0;
        }

        Set<String> baseline = landingService.parseBaseline(landing);
        Set<String> have = xunleiDriver.collectRelativeFileKeys(xunlei, landing.getXunleiFolderId());
        if (have == null) {
            log.warn("[NAS] 迅雷落地夹不可列: {}", landing.getXunleiFolderId());
            return 0;
        }
        // 身份键：综艺按「日期+期题」、剧集按「集数+国粤语」归一，忽略 - / 4K / 书名号等cosmetic
        Set<String> haveMatch = new LinkedHashSet<>();
        for (String rel : have) {
            haveMatch.add(NasFileMatcher.matchKey(rel));
        }
        Set<String> baselineMatch = new LinkedHashSet<>();
        for (String rel : baseline) {
            baselineMatch.add(NasFileMatcher.matchKey(rel));
        }

        if (baiduFiles.isEmpty()) {
            // 刚创建时百度可能还没落盘：取消过时 pending，并延迟再扫
            nasJobService.cancelPending(m.getMediaLinkId(), "百度夹暂无文件，取消待传任务");
            log.info("[NAS] 百度夹暂无文件 mediaLinkId={} xlHave={}，取消 pending",
                    m.getMediaLinkId(), have.size());
            if (allowEmptyRetry) {
                scheduleEmptyRetry(m);
            }
            return 0;
        }

        boolean coldStart = have.isEmpty();
        List<NasFileEntry> missing = new ArrayList<>();
        Set<String> queuedMatch = new LinkedHashSet<>();
        for (NasFileEntry f : baiduFiles) {
            String key = relKey(f);
            String mk = NasFileMatcher.matchKey(key);
            if (!coldStart && (baseline.contains(key) || baselineMatch.contains(mk))) {
                continue;
            }
            if (NasFileMatcher.alreadyHave(key, have, haveMatch)) {
                continue;
            }
            // 百度侧同身份重复文件只入队一次
            if (!queuedMatch.add(mk)) {
                continue;
            }
            missing.add(f);
        }
        if (missing.isEmpty()) {
            // 迅雷已齐（或换源补齐）→ 自动取消还在排队的整包/过时任务
            int cancelled = nasJobService.cancelPending(m.getMediaLinkId(),
                    "迅雷已有齐套文件，无需千云上传");
            log.info("[NAS] 无需灌盘 mediaLinkId={} baidu={} baseline={} xlHave={} coldStart={} cancelledPending={}",
                    m.getMediaLinkId(), baiduFiles.size(), baseline.size(), have.size(), coldStart, cancelled);
            return 0;
        }
        log.info("[NAS] {}入队 mediaLinkId={} newFiles={} (baidu={} baseline={} xl={} matchMiss={})",
                coldStart ? "首灌" : "只追新",
                m.getMediaLinkId(), missing.size(), baiduFiles.size(), baseline.size(), have.size(),
                missing.size());
        nasJobService.enqueueOrReplace(m.getMediaLinkId(), title, landing.getXunleiFolderId(),
                baiduAcc.getId(), missing);
        return missing.size();
    }

    private void scheduleEmptyRetry(TransferMonitor m) {
        Long linkId = m.getMediaLinkId();
        if (linkId == null || !emptyRetryScheduled.add(linkId)) {
            return;
        }
        taskScheduler.schedule(() -> {
            try {
                TransferMonitor latest = monitorMapper.selectOne(new LambdaQueryWrapper<TransferMonitor>()
                        .eq(TransferMonitor::getMediaLinkId, linkId)
                        .last("LIMIT 1"));
                if (latest != null && StringUtils.hasText(latest.getTargetFolderId())) {
                    log.info("[NAS] 空夹延迟重扫 mediaLinkId={}", linkId);
                    doFill(latest, false);
                }
            } catch (Exception e) {
                log.warn("[NAS] 空夹延迟重扫失败 mediaLinkId={}: {}", linkId, e.getMessage());
            } finally {
                emptyRetryScheduled.remove(linkId);
            }
        }, Instant.now().plusMillis(EMPTY_RETRY_DELAY_MS));
        log.info("[NAS] 已安排 {}s 后重扫百度夹 mediaLinkId={}", EMPTY_RETRY_DELAY_MS / 1000, linkId);
    }

    static String relKey(NasFileEntry f) {
        String rel = f.getRelDir() == null ? "" : f.getRelDir();
        return rel.isBlank() ? f.getName() : rel + "/" + f.getName();
    }
}
