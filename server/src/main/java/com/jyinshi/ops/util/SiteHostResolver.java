package com.jyinshi.ops.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 从前台请求解析「站点域名」。
 * 优先 Origin / Referer（ikantvs 调 api.naspt.vip 时 Host 是 API 域），再回落 Host。
 */
public final class SiteHostResolver {

    private static final Set<String> IGNORE_PREFIX = Set.of("api.", "admin.");

    private SiteHostResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String host = fromUrl(request.getHeader("Origin"));
        if (host == null) {
            host = fromUrl(request.getHeader("Referer"));
        }
        if (host == null) {
            host = normalize(request.getServerName());
        }
        if (host == null) {
            host = normalize(request.getHeader("Host"));
        }
        if (host != null && isInfraHost(host)) {
            return null;
        }
        return host;
    }

    /** 小写、去端口、剥 www.；非法返回 null。 */
    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String h = raw.trim().toLowerCase(Locale.ROOT);
        int slash = h.indexOf('/');
        if (slash >= 0) {
            h = h.substring(0, slash);
        }
        int colon = h.indexOf(':');
        if (colon >= 0) {
            h = h.substring(0, colon);
        }
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        if (h.isEmpty() || "localhost".equals(h) || h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return null;
        }
        return h;
    }

    private static String fromUrl(String url) {
        if (!StringUtils.hasText(url) || "null".equalsIgnoreCase(url.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return normalize(uri.getHost());
        } catch (Exception e) {
            return normalize(url);
        }
    }

    private static boolean isInfraHost(String host) {
        for (String p : IGNORE_PREFIX) {
            if (host.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
