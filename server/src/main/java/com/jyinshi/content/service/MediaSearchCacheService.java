package com.jyinshi.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.content.config.SearchCacheProperties;
import com.jyinshi.content.dto.MediaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 前台 media 搜索/列表 Redis 缓存。
 *
 * <p>版本号失效：内容变更时递增 ver，旧 key 靠 TTL 自然过期，避免 KEYS 扫描。
 */
@Slf4j
@Service
public class MediaSearchCacheService {

    private static final String KEY_PREFIX = "jyinshi:search:v1:";
    private static final String VERSION_KEY = "jyinshi:search:ver";
    private static final TypeReference<PageResult<MediaVO>> PAGE_TYPE = new TypeReference<>() {};

    private final SearchCacheProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MediaSearchCacheService(SearchCacheProperties props,
                                   StringRedisTemplate redis,
                                   ObjectMapper objectMapper) {
        this.props = props;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    boolean isEnabled() {
        return props.isEnabled();
    }

    Optional<PageResult<MediaVO>> get(MediaSearchCacheKey key) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(redisKey(key));
            if (!org.springframework.util.StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, PAGE_TYPE));
        } catch (Exception e) {
            log.warn("搜索缓存读取失败，降级查库: {}", e.getMessage());
            return Optional.empty();
        }
    }

    void put(MediaSearchCacheKey key, PageResult<MediaVO> result) {
        if (!props.isEnabled() || result == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            redis.opsForValue().set(redisKey(key), json, Duration.ofMinutes(Math.max(1, props.getTtlMinutes())));
        } catch (Exception e) {
            log.warn("搜索缓存写入失败: {}", e.getMessage());
        }
    }

    /** 内容发布/改标题/入库后调用，使旧搜索缓存失效。 */
    void invalidateAll() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("搜索缓存失效失败: {}", e.getMessage());
        }
    }

    private String redisKey(MediaSearchCacheKey key) {
        long ver = 0;
        try {
            String v = redis.opsForValue().get(VERSION_KEY);
            if (org.springframework.util.StringUtils.hasText(v)) {
                ver = Long.parseLong(v);
            }
        } catch (Exception ignored) {
            // 读版本失败时用 0，不影响查库
        }
        return KEY_PREFIX + ver + ":" + key.digest();
    }
}
