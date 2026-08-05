package com.jyinshi.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 {@code Authorization: Bearer <token>} 解析用户身份，写入 SecurityContext。
 * 校验失败不抛错，交给后续的鉴权链按"未登录"处理。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    /** 滑动续期：有新 token 时通过该响应头带回，前端自动替换。 */
    public static final String REFRESH_HEADER = "X-Refresh-Token";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            Long userId = jwtUtil.parseUserId(token);
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = jwtUtil.parseRole(token);
                AuthPrincipal principal = new AuthPrincipal(userId, role);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, AuthorityUtils.NO_AUTHORITIES);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                String renewed = jwtUtil.renewIfNeeded(token);
                if (renewed != null) {
                    response.setHeader(REFRESH_HEADER, renewed);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
