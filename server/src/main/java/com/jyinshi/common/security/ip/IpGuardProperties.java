package com.jyinshi.common.security.ip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IP 防护开关与阈值。总开关关闭时黑名单过滤器与限流拦截器整套旁路（方便本地开发）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.ip-guard")
public class IpGuardProperties {

    /** 总开关。false = 黑名单/限流全部旁路。生产建议 true。 */
    private boolean enabled = true;

    /** 白名单 IP（永不封禁），逗号分隔。默认含本地回环。 */
    private String whitelist = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    /** 自动封禁：窗口内被限流达到该次数则封禁。 */
    private int autoBanThreshold = 10;

    /** 自动封禁计数器窗口（秒）。 */
    private int autoBanCounterWindow = 180;

    /** 自动封禁时长（秒），默认 48 小时。 */
    private int autoBanDuration = 172800;

    public Set<String> whitelistSet() {
        if (!StringUtils.hasText(whitelist)) {
            return Set.of();
        }
        return Arrays.stream(whitelist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }
}
