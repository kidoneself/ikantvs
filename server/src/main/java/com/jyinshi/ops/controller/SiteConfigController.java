package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.ops.dto.SitePublicConfigVO;
import com.jyinshi.ops.service.LiveQrcodeService;
import com.jyinshi.ops.service.SiteDomainPanService;
import com.jyinshi.ops.service.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SiteDomainPanService siteDomainPanService;

    public SiteConfigController(SysConfigService sysConfigService,
                                LiveQrcodeService liveQrcodeService,
                                SiteDomainPanService siteDomainPanService) {
        this.sysConfigService = sysConfigService;
        this.liveQrcodeService = liveQrcodeService;
        this.siteDomainPanService = siteDomainPanService;
    }

    @GetMapping("/config")
    public Result<SitePublicConfigVO> config(HttpServletRequest request) {
        SitePublicConfigVO vo = sysConfigService.publicSiteConfig();
        // 按访问域名覆盖网盘开关（未配置域名则保留全局 pan.display）
        vo.setEnabledPans(siteDomainPanService.enabledPanLabels(request));
        liveQrcodeService.fillContact(vo);
        return Result.success(vo);
    }
}
