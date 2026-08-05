package com.jyinshi.common.security;

/**
 * 当前登录用户身份（JWT 解析结果）。
 */
public record AuthPrincipal(Long userId, String role) {
}
