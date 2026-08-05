package com.jyinshi.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域与来源校验。生产环境通过 CORS_ALLOWED_ORIGINS 配置前台/后台页面 Origin（逗号分隔）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.cors")
public class CorsProperties {

    /** 是否校验 Origin/Referer（生产建议 true；本地 dev 可 false）。 */
    private boolean enforceOrigin = false;

    /**
     * 允许的来源，逗号分隔。例：{@code https://ikantvs.com,http://103.24.216.84:8080}
     */
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173,"
            + "http://localhost:5174,http://127.0.0.1:5174";

    public List<String> originList() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
