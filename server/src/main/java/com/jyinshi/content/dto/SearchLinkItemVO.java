package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 站内链召回条目（供流式搜索优先推送）。
 * 一条 = 一条可转存/可打开的网盘分享。
 */
@Data
public class SearchLinkItemVO {

    /** media_link.id；站内链转存传这个。 */
    private Long id;
    /** 展示标题：优先 note，空则回退 media.title。 */
    private String title;
    private String panType;
    private String panLabel;
    private String source;
    /** 所属片 id（点标题可进详情，可选）。 */
    private Long mediaId;
    private String mediaTitle;
    /** source=self/manual 时前端标「站长精选」。 */
    private boolean local;
    /**
     * 追更识别的最新集数/日期（来自 transfer_monitor，非 TMDB）。
     * 展示如 {@code 79} / {@code 7.15}；无追更数据时为 null。
     */
    private String latestEpisode;
    /**
     * 源地址：磁力/电驴/自营直显；聚合网盘为 null（走 linkId 转存）。
     * 与详情页护城河口径一致。
     */
    private String url;
}
