package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.dto.SysConfigItemVO;
import com.jyinshi.ops.dto.SysConfigUpdateRequest;
import com.jyinshi.ops.service.SysConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置（后台）。仅管理员可读写。
 */
@RestController
@RequestMapping("/api/admin/config")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping
    public Result<List<SysConfigItemVO>> list() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(sysConfigService.items());
    }

    @PutMapping
    public Result<List<SysConfigItemVO>> update(@Valid @RequestBody SysConfigUpdateRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(sysConfigService.updateMany(req.getValues()));
    }
}
