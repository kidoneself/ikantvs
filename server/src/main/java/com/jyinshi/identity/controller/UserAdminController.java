package com.jyinshi.identity.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.dto.AdminUserVO;
import com.jyinshi.identity.dto.UserRoleUpdateRequest;
import com.jyinshi.identity.dto.UserStatusUpdateRequest;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.identity.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（后台）。仅管理员可访问（requireRole ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public Result<PageResult<AdminUserVO>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String role,
                                                @RequestParam(required = false) Integer status) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(userAdminService.page(page, size, q, role, status));
    }

    @PutMapping("/{id}/role")
    public Result<AdminUserVO> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody UserRoleUpdateRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(userAdminService.updateRole(id, req.getRole(), AuthContext.requireUserId()));
    }

    @PutMapping("/{id}/status")
    public Result<AdminUserVO> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody UserStatusUpdateRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(userAdminService.updateStatus(id, req.getStatus(), AuthContext.requireUserId()));
    }

}
