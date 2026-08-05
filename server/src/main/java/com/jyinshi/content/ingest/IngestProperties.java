package com.jyinshi.content.ingest;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资源聚合入库配置（{@code jyinshi.ingest.*}）。见 {@code docs/资源聚合与检测设计.md} §6。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.ingest")
public class IngestProperties {

    /** 总开关：关闭后所有来源采集、保鲜定时都不执行。 */
    private boolean enabled = true;

    /** 相关性准入阈值：note 与片名相关度低于此分丢弃（防张冠李戴）。0~100。 */
    private int relevanceThreshold = 40;

    /** 入库前查失效黑名单并过滤：命中 invalid_share 的分享直接不入库（等价老系统 filterInvalidLinks）。 */
    private boolean filterInvalidShare = true;

    /** 同一 media 采集冷却（分钟）：冷却内重复触发直接跳过，防打爆来源。被动最坏新鲜度延迟≈此值。 */
    private int cooldownMinutes = 10;

    /**
     * 空结果冷却（分钟）：本轮没搜到任何可用链接的片，只冷却这么短，
     * 好让「暂时没资源」的片被多次访问时能持续重试，而不是 30 分钟一直空着。
     */
    private int emptyCooldownMinutes = 3;

    private final Pansou pansou = new Pansou();
    private final Gying gying = new Gying();
    private final Seedhub seedhub = new Seedhub();
    private final Warm warm = new Warm();

    /** pansou 来源开关与参数。 */
    @Data
    public static class Pansou {
        private boolean enabled = true;
        /** 内网地址（容器内为 http://pansou，经 nginx）。 */
        private String baseUrl = "http://pansou";
        private long timeoutMs = 12000;
        /** 只要这些网盘类型（逗号分隔）。<b>留空=全要（所有网盘 + 磁力/ed2k 全部录入）</b>。 */
        private String cloudTypes = "";
    }

    /**
     * 观影（Gying）来源开关与参数。<b>需要账号才能搜</b>：{@link #accounts} 为空时该来源自动跳过。
     * 站点有登录墙 + PoW 机器人验证，客户端自带 cookie 缓存与 PoW 解题。
     */
    @Data
    public static class Gying {
        private boolean enabled = true;
        /** 站点根地址（不含路径）。 */
        private String baseUrl = "https://www.xn--wcv59z.com";
        /** 可选 HTTP 代理，形如 http://host:port。 */
        private String httpProxy = "";
        /** cookie 缓存目录（登录态持久化，避免每次都登）。 */
        private String cookieDir = "cache/gying_cookies";
        /** 账号池：逗号分隔的 {@code user:pass} 列表。留空=不启用该来源。 */
        private String accounts = "";
        /** 只要这些网盘类型（逗号分隔）。留空=全要。 */
        private String cloudTypes = "";
        /** 拉详情页的并发数。 */
        private int detailConcurrency = 8;
    }

    /**
     * SeedHub 来源开关与参数：{@code /s/{kw}/ → /movies/{id}/ → link_start → 网盘链接}。
     * 无需账号，但要能出网访问站点。
     */
    @Data
    public static class Seedhub {
        private boolean enabled = true;
        private String baseUrl = "https://www.seedhub.cc";
        private int timeoutSeconds = 10;
        /** 只要这些网盘类型（逗号分隔）。留空=全要（quark/baidu/xunlei/uc/aliyun）。 */
        private String panTypes = "";
        /** 列表页最多参与打分的候选数。 */
        private int maxListCandidates = 5;
        /** 最多拉几个详情页（防打爆）。 */
        private int maxDetailFetch = 2;
        /** top1 与 top2 分差小于此值视为歧义，追加拉 top2。 */
        private int scoreGapForTop2 = 15;
        /** 跳过非中文关键词（SeedHub 主要收录中文资源）。 */
        private boolean skipNonChinese = true;
        /** 并行 link_start 上限。 */
        private int linkStartConcurrency = 3;
        /** link_start 的 pan_id → URL 缓存（小时），走 Redis，减少重复跳转请求。 */
        private int panLinkTtlHours = 24;
    }

    /** 后台保鲜定时：周期性给库里的片刷新链接。 */
    @Data
    public static class Warm {
        private boolean enabled = true;
        /** 每轮处理多少部片（按热度倒序，冷却内的自动跳过）。 */
        private int batchSize = 30;
        /** cron：默认每小时一次。 */
        private String cron = "0 0 * * * *";
    }
}
