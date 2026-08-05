package com.jyinshi.analytics.controller;

import com.jyinshi.analytics.dto.AnalyticsOverviewVO;
import com.jyinshi.analytics.service.AnalyticsAdminService;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据洞察（后台）。审核员及以上可看。 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsAdminController {

    private final AnalyticsAdminService adminService;

    public AnalyticsAdminController(AnalyticsAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Result<AnalyticsOverviewVO> overview(@RequestParam(defaultValue = "7") int days) {
        AuthContext.requireRole(UserRole.REVIEWER);
        return Result.success(adminService.overview(days));
    }
}
