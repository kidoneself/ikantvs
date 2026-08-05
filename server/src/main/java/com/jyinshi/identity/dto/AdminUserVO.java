package com.jyinshi.identity.dto;

import com.jyinshi.identity.entity.User;
import com.jyinshi.identity.enums.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台运营账号列表视图。 */
@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static AdminUserVO from(User u) {
        AdminUserVO vo = new AdminUserVO();
        vo.id = u.getId();
        vo.username = u.getUsername();
        vo.nickname = u.getNickname();
        vo.avatar = u.getAvatar();
        vo.role = u.getRole() != null ? u.getRole() : UserRole.CONTRIBUTOR.getCode();
        vo.status = u.getStatus() != null ? u.getStatus() : 0;
        vo.lastLoginAt = u.getLastLoginAt();
        vo.createdAt = u.getCreatedAt();
        return vo;
    }
}
