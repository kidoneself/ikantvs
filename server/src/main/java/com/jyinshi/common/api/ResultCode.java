package com.jyinshi.common.api;

import lombok.Getter;

/**
 * 通用业务状态码。域内的专用错误码请在各自域里定义，避免这里膨胀。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    BIZ_ERROR(500, "操作失败"),
    SYSTEM_ERROR(999, "系统异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
