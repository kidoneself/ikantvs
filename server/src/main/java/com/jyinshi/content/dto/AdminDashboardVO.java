package com.jyinshi.content.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 后台仪表盘统计。 */
@Data
@Builder
public class AdminDashboardVO {

    private long total;
    private long published;
    private long draft;
    private long offline;

    /** type → count */
    private Map<String, Long> byType;

    private boolean r2Ready;
}
