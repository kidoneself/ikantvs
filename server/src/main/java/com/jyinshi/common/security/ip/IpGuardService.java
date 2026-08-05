package com.jyinshi.common.security.ip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * IP 黑名单服务（Redis 实现，跨实例共享、重启不丢）。支持自动封禁与手动封禁。
 *
 * <p>Redis Key：
 * <ul>
 *   <li>{@code ip:ban:{ip}} → 临时封禁标记（带 TTL = 到期自动解封）</li>
 *   <li>{@code ip:ban:counter:{ip}} → 被限流命中计数（窗口内累计，达阈值触发自动封禁）</li>
 *   <li>{@code ip:ban:permanent} → Set，永久封禁 IP 集合</li>
 * </ul>
 */
@Slf4j
@Service
public class IpGuardService {

    private static final String BLACKLIST_KEY = "ip:ban:";
    private static final String COUNTER_KEY = "ip:ban:counter:";
    private static final String PERMANENT_SET_KEY = "ip:ban:permanent";

    /** INCR 后首次设置过期（原子），返回累计值。 */
    private static final DefaultRedisScript<Long> INCR_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;
    private final IpGuardProperties props;

    public IpGuardService(StringRedisTemplate redis, IpGuardProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** IP 是否被封禁（临时或永久）。 */
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        Boolean temp = redis.hasKey(BLACKLIST_KEY + ip);
        if (Boolean.TRUE.equals(temp)) {
            return true;
        }
        return Boolean.TRUE.equals(redis.opsForSet().isMember(PERMANENT_SET_KEY, ip));
    }

    /**
     * 记录一次限流命中，窗口内累计达阈值则自动封禁。
     *
     * @return true = 本次已触发自动封禁
     */
    public boolean recordRateLimitHit(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String counterKey = COUNTER_KEY + ip;
        Long count = redis.execute(INCR_EXPIRE, List.of(counterKey),
                String.valueOf(props.getAutoBanCounterWindow()));
        if (count != null && count >= props.getAutoBanThreshold()) {
            banTemp(ip, props.getAutoBanDuration(),
                    "自动封禁：" + props.getAutoBanCounterWindow() + "秒内被限流" + count + "次");
            redis.delete(counterKey);
            return true;
        }
        return false;
    }

    /** 临时封禁（带 TTL）。 */
    public void banTemp(String ip, int durationSeconds, String reason) {
        redis.opsForValue().set(BLACKLIST_KEY + ip, reason + " | " + new Date(),
                durationSeconds, TimeUnit.SECONDS);
        log.warn("[IP封禁] {} 封禁 {}秒，原因：{}", ip, durationSeconds, reason);
    }

    /** 永久封禁。 */
    public void banPermanent(String ip, String reason) {
        redis.opsForValue().set(BLACKLIST_KEY + ip, "永久封禁 | " + reason + " | " + new Date());
        redis.opsForSet().add(PERMANENT_SET_KEY, ip);
        log.warn("[IP永久封禁] {} 原因：{}", ip, reason);
    }

    /** 解封（清临时、永久与计数器）。 */
    public void unban(String ip) {
        redis.delete(BLACKLIST_KEY + ip);
        redis.opsForSet().remove(PERMANENT_SET_KEY, ip);
        redis.delete(COUNTER_KEY + ip);
        log.info("[IP解封] {}", ip);
    }

    /** 当前黑名单列表（含 TTL 与是否永久）。 */
    public List<Map<String, Object>> blacklist() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> keys = redis.keys(BLACKLIST_KEY + "*");
        if (keys != null) {
            for (String key : keys) {
                // ip:ban:* 通配会命中计数器(ip:ban:counter:*)与永久集合(ip:ban:permanent)，需排除
                if (key.startsWith(COUNTER_KEY) || key.equals(PERMANENT_SET_KEY)) {
                    continue;
                }
                String ip = key.substring(BLACKLIST_KEY.length());
                Map<String, Object> item = new HashMap<>();
                item.put("ip", ip);
                item.put("reason", redis.opsForValue().get(key));
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                item.put("ttl", ttl != null && ttl > 0 ? ttl : -1);
                item.put("permanent", Boolean.TRUE.equals(redis.opsForSet().isMember(PERMANENT_SET_KEY, ip)));
                result.add(item);
            }
        }
        return result;
    }

    /** 可疑 IP（限流命中较多但未封禁），用于监控预警。 */
    public List<Map<String, Object>> suspicious() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> keys = redis.keys(COUNTER_KEY + "*");
        if (keys != null) {
            for (String key : keys) {
                String ip = key.substring(COUNTER_KEY.length());
                String v = redis.opsForValue().get(key);
                long count = v != null ? Long.parseLong(v) : 0;
                if (count >= 5) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("ip", ip);
                    item.put("rateLimitCount", count);
                    item.put("windowTtl", redis.getExpire(key, TimeUnit.SECONDS));
                    result.add(item);
                }
            }
        }
        result.sort((a, b) -> Long.compare((long) b.get("rateLimitCount"), (long) a.get("rateLimitCount")));
        return result;
    }
}
