package com.jyinshi.content.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.client.TmdbClient;
import com.jyinshi.content.config.ContentSyncProperties;
import com.jyinshi.content.dto.ContentSyncStatus;
import com.jyinshi.content.dto.MediaImportRequest;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内容自动同步（content 域）：把「拉新片/热播、刷新连载集数、重建自动榜单」三件事的逻辑收在这里，
 * 定时触发在 {@link com.jyinshi.content.schedule.ContentSyncScheduler}。
 *
 * <p>全部复用已有能力：拉新走 {@link MediaService#importByExternalId}，刷新走 {@link MediaService#refresh}，
 * 榜单走 {@link RankingService#upsertSystemRanking}。本类不直接碰 Mapper。
 *
 * <p>执行模型：所有任务经**单线程串行**执行，同一时刻只允许一个任务打 TMDB
 * （{@link #running} 抢占）。定时/启动同步（runScheduledXxx / runStartup）忙则跳过；
 * 后台手动触发（{@link #submit}）异步下发、忙则抛错；进度由 {@link #getStatus()} 轮询。
 */
@Slf4j
@Service
public class ContentSyncService {

    private final TmdbClient tmdbClient;
    private final MediaService mediaService;
    private final RankingService rankingService;
    private final ContentSyncProperties props;

    /** 单线程串行：保证同一时刻只有一个同步任务在跑，避免并发打爆 TMDB。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "content-sync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 进度快照字段：写在同步线程、读在请求线程，用 volatile 保证可见性。
    private volatile String task = "idle";
    private volatile String phase = "";
    private volatile int total = 0;
    private volatile int processed = 0;
    private volatile int affected = 0;
    private volatile String result = "";
    private volatile String error = "";
    private volatile long startedAt = 0L;
    private volatile long finishedAt = 0L;

    public ContentSyncService(TmdbClient tmdbClient, MediaService mediaService,
                              RankingService rankingService, ContentSyncProperties props) {
        this.tmdbClient = tmdbClient;
        this.mediaService = mediaService;
        this.rankingService = rankingService;
        this.props = props;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    // ---------------- 对外：后台手动异步触发 ----------------

    /**
     * 手动触发一次同步（异步，立即返回当前进度）。已有任务在跑时抛 {@link BizException}。
     *
     * @param taskName discover 拉新 / refresh 刷新连载 / rankings 重建榜单
     * @return 提交后的进度快照（running=true）
     */
    public ContentSyncStatus submit(String taskName) {
        Runnable job = switch (taskName == null ? "" : taskName) {
            case "discover" -> this::discoverNew;
            case "refresh" -> this::refreshAiring;
            case "rankings" -> this::rebuildAutoRankings;
            default -> throw new BizException("未知同步任务：" + taskName);
        };
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有同步任务在执行中，请等它完成再试");
        }
        executor.submit(() -> {
            try {
                job.run();
            } catch (Exception e) {
                error = e.getMessage();
                log.error("手动同步任务异常 task={}", taskName, e);
            } finally {
                finishedAt = System.currentTimeMillis();
                running.set(false);
            }
        });
        return getStatus();
    }

    /** 当前进度快照。 */
    public ContentSyncStatus getStatus() {
        ContentSyncStatus s = new ContentSyncStatus();
        s.setTask(task);
        s.setTaskLabel(label(task));
        s.setRunning(running.get());
        s.setPhase(phase);
        s.setTotal(total);
        s.setProcessed(processed);
        s.setAffected(affected);
        s.setResult(result);
        s.setError(error);
        s.setStartedAt(startedAt > 0 ? startedAt : null);
        s.setFinishedAt(finishedAt > 0 ? finishedAt : null);
        return s;
    }

    // ---------------- 对外：定时/启动同步（同步执行，忙则跳过） ----------------

    /** 定时拉新（忙则跳过）。 */
    public void runScheduledDiscover() {
        guarded("discover", this::discoverNew);
    }

    /** 定时刷新连载（忙则跳过）。 */
    public void runScheduledRefresh() {
        guarded("refresh", this::refreshAiring);
    }

    /** 定时重建榜单（忙则跳过）。 */
    public void runScheduledRankings() {
        guarded("rankings", this::rebuildAutoRankings);
    }

    /** 启动同步：重建榜单 → 拉新 → 再重建（忙则跳过）。 */
    public void runStartup() {
        guarded("startup", () -> {
            rebuildAutoRankings();
            discoverNew();
            rebuildAutoRankings();
        });
    }

    private void guarded(String name, Runnable work) {
        if (!running.compareAndSet(false, true)) {
            log.info("已有同步任务执行中，跳过 {}", name);
            return;
        }
        try {
            work.run();
        } catch (Exception e) {
            error = e.getMessage();
            log.error("定时同步任务异常 {}", name, e);
        } finally {
            finishedAt = System.currentTimeMillis();
            running.set(false);
        }
    }

    // ---------------- 三件事的实际逻辑（同时维护进度快照） ----------------

    /**
     * 拉新：从 TMDB 趋势 + 正在播出剧集 + 正在上映电影里，把库里还没有的条目采集入库。
     *
     * @return 本次新增入库条数
     */
    public int discoverNew() {
        beginTask("discover", "拉取候选", 0);
        if (!tmdbClient.isConfigured()) {
            log.warn("TMDB 未配置，跳过拉新");
            result = "TMDB 未配置，已跳过";
            return 0;
        }
        Set<TmdbClient.Ref> refs = new LinkedHashSet<>();
        refs.addAll(tmdbClient.trending("week", props.getTrendingPages()));
        refs.addAll(tmdbClient.onTheAirTv(props.getTvPages()));
        refs.addAll(tmdbClient.nowPlayingMovies(props.getRegion(), props.getMoviePages()));

        total = refs.size();
        phase = "采集入库";
        int imported = 0;
        for (TmdbClient.Ref ref : refs) {
            processed++;
            if (imported >= props.getMaxImportsPerRun()) {
                break;
            }
            if (mediaService.existsByTmdb(ref.tmdbId(), ref.type())) {
                continue;
            }
            try {
                MediaImportRequest req = new MediaImportRequest();
                req.setTmdbId(ref.tmdbId());
                req.setType(ref.type());
                req.setPublish(props.isPublish());
                mediaService.importByExternalId(req);
                imported++;
                affected = imported;
            } catch (Exception e) {
                log.warn("拉新入库失败 tmdb={} type={}：{}", ref.tmdbId(), ref.type(), e.getMessage());
            }
        }
        result = "候选 " + refs.size() + " 条，新增入库 " + imported + " 条";
        log.info("定时拉新完成：候选 {} 条，新增入库 {} 条", refs.size(), imported);
        return imported;
    }

    /**
     * 刷新连载剧：对「连载中」的剧集重拉 TMDB，更新集数进度/评分/海报。
     *
     * @return 本次成功刷新条数
     */
    public int refreshAiring() {
        List<Long> ids = mediaService.listAiringForRefresh(
                props.getRefreshBatchSize(), props.getRefreshMinIntervalHours());
        beginTask("refresh", "刷新连载", ids.size());
        int done = 0;
        for (Long id : ids) {
            processed++;
            try {
                mediaService.refresh(id);
                done++;
                affected = done;
            } catch (Exception e) {
                log.warn("刷新连载剧失败 media={}：{}", id, e.getMessage());
            }
        }
        result = "待刷 " + ids.size() + " 条，成功 " + done + " 条";
        log.info("定时刷新连载剧完成：待刷 {} 条，成功 {} 条", ids.size(), done);
        return done;
    }

    /** 重建自动榜单：正在热播 / 最新上映 / 高分推荐，全部按库里现状实时生成。 */
    public void rebuildAutoRankings() {
        beginTask("rankings", "重建榜单", 3);
        rankingService.upsertSystemRanking("sys-airing", "正在热播", "连载中的剧集，自动更新", 30,
                mediaService.listBoardIds("airing", 24));
        processed = 1;
        rankingService.upsertSystemRanking("sys-new", "最新上映", "近期上映 / 开播，自动更新", 20,
                mediaService.listBoardIds("new", 24));
        processed = 2;
        rankingService.upsertSystemRanking("sys-top", "高分推荐", "高评分作品，自动更新", 10,
                mediaService.listBoardIds("top", 24));
        processed = 3;
        affected = 3;
        result = "已重建：正在热播 / 最新上映 / 高分推荐";
        log.info("自动榜单已重建：正在热播 / 最新上映 / 高分推荐");
    }

    /** 开一个新任务：重置进度快照。 */
    private void beginTask(String taskName, String phaseText, int totalCount) {
        this.task = taskName;
        this.phase = phaseText;
        this.total = totalCount;
        this.processed = 0;
        this.affected = 0;
        this.error = "";
        this.result = "";
        this.startedAt = System.currentTimeMillis();
        this.finishedAt = 0L;
    }

    private static String label(String t) {
        return switch (t == null ? "" : t) {
            case "discover" -> "拉新片 / 热播";
            case "refresh" -> "刷新连载集数";
            case "rankings" -> "重建自动榜单";
            case "startup" -> "启动同步";
            default -> "空闲";
        };
    }
}
