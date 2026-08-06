package com.jyinshi.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内容同步配置（对应 application.yml 的 {@code jyinshi.content.sync.*}）。
 *
 * <p>开源版仅重建自动榜单；片库靠夸克热榜 + 手工录入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.content.sync")
public class ContentSyncProperties {

    /** 总开关：关闭后定时任务与启动同步都不执行。 */
    private boolean enabled = true;

    /** 启动后是否跑一次重建榜单。 */
    private boolean runOnStartup = true;
}
