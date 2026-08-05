package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.AdminDashboardVO;
import com.jyinshi.content.service.MediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台仪表盘（content 域）。 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final MediaService mediaService;

    public AdminDashboardController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/stats")
    public Result<AdminDashboardVO> stats() {
        AuthContext.requireStaff();
        return Result.success(mediaService.dashboardStats());
    }
}
