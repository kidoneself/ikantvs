package com.jyinshi.content.schedule;

import com.jyinshi.content.config.ContentSyncProperties;
import com.jyinshi.content.service.ContentSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 内容同步定时：开源仅重建自动榜单（片库靠夸克热榜）。
 */
@Slf4j
@Component
public class ContentSyncScheduler {

    private final ContentSyncService sync;
    private final ContentSyncProperties props;
    private final Executor scheduledWorkExecutor;

    public ContentSyncScheduler(ContentSyncService sync, ContentSyncProperties props,
                                @Qualifier("scheduledWorkExecutor") Executor scheduledWorkExecutor) {
        this.sync = sync;
        this.props = props;
        this.scheduledWorkExecutor = scheduledWorkExecutor;
    }

    @Scheduled(cron = "${jyinshi.content.sync.ranking-cron:0 0 */2 * * *}")
    public void rebuildRankings() {
        submit("rankings", sync::runScheduledRankings);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) {
            return;
        }
        submit("startup", sync::runStartup);
    }

    private void submit(String name, Runnable work) {
        if (!props.isEnabled() && !"startup".equals(name)) {
            return;
        }
        try {
            scheduledWorkExecutor.execute(() -> {
                try {
                    work.run();
                } catch (Exception e) {
                    log.error("[content-sync] {} 异常", name, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[content-sync] {} 跳过：工作线程池满", name);
        }
    }
}
