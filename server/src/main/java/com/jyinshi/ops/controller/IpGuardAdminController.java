package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.common.security.ip.IpGuardService;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.dto.IpBanRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * IP 黑名单管理（后台）。查看/手动封禁/解封。仅管理员可写，录入员以上可读。
 */
@RestController
@RequestMapping("/api/admin/ip-guard")
public class IpGuardAdminController {

    private final IpGuardService ipGuardService;

    public IpGuardAdminController(IpGuardService ipGuardService) {
        this.ipGuardService = ipGuardService;
    }

    /** 当前黑名单列表。 */
    @GetMapping("/blacklist")
    public Result<List<Map<String, Object>>> blacklist() {
        AuthContext.requireStaff();
        return Result.success(ipGuardService.blacklist());
    }

    /** 可疑 IP（限流命中较多但未封禁），用于预警。 */
    @GetMapping("/suspicious")
    public Result<List<Map<String, Object>>> suspicious() {
        AuthContext.requireStaff();
        return Result.success(ipGuardService.suspicious());
    }

    /** 手动封禁 IP。 */
    @PostMapping("/ban")
    public Result<Void> ban(@Valid @RequestBody IpBanRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        if (req.isPermanent()) {
            ipGuardService.banPermanent(req.getIp(), req.getReason());
        } else {
            ipGuardService.banTemp(req.getIp(), req.getDurationSeconds(), req.getReason());
        }
        return Result.success();
    }

    /** 解封 IP。 */
    @PostMapping("/unban")
    public Result<Void> unban(@RequestParam String ip) {
        AuthContext.requireRole(UserRole.ADMIN);
        ipGuardService.unban(ip);
        return Result.success();
    }
}
