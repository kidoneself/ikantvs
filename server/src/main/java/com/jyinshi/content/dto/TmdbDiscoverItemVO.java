package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 前台搜索「TMDB 发现」候选：本地 0 命中时展示，用户点击再入库。
 */
@Data
public class TmdbDiscoverItemVO {

    private Integer tmdbId;
    /** movie / tv */
    private String type;
    private String title;
    private String originalTitle;
    private Integer year;
    private String poster;
    private java.math.BigDecimal rating;
    /** 已在本地库且前台可见时回填，点击直接进详情、无需再 import。 */
    private Long localId;
}
