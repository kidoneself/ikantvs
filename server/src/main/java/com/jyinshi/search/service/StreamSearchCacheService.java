package com.jyinshi.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 流式搜索结果缓存（Redis）。有新鲜缓存先秒推，再按冷却决定是否后台补搜。
 */
@Slf4j
@Service
public class StreamSearchCacheService {

    private static final String KEY_PREFIX = "search:stream:v1:";
    private static final Duration TTL = Duration.ofHours(6);
    /** 普通冷却 10 分钟（对齐老站普通热度档）。 */
    private static final long COOLDOWN_SECONDS = 10 * 60L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StreamSearchCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public CachedBundle get(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(keyword));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, CachedBundle.class);
        } catch (Exception e) {
            log.warn("[search-cache] 读取失败 kw={}: {}", keyword, e.getMessage());
            return null;
        }
    }

    public void save(String keyword, List<CachedItem> items) {
        if (!StringUtils.hasText(keyword) || items == null || items.isEmpty()) {
            return;
        }
        try {
            CachedBundle bundle = new CachedBundle();
            bundle.setItems(new ArrayList<>(items));
            bundle.setRefreshedAtEpochSec(Instant.now().getEpochSecond());
            redis.opsForValue().set(key(keyword), objectMapper.writeValueAsString(bundle),
                    TTL.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[search-cache] 写入失败 kw={}: {}", keyword, e.getMessage());
        }
    }

    public boolean isFresh(CachedBundle bundle) {
        if (bundle == null || bundle.getRefreshedAtEpochSec() == null) {
            return false;
        }
        long age = Instant.now().getEpochSecond() - bundle.getRefreshedAtEpochSec();
        return age >= 0 && age < COOLDOWN_SECONDS;
    }

    private static String key(String keyword) {
        return KEY_PREFIX + keyword.trim().toLowerCase();
    }

    @Data
    public static class CachedBundle {
        private Long refreshedAtEpochSec;
        private List<CachedItem> items = new ArrayList<>();
    }

    @Data
    public static class CachedItem {
        private String title;
        private String url;
        private String password;
        private String panType;
        private String source;
        private String datetime;

        public static CachedItem of(String title, String url, String password,
                                    String panType, String source, String datetime) {
            CachedItem i = new CachedItem();
            i.title = title;
            i.url = url;
            i.password = password;
            i.panType = panType;
            i.source = source;
            i.datetime = datetime;
            return i;
        }
    }
}
