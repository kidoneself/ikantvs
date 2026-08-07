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

    /** 每页条数（接口单页上限约 50）。 */
    private int hitPerRank = 50;

    /**
     * 每个频道每个榜型最多翻几页（start=0,50,100…）。
     * 实测「电影·最热」约 650 条，默认 20 页≈1000 可基本拉完。
     */
    private int maxPagesPerRank = 20;

    /**
     * 单次同步最多新建条数；{@code <=0} 表示不限制（首次灌库建议不限）。
     */
    private int maxCreatesPerRun = 0;

    private String apiUrl = "https://biz.quark.cn/api/trending/ranking/getYingshiRanking";

    private int timeoutMs = 15000;

    /**
     * 两次榜页请求之间的间隔（毫秒），降低连打触发风控的概率。
     * {@code <=0} 表示不等待。
     */
    private int requestIntervalMs = 500;

    /** cron，默认每 6 小时。 */
    private String cron = "0 20 */6 * * *";
}
