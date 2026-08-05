package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.client.FetchedMetadata;
import com.jyinshi.content.client.TmdbClient;
import com.jyinshi.content.dto.MediaImportRequest;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.dto.TmdbDiscoverItemVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.ops.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 前台搜索未命中时的 TMDB 发现：只展示候选，用户点击卡片再入库。
 */
@Slf4j
@Service
public class MediaDiscoveryService {

    /** 最短关键词长度（过短 TMDB 结果过多且不准）。 */
    static final int MIN_QUERY_LEN = 2;
    /** 单次最多展示条数。 */
    static final int MAX_RESULTS = 8;
    /**
     * 同一关键词 TMDB 结果的缓存有效期（毫秒）。TMDB 目录很稳定，可以长缓存以省调用；
     * 「在不在库」不进缓存，每次请求实时计算，所以刚入库的版本会立即从「未入库」里消失。
     */
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L;
    /**
     * discover 每秒最多打 TMDB 的次数（限流兜底）。TMDB 洪水控制约 50 req/s（按 IP），
     * 这里留足余量，超了就当空结果返回、不写缓存，绝不把服务器打到 429。
     */
    private static final int MAX_TMDB_CALLS_PER_SEC = 40;

    private final TmdbClient tmdbClient;
    private final MediaMapper mediaMapper;
    private final MediaService mediaService;
    private final SensitiveWordService sensitiveWordService;

    /** 归一化关键词 → 缓存条目。 */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    /** 单飞锁：同一关键词并发 miss 时只放一个线程去打 TMDB，其余等结果，消除惊群。 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /** 每秒调用计数（固定窗口），仅在缓存 miss 时检查。 */
    private final Object rateLock = new Object();
    private long rateWindowStartMs = 0;
    private int rateWindowCount = 0;

    public MediaDiscoveryService(TmdbClient tmdbClient,
                                 MediaMapper mediaMapper,
                                 MediaService mediaService,
                                 SensitiveWordService sensitiveWordService) {
        this.tmdbClient = tmdbClient;
        this.mediaMapper = mediaMapper;
        this.mediaService = mediaService;
        this.sensitiveWordService = sensitiveWordService;
    }

