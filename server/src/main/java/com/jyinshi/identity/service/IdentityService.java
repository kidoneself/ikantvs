package com.jyinshi.identity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.common.security.JwtUtil;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.identity.dto.AuthResponse;
import com.jyinshi.identity.dto.LoginRequest;
import com.jyinshi.identity.dto.UserVO;
import com.jyinshi.identity.entity.User;
import com.jyinshi.identity.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 运营账号：登录、当前用户查询。 */
@Slf4j
@Service
public class IdentityService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public IdentityService(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /** 用户名密码登录仅供运营后台（录入员及以上）。 */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "账号已被封禁");
        }
        if (!UserRole.fromCode(user.getRole()).canAccessAdmin()) {
            throw new BizException("该账号无后台访问权限");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return buildAuth(user);
    }

    public UserVO currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return UserVO.from(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private AuthResponse buildAuth(User user) {
        String role = user.getRole() != null ? user.getRole() : UserRole.USER.getCode();
        String token = jwtUtil.issue(user.getId(), user.getUsername(), role);
        return AuthResponse.of(token, UserVO.from(user));
    }
}
