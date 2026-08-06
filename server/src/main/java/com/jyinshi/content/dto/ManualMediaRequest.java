package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅录入：无外部源时，人工录入条目（meta_source=none）。
 * 可带海报 URL；后面有了 TMDB id 可再补抓。
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