    /**
     * TMDB 候选（不入库）。过短/敏感/TMDB 未配置时返回空列表。
     * TMDB 结果按关键词长缓存（{@link #CACHE_TTL_MS}），localId 每次实时标注。
     *
     * @param type 可选，movie/tv/anime/variety；TMDB 只有 movie/tv，anime/variety 按 tv 过滤。
     */
    public List<TmdbDiscoverItemVO> discover(String query, String type) {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY_LEN) {
            return List.of();
        }
        if (sensitiveWordService.isBlocked(q)) {
            return List.of();
        }
        if (!tmdbClient.isConfigured()) {
            return List.of();
        }
        String key = normalizeKey(q) + "|" + normalizeTypeFilter(type);
        // localId 不进缓存：每次实时标注，保证刚入库的版本能立刻从「未入库」里去掉。
        return annotateLocal(loadBase(q, type, key));
    }

    /**
     * 取 TMDB 结果（不含 localId）：命中缓存直接返回；miss 时走单飞 + 每秒限流兜底。
     */
    private List<TmdbDiscoverItemVO> loadBase(String q, String type, String key) {
        CacheEntry hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.atMs < CACHE_TTL_MS) {
            return hit.items;
        }
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                // 拿到锁后二次检查：可能前一个线程已经把结果写进缓存了
                long t = System.currentTimeMillis();
                CacheEntry again = cache.get(key);
                if (again != null && t - again.atMs < CACHE_TTL_MS) {
                    return again.items;
                }
                if (!acquireTmdbSlot()) {
                    // 触发每秒限流：本次不打 TMDB、也不写缓存，返回空，下次再试
                    log.warn("[discover] TMDB 每秒限流兜底触发，跳过关键词: {}", key);
                    return List.of();
                }
                List<TmdbDiscoverItemVO> base = fetchAndMap(q, type);
                cache.put(key, new CacheEntry(t, base));
                if (cache.size() > 500) {
                    cache.entrySet().removeIf(e -> t - e.getValue().atMs >= CACHE_TTL_MS);
                }
                return base;
            }
        } finally {
            locks.remove(key, lock);
        }
    }

    /** 固定窗口每秒限流：本秒内未超上限返回 true（放行一次 TMDB 调用）。 */
    private boolean acquireTmdbSlot() {
        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            if (now - rateWindowStartMs >= 1000) {
                rateWindowStartMs = now;
                rateWindowCount = 0;
            }
            if (rateWindowCount >= MAX_TMDB_CALLS_PER_SEC) {
                return false;
            }
            rateWindowCount++;
            return true;
        }
    }

    /** 用户点击发现卡片：入库（已存在则直接返回）并尝试发布。 */
    public MediaVO importOnDemand(Integer tmdbId, String type) {
        if (tmdbId == null || tmdbId <= 0) {
            throw new BizException("无效的 TMDB ID");
        }
        if (!StringUtils.hasText(type)) {
            throw new BizException("请指定类型 movie 或 tv");
        }
        String mt = type.trim().toLowerCase();
        if (!"movie".equals(mt) && !"tv".equals(mt)) {
            throw new BizException("不支持的类型：" + type);
        }
        Media existing = findLocal(tmdbId, mt);
        if (existing != null && MediaPublicVisibility.isVisible(existing, sensitiveWordService)) {
            MediaPublicVisibility.maskOverview(existing, sensitiveWordService);
            return MediaVO.from(existing);
        }
        MediaImportRequest req = new MediaImportRequest();
        req.setTmdbId(tmdbId);
        req.setType(mt);
        req.setPublish(true);
        MediaVO vo = mediaService.importByExternalId(req);
        Media saved = mediaMapper.selectById(vo.getId());
        if (saved != null && !MediaPublicVisibility.isVisible(saved, sensitiveWordService)) {
            throw new BizException("该内容暂不可用");
        }
        if (saved != null) {
            MediaPublicVisibility.maskOverview(saved, sensitiveWordService);
            return MediaVO.from(saved);
        }
        return vo;
    }

    /** 只做 TMDB 抓取 + 过滤 + 映射（不含 localId），结果可长缓存。 */
    private List<TmdbDiscoverItemVO> fetchAndMap(String query, String typeFilter) {
        List<FetchedMetadata> raw = tmdbClient.searchMulti(query);
        if (raw.isEmpty()) {
            return List.of();
        }
        String wantType = tmdbTypeFilter(typeFilter);
        List<TmdbDiscoverItemVO> out = new ArrayList<>();
        for (FetchedMetadata m : raw) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            if (m.getTmdbId() == null || !StringUtils.hasText(m.getType())) {
                continue;
            }
            if (wantType != null && !wantType.equals(m.getType())) {
                continue;
            }
            // 口径同前台可见性：只有标题命中 block 才排除（简介命中不拦，入库后展示时打码）。
            if (sensitiveWordService.isBlocked(m.getTitle())) {
                continue;
            }
            TmdbDiscoverItemVO vo = new TmdbDiscoverItemVO();
            vo.setTmdbId(m.getTmdbId());
            vo.setType(m.getType());
            vo.setTitle(m.getTitle());
            vo.setOriginalTitle(m.getOriginalTitle());
            vo.setYear(m.getYear());
            vo.setPoster(m.getPoster());
            vo.setRating(m.getRating());
            out.add(vo);
        }
        return out;
    }

    /**
     * 用缓存里的 TMDB 结果拷贝出新列表并实时回填 localId（在库且前台可见时）。
     * 不改缓存里的对象，避免并发下污染缓存或让 localId 变陈旧。
     */
    private List<TmdbDiscoverItemVO> annotateLocal(List<TmdbDiscoverItemVO> base) {
        if (base.isEmpty()) {
            return base;
        }
        Map<String, Long> localByKey = indexLocalIds(base);
        List<TmdbDiscoverItemVO> out = new ArrayList<>(base.size());
        for (TmdbDiscoverItemVO src : base) {
            TmdbDiscoverItemVO vo = new TmdbDiscoverItemVO();
            vo.setTmdbId(src.getTmdbId());
            vo.setType(src.getType());
            vo.setTitle(src.getTitle());
            vo.setOriginalTitle(src.getOriginalTitle());
            vo.setYear(src.getYear());
            vo.setPoster(src.getPoster());
            vo.setRating(src.getRating());
            vo.setLocalId(localByKey.get(localKey(src.getTmdbId(), src.getType())));
            out.add(vo);
        }
        return out;
    }

    /** 一次 IN 查出候选里已在库且前台可见的条目，返回 localKey → mediaId。 */
    private Map<String, Long> indexLocalIds(List<TmdbDiscoverItemVO> base) {
        List<Integer> ids = base.stream()
                .map(TmdbDiscoverItemVO::getTmdbId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> map = new LinkedHashMap<>();
        for (Media m : mediaMapper.selectList(Wrappers.<Media>lambdaQuery()
                .in(Media::getTmdbId, ids))) {
            if (m.getTmdbId() != null && StringUtils.hasText(m.getType())
                    && MediaPublicVisibility.isVisible(m, sensitiveWordService)) {
                map.put(localKey(m.getTmdbId(), m.getType()), m.getId());
            }
        }
        return map;
    }

    private Media findLocal(int tmdbId, String type) {
        return mediaMapper.selectOne(Wrappers.<Media>lambdaQuery()
                .eq(Media::getTmdbId, tmdbId)
                .eq(Media::getType, type)
                .last("limit 1"));
    }

    private static String localKey(int tmdbId, String type) {
        return tmdbId + "|" + (type == null ? "" : type.toLowerCase());
    }

    private static String normalizeKey(String q) {
        return q.trim().toLowerCase();
    }

    /** anime/variety 在 TMDB 侧按 tv 过滤；movie/tv 原样；空=不限。 */
    private static String tmdbTypeFilter(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return switch (type.trim().toLowerCase()) {
            case "movie" -> "movie";
            case "tv", "anime", "variety" -> "tv";
            default -> null;
        };
    }

    private static String normalizeTypeFilter(String type) {
        String t = tmdbTypeFilter(type);
        return t == null ? "*" : t;
    }

    private record CacheEntry(long atMs, List<TmdbDiscoverItemVO> items) {
    }
}
