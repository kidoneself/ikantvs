package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 后台人工编辑 media（不触发 TMDB 重采）。
 */
@Data
public class MediaUpdateRequest {

    private String title;
    private String overview;
    private String poster;
    private Integer year;

    /** 0草稿 1已发布 2下架 */
    private Integer pubStatus;

    private Integer hot;

    /** 0普通 1精品 2专区 */
    private Integer tier;

    /** 1=前台隐藏 */
    private Integer searchHidden;

    /** 手动补挂 TMDB id（仅写 id，不触发重采） */
    private Integer tmdbId;

    /** 手动补挂豆瓣 subject id（仅写 id，不触发重采） */
    private String doubanId;
}
