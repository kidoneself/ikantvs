package com.jyinshi.content.ingest.source;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 来源插件统一吐出的「规范化候选链接」。
 *
 * <p>Source 只负责「找链接」，不关心归属哪部片、也不入库——归属识别与落库分别由
 * Matcher / Ingestor 负责（见 {@code docs/资源聚合与检测设计.md}）。所有来源
 * （pansou / 观影 / 文档 / 其它爬虫）都产出本对象，下游一视同仁。
 */
@Data
public class RawLink {

    /** 网盘类型：baidu/quark/xunlei/uc/aliyun/tianyi/mobile/115/123/magnet ... */
    private String panType;
    /** 分享链接（不含提取码更佳；提取码单列在 {@link #password}）。 */
    private String url;
    /** 提取码（可空）。 */
    private String password;
    /** 资源标题/说明（含「全40集/1080P/合集」等信息，供相关性判断与展示）。 */
    private String note;
    /** 来源标识：pansou / gying / manual / ...（落到 media_link.source）。 */
    private String source;
    /** 来源内唯一 id（幂等/溯源用，可空）。 */
    private String sourceItemId;
    /**
     * 来源搜索命中的详情页标题（如 gying 的「昨夜将至（2026）」）。
     * 用于入库相关性：详情页已命中片名时，页内英文磁力名也可入库。
     */
    private String matchTitle;
    /** 来源发布时间（可空，作新鲜度信号）。 */
    private LocalDateTime publishedAt;

    public static RawLink of(String panType, String url, String password, String note, String source) {
        RawLink r = new RawLink();
        r.panType = panType;
        r.url = url;
        r.password = password;
        r.note = note;
        r.source = source;
        return r;
    }
}
