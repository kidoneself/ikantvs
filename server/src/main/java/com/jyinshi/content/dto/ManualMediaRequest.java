package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅录入：TMDB / 豆瓣都没有时，人工录入一个「仅标题」条目（meta_source=none）。
 * 后面有了外部 id 可再补抓。
 */
@Data
public class ManualMediaRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    /** movie/tv/anime/variety */
    private String type;

    private Integer year;

    private String poster;

    private String overview;

    private String genres;

    private Boolean publish;
}
