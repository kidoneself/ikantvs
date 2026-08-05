package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 单条采集请求：给一个 TMDB 或 豆瓣 的链接/ID，采集那一个源（不做跨源兜底）。
 *
 * <p>三选一即可：
 * <ul>
 *   <li>{@link #url} 豆瓣或 TMDB 详情页链接，系统自动解析出源+id+类型；</li>
 *   <li>{@link #tmdbId}（+{@link #type}）直接走 TMDB；</li>
 *   <li>{@link #doubanId} 直接走豆瓣。</li>
 * </ul>
 */
@Data
public class MediaImportRequest {

    /** 豆瓣/TMDB 详情页链接，自动解析。 */
    private String url;

    private Integer tmdbId;

    private String doubanId;

    /** movie/tv/anime/variety；TMDB 抓取时用于区分 movie/tv 端点 */
    private String type;

    /** 抓取后是否直接发布（pub_status=1）；默认 false（草稿，待补链接） */
    private Boolean publish;
}
