package com.jyinshi.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 每日更新看板一条（后台/前台展示）。 */
@Data
public class DailyItemVO {

    private Long id;
    private Long mediaId;
    /** 以下 media 字段由后端 join 带出。 */
    private String title;
    private String poster;
    private String type;
    private Integer year;

    /** 展示：更新至第 X 集 / 日期（手动值与各盘自动值取较新，已智能提取）。 */
    private String latestEpisode;
    /** 运营手动填写的集数/日期（编辑回填用；空=纯自动）。 */
    private String manualEpisode;
    /** 最近一次真正补到新集数的时间（"更新了没"看这个）。 */
    private LocalDateTime lastUpdateAt;
    /** 最近一次巡检检查的时间（"到点查过没"）。 */
    private LocalDateTime lastCheckAt;

    /** 整体追更状态：ended 已完结 / active 正常追更 / invalid 源失效 / paused 暂停 / none 未建追更。 */
    private String status;

    /** 追更节奏（编辑回填 + 列表展示）；空=沿用全局巡检。 */
    private String checkDays;
    private String checkHours;
    private Integer checkInterval;

    private Integer pinned;
    private Integer sort;
    private Integer enabled;
    /** 1=已完结（停追更，换号不迁）。 */
    private Integer ended;

    /** 各盘追更链状态。 */
    private List<DailyMonitorVO> monitors;
}
