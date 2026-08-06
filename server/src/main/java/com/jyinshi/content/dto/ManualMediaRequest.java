package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手工录入条目（meta_source=manual）。可带海报 URL（建议先本地上传）。
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
