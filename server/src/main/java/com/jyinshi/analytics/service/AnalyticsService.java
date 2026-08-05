package com.jyinshi.analytics.service;

import com.jyinshi.analytics.dto.EventRequest;
import com.jyinshi.analytics.dto.KeywordStat;
import com.jyinshi.analytics.dto.MediaHeat;
import com.jyinshi.analytics.entity.ContentEvent;
import com.jyinshi.analytics.mapper.AnalyticsStatMapper;
import com.jyinshi.analytics.mapper.ContentEventMapper;
import com.jyinshi.ops.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 行为埋点（analytics 域）。其它域 / 前台只通过本 service 上报，不直接写事件表。
 *
 * <p>埋点是旁路逻辑：异步落库、异常吞掉，绝不影响主请求。热度与各类榜单由
 * {@code AnalyticsAdminService} / 热度回写 job 基于这些事件计算。
 */
@Slf4j
@Service
public class AnalyticsService {

    public static final String EVENT_SEARCH = "search";
    public static final String EVENT_LINK_CLICK = "link_click";
    /** 信息流/榜单卡片点击：tag=来源(home/ranking/search...)，num=排位。 */
    public static final String EVENT_CARD_CLICK = "card_click";

    /** 允许前台上报的类型。 */
    private static final Set<String> CLIENT_TYPES =
            Set.of(EVENT_SEARCH, EVENT_LINK_CLICK, EVENT_CARD_CLICK);

    /** 前台「大家在搜」热词缓存键前缀。 */
    private static final String HOT_KW_CACHE_PREFIX = "jyinshi:hotkw:";
    /** 热词缓存有效期：搜索行为变化不快，5 分钟足够新鲜，避免每次点首页都聚合一次。 */
    private static final Duration HOT_KW_TTL = Duration.ofMinutes(5);
    /** 热词最短长度：单字关键词噪声大、指向不明，不进榜。 */
    private static final int HOT_KW_MIN_LEN = 2;

    private final ContentEventMapper eventMapper;
    private final AnalyticsStatMapper statMapper;
    private final StringRedisTemplate redis;
    private final SensitiveWordService sensitiveWordService;

    public AnalyticsService(ContentEventMapper eventMapper,
                            AnalyticsStatMapper statMapper,
                            StringRedisTemplate redis,
                            SensitiveWordService sensitiveWordService) {
        this.eventMapper = eventMapper;
        this.statMapper = statMapper;
        this.redis = redis;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 前台上报（搜索/网盘点击/卡片点击），异步。非法类型直接忽略。 */
    @Async("analyticsExecutor")
    public void track(EventRequest req, String visitorId) {
        if (req == null || !StringUtils.hasText(req.getType()) || !CLIENT_TYPES.contains(req.getType())) {
            return;
        }
        ContentEvent e = new ContentEvent();
        e.setEventType(req.getType());
        e.setVisitorId(normalizeVisitor(visitorId));
        e.setMediaId(req.getMediaId());
        if (StringUtils.hasText(req.getKeyword())) {
            String kw = req.getKeyword().trim();
            e.setKeyword(kw.length() > 128 ? kw.substring(0, 128) : kw);
        }
        if (StringUtils.hasText(req.getTag())) {
            String t = req.getTag().trim();
            e.setTag(t.length() > 32 ? t.substring(0, 32) : t);
        }
        e.setNum(req.getNum());
        save(e);
    }

    /**
     * 近 days 天各片的行为热度分（供热度回写 job 用）。
     * link_click 权重高于 card_click，并按天数指数衰减（越近越重）。
     */
    public List<MediaHeat> recentHeat(int days) {
        int d = days <= 0 ? 14 : Math.min(days, 90);
        LocalDateTime since = LocalDateTime.now().minusDays(d);
        return statMapper.recentHeat(since);
    }

    /**
     * 前台「大家在搜」热词：近 days 天真实搜索词按频次倒序。
     *
     * <p>会剔除命中敏感词、过短的词，并按归一化去重（避免大小写/空格造成的重复条目）。
     * 结果按关键词榜的口径先多取一些再过滤，最终截到 limit。整榜缓存进 Redis
     * （{@link #HOT_KW_TTL}），Redis 不可用时直接查库降级，不影响首页。
     */
    public List<String> hotKeywords(int days, int limit) {
        int d = days <= 0 ? 14 : Math.min(days, 90);
        int n = limit <= 0 ? 10 : Math.min(limit, 30);
        String cacheKey = HOT_KW_CACHE_PREFIX + d + ':' + n;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached.isEmpty() ? List.of() : List.of(cached.split("\n"));
            }
        } catch (Exception ex) {
            log.warn("热词缓存读取失败：{}", ex.getMessage());
        }

        LocalDateTime since = LocalDateTime.now().minusDays(d);
        LocalDateTime until = LocalDateTime.now();
        // 多取几倍：敏感/过短/重复会被过滤掉，留足余量才能凑够 n 条。
        List<KeywordStat> raw = statMapper.topSearches(since, until, n * 4);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>(n);
        for (KeywordStat s : raw) {
            String kw = s.getKeyword() == null ? "" : s.getKeyword().trim();
            if (kw.length() < HOT_KW_MIN_LEN || sensitiveWordService.isBlocked(kw)) {
                continue;
            }
            if (!seen.add(kw.toLowerCase())) {
                continue;
            }
            out.add(kw);
            if (out.size() >= n) {
                break;
            }
        }

        try {
            redis.opsForValue().set(cacheKey, String.join("\n", out), HOT_KW_TTL);
        } catch (Exception ex) {
            log.warn("热词缓存写入失败：{}", ex.getMessage());
        }
        return out;
    }

    private String normalizeVisitor(String visitorId) {
        if (!StringUtils.hasText(visitorId)) {
            return null;
        }
        String v = visitorId.trim();
        return v.length() > 36 ? v.substring(0, 36) : v;
    }

    private void save(ContentEvent e) {
        try {
            e.setCreatedAt(LocalDateTime.now());
            eventMapper.insert(e);
        } catch (Exception ex) {
            log.warn("埋点写入失败 type={} mediaId={} kw={}: {}",
                    e.getEventType(), e.getMediaId(), e.getKeyword(), ex.getMessage());
        }
    }
}
