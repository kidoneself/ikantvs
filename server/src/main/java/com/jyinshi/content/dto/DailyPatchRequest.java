package com.jyinshi.content.dto;

import lombok.Data;

/** 快捷切换上架 / 置顶 / 排序 / 完结、手动纠正集数（不动链接）。 */
@Data
public class DailyPatchRequest {

    private Integer enabled;
    private Integer pinned;
    private Integer sort;
    /** 手动填/改「更新至第 X 集/日期」；传空串表示清除手动值、回到自动。 */
    private String manualEpisode;
    /** 1=标完结停追更；0=取消完结恢复追更。 */
    private Integer ended;
}
