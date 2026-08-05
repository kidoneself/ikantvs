package com.jyinshi.common.security.ratelimit;

import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.common.security.ip.ClientIpResolver;
import com.jyinshi.common.security.ip.IpGuardProperties;
import com.jyinshi.common.security.ip.IpGuardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 限流拦截器：读取 Controller 方法上的 {@link RateLimit}，Redis 固定窗口计数超限即拒绝。
 * 按 IP 限流时，超限还会累计到自动封禁计数器（{@link IpGuardService}），频繁触发者自动封禁。
 *
 * <p>在 preHandle 抛出 {@link BizException}，由全局异常处理器统一转 {@code Result}。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final DefaultRedisScript<Long> INCR_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;
    private final IpGuardService ipGuardService;
    private final IpGuardProperties props;

    public RateLimitInterceptor(StringRedisTemplate redis,
                                IpGuardService ipGuardService,
                                IpGuardProperties props) {
        this.redis = redis;
        this.ipGuardService = ipGuardService;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!props.isEnabled() || !(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RateLimit rl = hm.getMethodAnnotation(RateLimit.class);
        if (rl == null) {
            return true;
        }

        String ip = ClientIpResolver.resolve(request);
        // 白名单 / 内网 / 回环 IP 完全跳过限流（方便自测与内部调用），也不会被自动封禁
        if (isExempt(ip)) {
            return true;
        }
        String redisKey = buildKey(rl, request, ip);
        Long count = redis.execute(INCR_EXPIRE, List.of(redisKey), String.valueOf(rl.time()));
        if (count != null && count > rl.count()) {
            log.warn("[限流] key={} count={} limit={}", redisKey, count, rl.count());
            if (rl.limitType() == RateLimit.LimitType.IP) {
                boolean banned = ipGuardService.recordRateLimitHit(ip);
                if (banned) {
                    log.warn("[自动封禁] IP {} 因频繁触发限流已被封禁", ip);
                }
            }
            throw new BizException(ResultCode.TOO_MANY_REQUESTS.getCode(),
                    rl.message() + "，请 " + rl.time() + " 秒后再试");
        }
        return true;
    }

    private boolean isExempt(String ip) {
        return props.whitelistSet().contains(ip) || ClientIpResolver.isPrivateOrLoopback(ip);
    }

    private String buildKey(RateLimit rl, HttpServletRequest request, String ip) {
        String prefix = rl.key().isEmpty() ? request.getRequestURI() : rl.key();
        return switch (rl.limitType()) {
            case USER -> {
                Long uid = AuthContext.currentUserIdOrNull();
                yield "rate_limit:" + prefix + ":u:" + (uid != null ? uid : "ip:" + ip);
            }
            case GLOBAL -> "rate_limit:" + prefix + ":global";
            default -> "rate_limit:" + prefix + ":ip:" + ip;
        };
    }
}
