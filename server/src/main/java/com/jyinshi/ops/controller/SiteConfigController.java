package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.ops.dto.SitePublicConfigVO;
import com.jyinshi.ops.service.LiveQrcodeService;
import com.jyinshi.ops.service.SysConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台可读站点配置（迅雷 SDK、公告、加群等）。
 */
@RestController
@RequestMapping("/api/site")
public class SiteConfigController {

    private final SysConfigService sysConfigService;
    private final LiveQrcodeService liveQrcodeService;

    public SiteConfigController(SysConfigService sysConfigService,
                                LiveQrcodeService liveQrcodeService) {
        this.sysConfigService = sysConfigService;
        this.liveQrcodeService = liveQrcodeService;
    }

    @GetMapping("/config")
    public Result<SitePublicConfigVO> config() {
        SitePublicConfigVO vo = sysConfigService.publicSiteConfig();
        liveQrcodeService.fillContact(vo);
        return Result.success(vo);
    }
}
