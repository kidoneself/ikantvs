package com.jyinshi.analytics.dto;

import lombok.Data;

/** 搜索词聚合：词 + 次数。 */
@Data
public class KeywordStat {
    private String keyword;
    private Long cnt;
}
