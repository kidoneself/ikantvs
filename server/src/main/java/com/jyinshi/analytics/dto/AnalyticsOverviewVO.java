package com.jyinshi.analytics.dto;

import lombok.Data;

import java.util.List;

/** 数据洞察总览（后台）。指标均带环比（相对上一同长周期）。 */
@Data
public class AnalyticsOverviewVO {

    private int days;

    /** 独立访客 */
    private MetricDelta visitors;
    /** 搜索次数 */
    private MetricDelta searches;
    /** 卡片点击（首页/榜单/搜索结果点片） */
    private MetricDelta cardClicks;
    /** 网盘链点击 */
    private MetricDelta linkClicks;

    /** 热搜词 */
    private List<KeywordStat> topSearches;
    /** 求片榜：0 结果搜索词 */
    private List<KeywordStat> demandGaps;
    /** 热门卡片点击 */
    private List<MediaStat> topCardClicked;
    /** 网盘链点击榜 */
    private List<MediaStat> topLinkClicked;
}
