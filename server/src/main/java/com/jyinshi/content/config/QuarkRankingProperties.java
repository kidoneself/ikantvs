package com.jyinshi.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 夸克影视热榜同步（开源默认元数据源）。
 * 无搜索能力，仅排行榜灌库；对应 {@code jyinshi.content.quark-ranking.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.content.quark-ranking")
public class QuarkRankingProperties {

    /** 总开关；开源默认开启。 */
    private boolean enabled = true;

    /** 启动后是否跑一次，让空库尽快有片。 */
    private boolean runOnStartup = true;

    /** 每个频道每个榜型抓取条数（接口分页累加）。 */
    private int hitPerRank = 40;

    /** 单次同步最多新建条数，防止一次灌爆。 */
    private int maxCreatesPerRun = 200;

    private String apiUrl = "https://biz.quark.cn/api/trending/ranking/getYingshiRanking";

    private int timeoutMs = 15000;

    /** cron，默认每 6 小时。 */
    private String cron = "0 20 */6 * * *";
}
