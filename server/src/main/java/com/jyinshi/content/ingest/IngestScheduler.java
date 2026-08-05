package com.jyinshi.content.ingest;

import com.jyinshi.content.service.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 后台保鲜定时（content 域）：周期性给库里的热门片刷 pansou 链接，让用户搜索永远只读库、秒回。
 *
 * <p>逐部调 {@link IngestService#ingestForMedia}，冷却内的自动跳过；总开关/保鲜开关任一关闭即空跑。
 * 实际采集丢到 {@code ingestExecutor}，调度线程只负责触发——外站卡住也不会堵清理/巡检。</p>
 */
@Slf4j
@Component
public class IngestScheduler {

    private final IngestProperties props;
    private final IngestService ingestService;
    private final MediaService mediaService;
    private final Executor ingestExecutor;

    public IngestScheduler(IngestProperties props, IngestService ingestService, MediaService mediaService,
                           @Qualifier("ingestExecutor") Executor ingestExecutor) {
        this.props = props;
        this.ingestService = ingestService;
        this.mediaService = mediaService;
        this.ingestExecutor = ingestExecutor;
    }

    @Scheduled(cron = "${jyinshi.ingest.warm.cron:0 0 * * * *}")
    public void warm() {
        if (!props.isEnabled() || !props.getWarm().isEnabled()) {
            return;
        }
        try {
            ingestExecutor.execute(this::doWarm);
        } catch (RejectedExecutionException e) {
            log.warn("[ingest] 保鲜跳过：ingest 线程池满");
        }
    }

    private void doWarm() {
        int batch = Math.max(1, props.getWarm().getBatchSize());
        List<Long> ids = mediaService.listBoardIds("hot", batch);
        if (ids.isEmpty()) {
            return;
        }
        int touched = 0;
        int newLinks = 0;
        for (Long id : ids) {
            try {
                IngestResult r = ingestService.ingestForMedia(id);
                if ("done".equals(r.getStatus())) {
                    touched++;
                    newLinks += r.getAdded();
                }
            } catch (Exception e) {
                log.warn("[ingest] 保鲜采集异常 media={}: {}", id, e.getMessage());
            }
        }
        log.info("[ingest] 保鲜完成 候选={} 实采={} 新增链接={}", ids.size(), touched, newLinks);
    }
}
