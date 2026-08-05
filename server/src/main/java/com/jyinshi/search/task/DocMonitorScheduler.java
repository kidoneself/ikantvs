package com.jyinshi.search.task;

import com.jyinshi.search.service.DocMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class DocMonitorScheduler {

    private final DocMonitorService service;
    private final Executor scheduledWorkExecutor;

    public DocMonitorScheduler(DocMonitorService service,
                               @Qualifier("scheduledWorkExecutor") Executor scheduledWorkExecutor) {
        this.service = service;
        this.scheduledWorkExecutor = scheduledWorkExecutor;
    }

    @Scheduled(cron = "${jyinshi.doc-monitor.cron:0 30 * * * ?}")
    public void tick() {
        try {
            scheduledWorkExecutor.execute(() -> {
                try {
                    service.runScheduledCheck();
                } catch (Exception e) {
                    log.error("[DocMonitor] 定时检查异常", e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[DocMonitor] 定时检查跳过：工作线程池满");
        }
    }
}
