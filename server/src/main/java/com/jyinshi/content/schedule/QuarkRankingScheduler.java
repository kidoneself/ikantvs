package com.jyinshi.content.schedule;

import com.jyinshi.content.config.QuarkRankingProperties;
import com.jyinshi.content.service.QuarkMediaSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 夸克热榜定时灌库（开源默认）。
 */
@Slf4j
@Component
public class QuarkRankingScheduler {

    private final QuarkMediaSyncService sync;
    private final QuarkRankingProperties props;
    private final Executor scheduledWorkExecutor;

    public QuarkRankingScheduler(QuarkMediaSyncService sync, QuarkRankingProperties props,
                                 @Qualifier("scheduledWorkExecutor") Executor scheduledWorkExecutor) {
        this.sync = sync;
        this.props = props;
        this.scheduledWorkExecutor = scheduledWorkExecutor;
    }

    @Scheduled(cron = "${jyinshi.content.quark-ranking.cron:0 20 */6 * * *}")
    public void scheduled() {
        if (!props.isEnabled()) {
            return;
        }
        submit("cron", sync::syncAll);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) {
            return;
        }
        submit("startup", sync::syncAll);
    }

    private void submit(String name, Runnable work) {
        try {
            scheduledWorkExecutor.execute(() -> {
                try {
                    work.run();
                } catch (Exception e) {
                    log.error("[quark-ranking] {} 异常", name, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[quark-ranking] {} 跳过：线程池满", name);
        }
    }
}
