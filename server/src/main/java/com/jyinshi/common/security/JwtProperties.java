package com.jyinshi.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（对应 application.yml 的 {@code jyinshi.jwt.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.jwt")
public class JwtProperties {

    /** 签名密钥，至少 32 字节。生产务必通过环境变量覆盖。 */
    private String secret;

    /** 过期时间（毫秒）。 */
    private long expirationMs = 604800000L;
}
