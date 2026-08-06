package com.jyinshi.content.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.ContentSyncStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内容同步（开源）：仅重建自动榜单（按库内夸克/手工条目）。
 */
@Slf4j
@Service
public class ContentSyncService {

    private final MediaService mediaService;
    private final RankingService rankingService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "content-sync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile String task = "idle";
    private volatile String phase = "";
    private volatile int total = 0;
    private volatile int processed = 0;
    private volatile int affected = 0;
    private volatile String result = "";
    private volatile String error = "";
    private volatile long startedAt = 0L;
    private volatile long finishedAt = 0L;

    public ContentSyncService(MediaService mediaService, RankingService rankingService) {
        this.mediaService = mediaService;
        this.rankingService = rankingService;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public ContentSyncStatus submit(String taskName) {
        if (!"rankings".equals(taskName)) {
            throw new BizException("未知同步任务：" + taskName);
        }
        if (!running.compareAndSet(false, true)) {
            throw new BizException("已有同步任务在执行中，请等它完成再试");
        }
        executor.submit(() -> {
            try {
                rebuildAutoRankings();
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

    public void runScheduledRankings() {
        guarded("rankings", this::rebuildAutoRankings);
    }

    public void runStartup() {
        guarded("startup", this::rebuildAutoRankings);
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
            case "rankings" -> "重建自动榜单";
            case "startup" -> "启动同步";
            default -> "空闲";
        };
    }
}
