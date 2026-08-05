package com.jyinshi.content.dto;

import lombok.Data;

import java.util.List;

/** 新增/编辑每日更新。 */
@Data
public class DailySaveRequest {

    private Long id;
    /** 已有剧；与 title 二选一（都没有则报错）。 */
    private Long mediaId;
    /** 库里没有时：用片名新建一条瘦 media 再绑定。 */
    private String title;
    /** 新建时类型，默认 tv。 */
    private String type;
    /** 新建时年份，可选。 */
    private Integer year;
    private Integer pinned;
    private Integer sort;
    private Integer enabled;
    /** 上游链（含指定账号）；后端据此建/改 transfer 追更。 */
    private List<DailyLinkInput> links;

    /** 追更节奏 · 检查日：0-6 对应周日-周六，逗号分隔；空=每天。 */
    private String checkDays;
    /** 追更节奏 · 检查时段："起-止" 小时（止不含），如 "18-23"；空=用全局巡检时段。 */
    private String checkHours;
    /** 追更节奏 · 检查间隔（分钟）；空=用全局巡检间隔。 */
    private Integer checkInterval;
}
