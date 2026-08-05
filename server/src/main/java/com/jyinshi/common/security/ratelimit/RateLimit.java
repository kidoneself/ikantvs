package com.jyinshi.common.security.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解。由 {@code RateLimitInterceptor} 基于 Redis 固定窗口实现。
 * 加在 Controller 方法上即可生效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流 key 前缀（默认用方法名）。 */
    String key() default "";

    /** 时间窗口（秒）。 */
    int time() default 60;

    /** 窗口内最大请求次数。 */
    int count() default 30;

    /** 限流维度：按 IP / 按用户 / 全局。 */
    LimitType limitType() default LimitType.IP;

    /** 超限提示。 */
    String message() default "请求过于频繁，请稍后再试";

    enum LimitType {
        /** 按客户端 IP。 */
        IP,
        /** 按登录用户（未登录退化为 IP）。 */
        USER,
        /** 全局（所有请求共用一个计数）。 */
        GLOBAL
    }
}
