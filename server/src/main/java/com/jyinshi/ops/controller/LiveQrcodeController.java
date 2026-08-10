package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.common.security.ip.ClientIpResolver;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.dto.LiveQrcodeAdminVO;
import com.jyinshi.ops.dto.LiveQrcodeUpdateRequest;
import com.jyinshi.ops.service.LiveQrcodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活码公开页 + 后台管理（对齐老站路径，便于 example.com/qr 无缝切换）。
 */
@RestController
@RequestMapping("/api")
public class LiveQrcodeController {

    private final LiveQrcodeService liveQrcodeService;

    public LiveQrcodeController(LiveQrcodeService liveQrcodeService) {
        this.liveQrcodeService = liveQrcodeService;
    }

    /** 公开：活码页数据 + 记一次访问。 */
    @GetMapping("/qr")
    public Result<Map<String, Object>> qrPage(
            @RequestParam(value = "from", required = false) String from,
            HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        String ua = request.getHeader("User-Agent");
        return Result.success(liveQrcodeService.openQrPage(from, ip, ua));
    }

    @GetMapping("/admin/live-qrcode/config")
    public Result<LiveQrcodeAdminVO> adminConfig() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(liveQrcodeService.adminConfig());
    }

    @PutMapping("/admin/live-qrcode/config")
    public Result<LiveQrcodeAdminVO> update(@Valid @RequestBody LiveQrcodeUpdateRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        liveQrcodeService.updateConfig(req);
        return Result.success(liveQrcodeService.adminConfig());
    }

    @GetMapping("/admin/live-qrcode/stats")
    public Result<Map<String, Object>> stats() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(liveQrcodeService.stats());
    }
}
