package com.jyinshi.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.identity.dto.AdminUserVO;
import com.jyinshi.identity.entity.User;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.identity.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 运营账号管理（后台）。仅管理员可用，权限在 controller 层校验。 */
@Service
public class UserAdminService {

    private final UserMapper userMapper;

    public UserAdminService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 仅列出可登录后台的运营账号（录入员及以上）。 */
    public PageResult<AdminUserVO> page(long page, long size, String q, String role, Integer status) {
        LambdaQueryWrapper<User> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(q)) {
            String kw = q.trim();
            w.and(x -> x.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        if (StringUtils.hasText(role)) {
            UserRole target = UserRole.fromCode(role.trim());
            if (!target.canAccessAdmin()) {
                throw new BizException("仅支持运营角色筛选");
            }
            w.eq(User::getRole, target.getCode());
        } else {
            w.in(User::getRole,
                    UserRole.CONTRIBUTOR.getCode(),
                    UserRole.REVIEWER.getCode(),
                    UserRole.ADMIN.getCode());
        }
        if (status != null) {
            w.eq(User::getStatus, status);
        }
        w.orderByDesc(User::getId);
        IPage<User> p = userMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size,
                p.getRecords().stream().map(AdminUserVO::from).toList());
    }

    @Transactional
    public AdminUserVO updateRole(Long id, String role, Long operatorId) {
        User user = requireStaff(id);
        if (id.equals(operatorId)) {
            throw new BizException("不能修改自己的角色");
        }
        UserRole target = UserRole.fromCode(role);
        if (!target.getCode().equalsIgnoreCase(role)) {
            throw new BizException("非法角色：" + role);
        }
        if (!target.canAccessAdmin()) {
            throw new BizException("不能设为普通用户，仅支持运营角色");
        }
        user.setRole(target.getCode());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return AdminUserVO.from(user);
    }

    @Transactional
    public AdminUserVO updateStatus(Long id, Integer status, Long operatorId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("非法状态");
        }
        User user = requireStaff(id);
        if (id.equals(operatorId) && status == 1) {
            throw new BizException("不能封禁自己");
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return AdminUserVO.from(user);
    }

    private User requireStaff(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "账号不存在");
        }
        if (!UserRole.fromCode(user.getRole()).canAccessAdmin()) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "账号不存在");
        }
        return user;
    }
}
