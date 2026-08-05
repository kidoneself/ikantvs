package com.jyinshi.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页「已更新」一条：后台每日更新看板（daily_update）。
 * 卡片主体复用 {@link MediaVO}，另带追更集数与真实更新时间。
 */
@Data
public class DailyFeedItemVO {

    /** 卡片主体（海报/标题/评分/题材等）。 */
    private MediaVO media;

    /** 更新徽标：「更新至第 X 集」等真实追更集数。 */
    private String updateNote;

    /** 真实更新时间（补到新集数 / 看板编辑时间）。 */
    private LocalDateTime updatedAt;

    /** 固定 true（历史字段，兼容前端）。 */
    private boolean curated;
}
