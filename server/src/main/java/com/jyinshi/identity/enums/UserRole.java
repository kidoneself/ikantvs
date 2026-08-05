package com.jyinshi.identity.enums;

import lombok.Getter;

/**
 * 后台工作人员角色（挂在 user.role）。
 *
 * <p>{@link #USER} 为历史遗留，不再新建；运营后台仅管理录入员及以上。
 */
@Getter
public enum UserRole {

    USER("user", 0),
    CONTRIBUTOR("contributor", 1),
    REVIEWER("reviewer", 2),
    ADMIN("admin", 3);

    private final String code;
    private final int level;

    UserRole(String code, int level) {
        this.code = code;
        this.level = level;
    }

    public static UserRole fromCode(String code) {
        if (code == null || code.isBlank()) {
            return USER;
        }
        for (UserRole r : values()) {
            if (r.code.equalsIgnoreCase(code)) {
                return r;
            }
        }
        return USER;
    }

    /** 能否登录运营后台（至少录入员）。 */
    public boolean canAccessAdmin() {
        return level >= CONTRIBUTOR.level;
    }

    public boolean isAtLeast(UserRole required) {
        return this.level >= required.level;
    }
}
