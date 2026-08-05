package com.jyinshi.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（jjwt 0.12.x）。
 */
@Component
public class JwtUtil {

    /** token 使用超过此时长就自动续签（滑动过期），活跃用户不掉线。 */
    private static final long RENEW_AFTER_MS = 24L * 60 * 60 * 1000;

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
    }

    /** 以用户 id 为主体签发 token（含 role，供后台鉴权）。 */
    public String issue(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role != null ? role : "user")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 兼容旧调用 */
    public String issue(Long userId, String username) {
        return issue(userId, username, "user");
    }

    /** 解析 role claim；缺省 user。 */
    public String parseRole(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            return role != null ? role : "user";
        } catch (Exception e) {
            return "user";
        }
    }

    /**
     * 滑动续期：token 距签发已超过 {@link #RENEW_AFTER_MS} 时，签发一个新 token 返回；否则返回 null。
     *
     * <p>这样只要用户在有效期内持续活动，每隔约一天自动换发新 token，等于永不掉线；
     * 闲置超过有效期才需重新登录。
     */
    public String renewIfNeeded(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date issuedAt = c.getIssuedAt();
            if (issuedAt == null) {
                return null;
            }
            if (System.currentTimeMillis() - issuedAt.getTime() < RENEW_AFTER_MS) {
                return null;
            }
            Long userId = Long.valueOf(c.getSubject());
            String username = c.get("username", String.class);
            String role = c.get("role", String.class);
            return issue(userId, username, role);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析并校验 token，返回用户 id；非法/过期返回 null。 */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
