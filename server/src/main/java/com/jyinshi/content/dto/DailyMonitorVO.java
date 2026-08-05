package com.jyinshi.content.dto;

import lombok.Data;

/** 某条追更链的运行态（展示用，来自 transfer 域）。 */
@Data
public class DailyMonitorVO {

    private String panType;
    /** 上游源分享链。 */
    private String shareUrl;
    private String accountName;
    /** active/invalid/paused。 */
    private String status;
    /** 我方分享链（首转生成，用户看/点这个）。 */
    private String myShareUrl;
    /** 该盘最新集数/日期（已做智能提取）。 */
    private String latestEpisode;
}
