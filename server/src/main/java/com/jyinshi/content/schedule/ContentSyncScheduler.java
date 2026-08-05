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
 * 内容自动同步定时触发（content 域）。cron 可用环境变量覆盖，见 application.yml。
 *
 * <p>所有任务先看总开关 {@link ContentSyncProperties#isEnabled()}，关掉即全部空跑。
 * 实际同步丢到 {@code scheduledWorkExecutor}，避免占死调度线程。</p>
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

    /** 拉新片 / 热播：默认每 6 小时。 */
    @Scheduled(cron = "${jyinshi.content.sync.discover-cron:0 0 */6 * * *}")
    public void discover() {
        submit("discover", sync::runScheduledDiscover);
    }

    /** 刷新连载剧集数：默认每天 04:00。 */
    @Scheduled(cron = "${jyinshi.content.sync.refresh-cron:0 0 4 * * *}")
    public void refreshAiring() {
        submit("refresh", sync::runScheduledRefresh);
    }

    /** 重建自动榜单：默认每 2 小时。 */
    @Scheduled(cron = "${jyinshi.content.sync.ranking-cron:0 0 */2 * * *}")
    public void rebuildRankings() {
        submit("rankings", sync::runScheduledRankings);
    }

    /** 启动后异步跑一次（重建榜单 → 拉新 → 再重建），让新部署立即有数据。 */
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
