package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建/编辑榜单（id 为空=新建）。 */
@Data
public class RankingSaveRequest {

    private Long id;

    @NotBlank(message = "榜单名不能为空")
    private String name;

    @NotBlank(message = "slug 不能为空")
    private String slug;

    private String description;

    private Integer sort;

    /** 1 上架 0 下架，默认上架。 */
    private Integer enabled;
}
