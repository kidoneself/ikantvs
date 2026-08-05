package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.dto.PanOptionVO;
import com.jyinshi.ops.dto.SiteDomainConfigVO;
import com.jyinshi.ops.dto.SiteDomainSaveRequest;
import com.jyinshi.ops.service.SiteDomainPanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台：按域名配置前台网盘开关。 */
@RestController
@RequestMapping("/api/admin/site-domains")
public class SiteDomainAdminController {

    private final SiteDomainPanService siteDomainPanService;

    public SiteDomainAdminController(SiteDomainPanService siteDomainPanService) {
        this.siteDomainPanService = siteDomainPanService;
    }

    @GetMapping("/pan-options")
    public Result<List<PanOptionVO>> panOptions() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(siteDomainPanService.panOptions());
    }

    @GetMapping
    public Result<List<SiteDomainConfigVO>> list() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(siteDomainPanService.listAll());
    }

    @PostMapping
    public Result<SiteDomainConfigVO> create(@Valid @RequestBody SiteDomainSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(siteDomainPanService.create(req));
    }

    @PutMapping("/{id}")
    public Result<SiteDomainConfigVO> update(@PathVariable Long id,
                                             @Valid @RequestBody SiteDomainSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(siteDomainPanService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.ADMIN);
        siteDomainPanService.delete(id);
        return Result.success();
    }
}
