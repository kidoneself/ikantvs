package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.client.QuarkRankingClient;
import com.jyinshi.content.client.QuarkRankingItem;
import com.jyinshi.content.config.QuarkRankingProperties;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.mapper.MediaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将夸克热榜同步进 {@code media}（开源默认灌库）。
 *
 * <p>去重：同 type + title + year；已有 TMDB 条目不覆盖元数据，只可抬热度。
 * 无搜索，仅排行；日更选不到的片可手工建瘦 media。</p>
 */
@Slf4j
@Service
public class QuarkMediaSyncService {

    private static final List<String> CHANNELS = List.of("电影", "电视剧", "综艺", "动漫", "短剧");
    private static final List<String> RANK_TYPES = List.of("最热", "新片榜", "好评榜", "热搜榜");

    private final QuarkRankingClient client;
    private final QuarkRankingProperties props;
    private final MediaMapper mediaMapper;
    private final MediaSearchCacheService searchCache;

    public QuarkMediaSyncService(QuarkRankingClient client, QuarkRankingProperties props,
                                 MediaMapper mediaMapper, MediaSearchCacheService searchCache) {
        this.client = client;
        this.props = props;
        this.mediaMapper = mediaMapper;
        this.searchCache = searchCache;
    }

    /** 全量跑一轮（各频道 × 榜型，分页翻完）。 */
    public SyncResult syncAll() {
        if (!props.isEnabled()) {
            return SyncResult.disabled();
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int maxCreate = props.getMaxCreatesPerRun(); // <=0 = 不限制
        int hit = Math.max(1, Math.min(50, props.getHitPerRank()));
        int maxPages = Math.max(1, props.getMaxPagesPerRank());

        Set<String> seen = new LinkedHashSet<>();
        boolean firstRequest = true;
        for (String channel : CHANNELS) {
            for (String rank : RANK_TYPES) {
                for (int pageIdx = 0; pageIdx < maxPages; pageIdx++) {
                    if (!firstRequest && !pauseBetweenRequests()) {
                        bumpCache();
                        return new SyncResult(true, created, updated, skipped);
                    }
                    firstRequest = false;

                    int start = pageIdx * hit;
                    List<QuarkRankingItem> page = client.fetch(channel, rank, start, hit);
                    if (page.isEmpty()) {
                        break;
                    }
                    for (QuarkRankingItem item : page) {
                        String dedupe = channel + "|" + item.title() + "|" + item.year();
                        if (!seen.add(dedupe)) {
                            skipped++;
                            continue;
                        }
                        try {
                            ApplyResult r = upsertOne(item);
                            if (r == ApplyResult.CREATED) {
                                created++;
                                if (maxCreate > 0 && created >= maxCreate) {
                                    log.info("[quark-ranking] 达新建上限 {}，本轮结束", maxCreate);
                                    bumpCache();
                                    return new SyncResult(true, created, updated, skipped);
                                }
                            } else if (r == ApplyResult.UPDATED) {
                                updated++;
                            } else {
                                skipped++;
                            }
                        } catch (Exception e) {
                            skipped++;
                            log.debug("[quark-ranking] 跳过 {}: {}", item.title(), e.getMessage());
                        }
                    }
                    // 不足一页 → 该榜已尽
                    if (page.size() < hit) {
                        break;
                    }
                }
            }
        }
        bumpCache();
        log.info("[quark-ranking] 同步完成 created={} updated={} skipped={}", created, updated, skipped);
        return new SyncResult(true, created, updated, skipped);
    }

    ApplyResult upsertOne(QuarkRankingItem item) {
        String type = mapType(item.channel());
        Integer year = parseYear(item.year());
        Media existing = findExisting(type, item.title(), year);

        if (existing != null) {
            if ("tmdb".equalsIgnoreCase(existing.getMetaSource()) && existing.getTmdbId() != null) {
                // 不覆盖 TMDB 元数据，仅抬热度种子
                int hot = parseHot(item.hotScore());
                if (hot > nz(existing.getHotSeed())) {
                    existing.setHotSeed(hot);
                    existing.setHot(Math.max(nz(existing.getHot()), hot));
                    existing.setUpdatedAt(LocalDateTime.now());
                    mediaMapper.updateById(existing);
                    return ApplyResult.UPDATED;
                }
                return ApplyResult.SKIPPED;
            }
            applyFields(existing, item, type, year);
            existing.setUpdatedAt(LocalDateTime.now());
            mediaMapper.updateById(existing);
            return ApplyResult.UPDATED;
        }

        Media m = new Media();
        applyFields(m, item, type, year);
        m.setMetaSource("quark");
        m.setPubStatus(1);
        m.setSearchHidden(0);
        m.setTier(0);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        mediaMapper.insert(m);
        return ApplyResult.CREATED;
    }

    private Media findExisting(String type, String title, Integer year) {
        var qw = Wrappers.<Media>lambdaQuery()
                .eq(Media::getType, type)
                .eq(Media::getTitle, title)
                .last("limit 1");
        if (year != null) {
            qw.eq(Media::getYear, year);
        } else {
            qw.isNull(Media::getYear);
        }
        return mediaMapper.selectOne(qw);
    }

    private void applyFields(Media m, QuarkRankingItem item, String type, Integer year) {
        m.setType(type);
        m.setTitle(item.title());
        m.setYear(year);
        if (StringUtils.hasText(item.poster())) {
            m.setPoster(item.poster().trim());
        }
        if (StringUtils.hasText(item.desc())) {
            m.setOverview(trim(item.desc(), 2000));
        }
        if (StringUtils.hasText(item.category())) {
            m.setGenres(item.category().replace(' ', ',').replace("，", ","));
        }
        if (StringUtils.hasText(item.area())) {
            m.setCountry(item.area().trim());
        }
        if (StringUtils.hasText(item.actors())) {
            m.setActors(trim(item.actors().replace(' ', ','), 500));
        }
        BigDecimal rating = parseRating(item.scoreAvg());
        if (rating != null) {
            m.setRating(rating);
        }
        int hot = parseHot(item.hotScore());
        m.setHotSeed(hot);
        m.setHot(hot);
        if (!StringUtils.hasText(m.getMetaSource()) || "none".equals(m.getMetaSource())
                || "manual".equals(m.getMetaSource()) || "quark".equals(m.getMetaSource())) {
            m.setMetaSource("quark");
        }
        if (m.getPubStatus() == null || m.getPubStatus() == 0) {
            m.setPubStatus(1);
        }
        // video_id 塞进 originalTitle 供排查（夸克无稳定数字 id）；已有原名且非 quark 键则保留
        if (StringUtils.hasText(item.videoId())) {
            String vid = item.videoId().trim();
            if (!StringUtils.hasText(m.getOriginalTitle()) || m.getOriginalTitle().contains("/")) {
                m.setOriginalTitle(trim(vid, 255));
            }
        }
    }

    private void bumpCache() {
        try {
            searchCache.invalidateAll();
        } catch (Exception ignored) {
            // cache optional
        }
    }

    /** @return false 若被中断，调用方应结束本轮 */
    private boolean pauseBetweenRequests() {
        int ms = props.getRequestIntervalMs();
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[quark-ranking] 等待间隔被中断，提前结束本轮");
            return false;
        }
    }

    static String mapType(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "movie";
        }
        return switch (channel.trim()) {
            case "电视剧", "短剧" -> "tv";
            case "动漫" -> "anime";
            case "综艺" -> "variety";
            default -> "movie";
        };
    }

    private static Integer parseYear(String year) {
        if (!StringUtils.hasText(year)) {
            return null;
        }
        try {
            int y = Integer.parseInt(year.trim().substring(0, Math.min(4, year.trim().length())));
            return y >= 1900 && y <= 2100 ? y : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseRating(String score) {
        if (!StringUtils.hasText(score)) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(score.trim());
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return v.min(new BigDecimal("10.0"));
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseHot(String hot) {
        if (!StringUtils.hasText(hot)) {
            return 0;
        }
        try {
            long v = Long.parseLong(hot.trim().replace(",", ""));
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    enum ApplyResult { CREATED, UPDATED, SKIPPED }

    public record SyncResult(boolean ran, int created, int updated, int skipped) {
        static SyncResult disabled() {
            return new SyncResult(false, 0, 0, 0);
        }
    }
}
