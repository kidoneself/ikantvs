package com.jyinshi.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 运营账号（identity 域）：仅供后台录入员/审核员/管理员登录。
 */
@Data
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码，绝不返回给前端。 */
    private String passwordHash;

    private String nickname;

    private String avatar;

    /** 状态：0 正常 1 封禁。 */
    private Integer status;

    /** user / contributor / reviewer / admin，见 {@link com.jyinshi.identity.enums.UserRole} */
    private String role;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
