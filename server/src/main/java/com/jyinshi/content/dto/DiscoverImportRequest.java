package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 前台 TMDB 发现卡片点击入库。 */
@Data
public class DiscoverImportRequest {

    @NotNull
    private Integer tmdbId;
    /** movie / tv */
    @NotBlank
    private String type;
}
