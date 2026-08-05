package com.jyinshi.common.security;

import com.jyinshi.common.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 拒绝非白名单来源的 API 请求（浏览器 Origin/Referer）。
 * 注意：无法阻止 curl/脚本直连，仅能约束浏览器跨域与盗链式调用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FrontendOriginFilter extends OncePerRequestFilter {

    /** 无浏览器 Origin 的服务端回调/导入，跳过前台来源校验。 */
    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/health",
            "/api/drama/import",
            "/api/drama/update-cover",
            "/api/auto-resource/xunlei/callback",
            "/api/qr",
            "/api/feishu/webhook",
            "/api/feishu/health",
            "/api/notify/today"
    );

    private final CorsProperties corsProperties;

    public FrontendOriginFilter(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!corsProperties.isEnforceOrigin()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        // 运营上传图公开读（img 可能无 Origin；与 /drama-covers 同策略）
        if (!path.startsWith("/api/")
                || SKIP_PATHS.contains(path)
                || path.startsWith("/api/uploads/")) {
            chain.doFilter(request, response);
            return;
        }

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (isAllowed(request.getHeader("Origin"), request.getHeader("Referer"))) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write("{\"code\":403,\"message\":\"来源未授权\"}".getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAllowed(String origin, String referer) {
        for (String allowed : corsProperties.originList()) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            if (StringUtils.hasText(origin) && origin.equals(allowed)) {
                return true;
            }
            if (StringUtils.hasText(referer) && (referer.startsWith(allowed + "/") || referer.equals(allowed))) {
                return true;
            }
        }
        return false;
    }
}
