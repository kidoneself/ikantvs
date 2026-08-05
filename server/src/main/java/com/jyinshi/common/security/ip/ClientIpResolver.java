package com.jyinshi.common.security.ip;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 统一解析客户端真实 IP。全站取 IP 都走这里，避免各处逻辑不一致。
 *
 * <p>优先级：Cloudflare 回源头 {@code CF-Connecting-IP} > {@code X-Forwarded-For}(取第一个)
 * > {@code X-Real-IP} > {@code remoteAddr}。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("CF-Connecting-IP");
        if (valid(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("X-Forwarded-For");
        if (valid(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (valid(ip)) {
            return ip.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    private static boolean valid(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }

    /** 私有/回环 IP（RFC1918 + IPv6 回环/ULA），此类 IP 一定是同机/内网调用，永不封禁。 */
    public static boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("fc") || ip.startsWith("fd")) {
            return true;
        }
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            int dot = ip.indexOf('.', 4);
            if (dot > 4) {
                try {
                    int second = Integer.parseInt(ip.substring(4, dot));
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    // fallthrough
                }
            }
        }
        return false;
    }
}
