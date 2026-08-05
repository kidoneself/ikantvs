package com.jyinshi.common.security;

import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.identity.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前登录用户。Controller/Service 需要"当前用户是谁"时统一走这里。
 */
public final class AuthContext {

    private AuthContext() {
    }

    public static AuthPrincipal currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }

    public static Long currentUserIdOrNull() {
        AuthPrincipal p = currentOrNull();
        return p != null ? p.userId() : null;
    }

    public static UserRole currentRole() {
        AuthPrincipal p = currentOrNull();
        return p != null ? UserRole.fromCode(p.role()) : UserRole.USER;
    }

    public static Long requireUserId() {
        Long id = currentUserIdOrNull();
        if (id == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return id;
    }

    /** 运营后台：至少录入员。 */
    public static void requireStaff() {
        requireUserId();
        if (!currentRole().canAccessAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无后台访问权限");
        }
    }

    /** 至少指定角色（含更高角色）。 */
    public static void requireRole(UserRole minRole) {
        requireUserId();
        if (!currentRole().isAtLeast(minRole)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "权限不足");
        }
    }
}
