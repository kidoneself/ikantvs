package com.jyinshi.common.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印数据源模式，方便确认是否已填「一份链接」。
 */
@Component
public class CloudDataStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudDataStartupLogger.class);

    private final CloudDataProperties cloud;

    public CloudDataStartupLogger(CloudDataProperties cloud) {
        this.cloud = cloud;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!cloud.isEnabled()) {
            log.info("数据源模式: local（本地库 / 演示种子）。对接上游请设置 JYINSHI_CLOUD_ENABLED=true 与 BASE_URL / API_KEY");
            return;
        }
        String base = cloud.getBaseUrl() == null ? "" : cloud.getBaseUrl().trim();
        boolean hasKey = cloud.getApiKey() != null && !cloud.getApiKey().isBlank();
        if (base.isEmpty() || !hasKey) {
            log.warn("数据源模式: cloud 已启用，但 JYINSHI_CLOUD_BASE_URL 或 API_KEY 未配齐");
            return;
        }
        log.info("数据源模式: cloud → {}", base);
    }
}
