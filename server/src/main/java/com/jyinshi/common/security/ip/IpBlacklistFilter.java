package com.jyinshi.common.security.ip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.api.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * IP 黑名单过滤器：被封禁 IP 直接 403。最高优先级，早于 Spring Security 与限流拦截器执行，
 * 让被封 IP 以最低成本被挡在门外。白名单与内网/回环 IP 永远放行。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpBlacklistFilter extends OncePerRequestFilter {

    private final IpGuardService ipGuardService;
    private final IpGuardProperties props;
    private final ObjectMapper objectMapper;

    public IpBlacklistFilter(IpGuardService ipGuardService,
                             IpGuardProperties props,
                             ObjectMapper objectMapper) {
        this.ipGuardService = ipGuardService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolve(request);
        if (props.whitelistSet().contains(ip) || ClientIpResolver.isPrivateOrLoopback(ip)) {
            chain.doFilter(request, response);
            return;
        }

        if (ipGuardService.isBlocked(ip)) {
            log.warn("[IP封禁] 拦截 {} {} from {}", request.getMethod(), request.getRequestURI(), ip);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Result<Void> body = Result.fail(ResultCode.FORBIDDEN.getCode(), "您的 IP 已被临时封禁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        chain.doFilter(request, response);
    }
}
