package com.jyinshi.identity.dto;

import com.jyinshi.identity.entity.User;
import lombok.Data;

/**
 * 用户对外视图。实体不直接暴露，敏感字段（密码）不出现在这里。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;

    /** 角色：user / contributor / reviewer / admin */
    private String role;

    public static UserVO from(User u) {
        UserVO vo = new UserVO();
        vo.id = u.getId();
        vo.username = u.getUsername();
        vo.nickname = u.getNickname();
        vo.avatar = u.getAvatar();
        vo.role = u.getRole() != null ? u.getRole() : "user";
        return vo;
    }
}
