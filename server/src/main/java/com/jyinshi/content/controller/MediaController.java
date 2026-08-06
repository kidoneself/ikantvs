package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.service.MediaService;
import com.jyinshi.common.security.ratelimit.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体信息公开读接口（前台信息流 / 片库浏览）。只返回已发布内容。
 */
@Slf4j
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @RateLimit(key = "media_list", time = 60, count = 240, message = "请求太频繁了，请稍后再试")
    @GetMapping
    public Result<PageResult<MediaVO>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer yearFrom,
                                            @RequestParam(required = false) Integer yearTo,
                                            @RequestParam(required = false) String genre,
                                            @RequestParam(required = false) String country,
                                            @RequestParam(required = false) java.math.BigDecimal minRating) {
        return Result.success(mediaService.page(page, size, type, true, q, sort,
                yearFrom, yearTo, genre, country, minRating));
    }
}
