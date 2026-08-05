package com.jyinshi.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内容自动同步配置（对应 application.yml 的 {@code jyinshi.content.sync.*}）。
 *
 * <p>三件事：定时从 TMDB 拉新片/热播、定时刷新连载剧集数、定时重建自动榜单。
 * cron 表达式直接写在 {@code @Scheduled} 里（读同名配置），本类只放业务开关与量级参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.content.sync")
public class ContentSyncProperties {

    /** 总开关：关闭后所有定时任务与启动同步都不执行。 */
    private boolean enabled = true;

    /** 拉新入库时是否直接发布（false 则入草稿，等人工发布）。 */
    private boolean publish = true;

    /** 启动后是否跑一次（重建榜单 + 拉新），让新部署立即有数据。 */
    private boolean runOnStartup = true;

    /** now_playing 的地区（如 CN/US），空则全球。 */
    private String region = "";

    /** 各发现端点抓取页数（每页约 20 条）。 */
    private int trendingPages = 1;
    private int tvPages = 1;
    private int moviePages = 1;

    /** 单次拉新最多入库多少条新条目（防止一次灌太多、打爆 TMDB）。 */
    private int maxImportsPerRun = 40;

    /** 单次刷新连载剧的批量上限。 */
    private int refreshBatchSize = 60;

    /** 刷新连载剧时，近多少小时内已更新过的跳过。 */
    private int refreshMinIntervalHours = 12;
}
