package com.jyinshi.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 设置榜单条目（整表替换 + 顺序即数组顺序）。 */
@Data
public class RankingItemsRequest {

    @NotNull(message = "条目列表不能为空")
    private List<Long> mediaIds;
}
