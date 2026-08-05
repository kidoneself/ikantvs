package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.MediaLinkAdminVO;
import com.jyinshi.content.service.MediaLinkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台链接搜索（content 域）。72 万级数据，必须带关键词或筛选条件，服务端分页。
 */
@RestController
@RequestMapping("/api/admin/media-links")
public class MediaLinkAdminController {

    private final MediaLinkService mediaLinkService;

    public MediaLinkAdminController(MediaLinkService mediaLinkService) {
        this.mediaLinkService = mediaLinkService;
    }

    /**
     * 搜索链接：关键词匹配 note / url / 片名；可叠加网盘类型、来源、失效状态、mediaId。
     */
    @GetMapping
    public Result<PageResult<MediaLinkAdminVO>> list(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String panType,
                                                     @RequestParam(required = false) String source,
                                                     @RequestParam(required = false) Integer invalid,
                                                     @RequestParam(required = false) Long mediaId) {
        AuthContext.requireStaff();
        return Result.success(mediaLinkService.pageAdmin(page, size, q, panType, source, invalid, mediaId));
    }
}
