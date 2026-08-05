package com.jyinshi.identity.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.identity.dto.AuthResponse;
import com.jyinshi.identity.dto.LoginRequest;
import com.jyinshi.identity.dto.UserVO;
import com.jyinshi.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。只做参数校验 + 调 service + 包 Result（遵守架构规则 2）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /** 用户名密码登录仅供运营后台（录入员及以上）。单 IP 60 秒最多 5 次，防暴力破解。 */
    @RateLimit(key = "auth_login", time = 60, count = 5, message = "登录尝试次数过多")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(identityService.login(req));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(identityService.currentUser(AuthContext.requireUserId()));
    }
}
