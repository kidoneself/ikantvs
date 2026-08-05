package com.jyinshi.transfer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 一次性：环境变量 {@code NAS_RECOMPUTE_LINKS=mediaLinkId,...}
 * 等应用就绪且账号池装入后再重算（ApplicationRunner 太早会拿不到号）。
 */
@Slf4j
@Component
public class NasRecomputeBoot {

    private static final long DELAY_MS = 8_000L;

    private final NasFillService nasFillService;
    private final TaskScheduler taskScheduler;

    public NasRecomputeBoot(NasFillService nasFillService, TaskScheduler taskScheduler) {
        this.nasFillService = nasFillService;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1000)
    public void onReady() {
        String raw = System.getenv("NAS_RECOMPUTE_LINKS");
        if (!StringUtils.hasText(raw)) {
            return;
        }
        log.info("[NAS] 已登记启动重算，{}s 后执行 links={}", DELAY_MS / 1000, raw);
        final String links = raw;
        taskScheduler.schedule(() -> runRecompute(links), Instant.now().plusMillis(DELAY_MS));
    }

    private void runRecompute(String raw) {
        log.info("[NAS] 开始启动重算 links={}", raw);
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(s);
                int n = nasFillService.recomputeByMediaLinkId(id);
                log.info("[NAS] 启动重算完成 mediaLinkId={} queued={}", id, n);
            } catch (Exception e) {
                log.warn("[NAS] 启动重算失败 link={}: {}", s, e.getMessage(), e);
            }
        }
    }
}
