package com.jyinshi.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 前台 media 列表/搜索 Redis 缓存。 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.search.cache")
public class SearchCacheProperties {

    /** 是否启用（Redis 不可用时自动降级查库）。 */
    private boolean enabled = true;

    /** 缓存 TTL（分钟）。 */
    private int ttlMinutes = 3;
}
