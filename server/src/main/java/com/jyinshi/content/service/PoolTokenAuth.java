package com.jyinshi.content.service;

import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.ops.service.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 入池开放 Token：与运营 JWT 共用配置 {@code pool.self.token}。未配置则脚本接口关闭。
 */
@Component
public class PoolTokenAuth {

    public static final String HEADER = "X-Api-Token";

    private final SysConfigService sysConfig;

    public PoolTokenAuth(SysConfigService sysConfig) {
        this.sysConfig = sysConfig;
    }

    public String configuredToken() {
        return sysConfig.getOrDefault(SysConfigService.POOL_SELF_TOKEN, "").trim();
    }

    public boolean matchesOpenToken(HttpServletRequest request) {
        String expected = configuredToken();
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        String got = extractToken(request);
        return expected.equals(got);
    }

    /** 脚本接口：仅开放 Token。未配置或错误 → 401。 */
    public void requireOpenToken(HttpServletRequest request) {
        String expected = configuredToken();
        if (!StringUtils.hasText(expected)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "开放 Token 未配置");
        }
        if (!expected.equals(extractToken(request))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /** 同行录入：运营 JWT 或开放 Token。 */
    public void requireStaffOrOpenToken(HttpServletRequest request) {
        if (AuthContext.currentOrNull() != null) {
            AuthContext.requireStaff();
            return;
        }
        requireOpenToken(request);
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
