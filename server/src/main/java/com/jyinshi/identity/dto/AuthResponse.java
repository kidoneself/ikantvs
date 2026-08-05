package com.jyinshi.identity.dto;

import lombok.Data;

/**
 * 注册/登录成功后的返回：token + 用户信息。
 */
@Data
public class AuthResponse {

    private String token;
    private UserVO user;

    public static AuthResponse of(String token, UserVO user) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.user = user;
        return r;
    }
}
