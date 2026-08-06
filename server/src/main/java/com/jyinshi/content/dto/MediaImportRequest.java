package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 单条采集请求：TMDB 链接或 ID。
 *
 * <ul>
 *   <li>{@link #url} TMDB 详情页链接，自动解析出 id+类型；</li>
 *   <li>{@link #tmdbId}+{@link #type} 直接走 TMDB。</li>
 * </ul>
 */
@Data
public class MediaImportRequest {

    /** TMDB 详情页链接，自动解析。 */
    private String url;

    private Integer tmdbId;

    /** movie/tv/anime/variety；与 tmdbId 一起用时建议填写。 */
    private String type;

    /** 是否直接发布；默认由调用方决定。 */
    private Boolean publish;
}
