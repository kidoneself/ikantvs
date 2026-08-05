package com.jyinshi.content.service;

import com.jyinshi.analytics.dto.MediaHeat;
import com.jyinshi.analytics.service.AnalyticsService;
import com.jyinshi.content.mapper.MediaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 热度回写（content 域）：把 analytics 的近期行为分叠加到 media.hot 上。
 *
 * <p>{@code hot = hot_seed + 行为分 × behaviorWeight}。行为数据属 analytics 域，只经其 service
 * 读取，不跨域直连 content_event 表（架构铁律 3）。无近期行为的片会归位到 hot_seed，
 * 既有 TMDB 种子 / 后台手工基线不会被冲掉。
 *
 * <p><b>为什么要乘 behaviorWeight（默认 50000）</b>：hot_seed 用的是 TMDB popularity，量级
 * 高达上万（本库最大 13769），而单片近 14 天的原始行为分往往只有个位到几百。若直接相加，
 * 真正被本站用户点爆的冷门片永远压不过 TMDB 人气榜的大热门——「最热」就退化成了「TMDB 热门」。
 * 把行为分放大到远超种子上限后，只要有真实行为的片都会浮到前面并按行为量排序，
 * 种子只在「无人问津的长尾」之间以及行为持平时做兜底次序。即「最热 = 真行为优先」。
 */
@Slf4j
@Service
public class HotScoreService {

    private final AnalyticsService analyticsService;
    private final MediaMapper mediaMapper;

    /** 行为热度统计窗口（天）。 */
    @Value("${jyinshi.content.hot.window-days:14}")
    private int windowDays;

    /** 总开关：关掉则不回写（hot 保持基线）。 */
    @Value("${jyinshi.content.hot.enabled:true}")
    private boolean enabled;

    /**
     * 行为分权重：叠加时 hot = hot_seed + score × 该值。须大于 hot_seed 的量级（本库上限约 1.4 万），
     * 才能保证任何有真实行为的片都排在纯种子片之前，实现「行为优先」。调小=更看重 TMDB 人气。
     */
    @Value("${jyinshi.content.hot.behavior-weight:50000}")
    private int behaviorWeight;

    public HotScoreService(AnalyticsService analyticsService, MediaMapper mediaMapper) {
        this.analyticsService = analyticsService;
        this.mediaMapper = mediaMapper;
    }

    /** 默认每天 03:30 回写一次。 */
    @Scheduled(cron = "${jyinshi.content.hot.cron:0 30 3 * * *}")
    public void refreshHot() {
        if (!enabled) {
            return;
        }
        try {
            List<MediaHeat> heats = analyticsService.recentHeat(windowDays);
            // 先全部归位到基线，让上一轮有热度、本轮无行为的片衰减回 hot_seed。
            mediaMapper.resetHotToSeed();
            int applied = 0;
            for (MediaHeat h : heats) {
                if (h.getMediaId() == null || h.getScore() == null || h.getScore() <= 0) {
                    continue;
                }
                // 放大后叠加，让真实行为压过 TMDB 种子人气（详见类注释）。
                applied += mediaMapper.applyHeat(h.getMediaId(), h.getScore() * behaviorWeight);
            }
            log.info("热度回写完成：窗口 {} 天，行为片 {} 部", windowDays, applied);
        } catch (Exception ex) {
            log.error("热度回写失败：{}", ex.getMessage(), ex);
        }
    }
}
