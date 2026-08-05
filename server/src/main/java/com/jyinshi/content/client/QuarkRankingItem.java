package com.jyinshi.content.client;

/**
 * 夸克热榜一条（展示 / 灌库用）。
 */
public record QuarkRankingItem(
        String title,
        String year,
        String area,
        String category,
        String channel,
        String scoreAvg,
        String hotScore,
        String poster,
        String actors,
        String desc,
        String videoId,
        String ranking
) {
}
