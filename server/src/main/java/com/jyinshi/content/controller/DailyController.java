package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.content.dto.DailyFeedItemVO;
import com.jyinshi.content.service.DailyUpdateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页「已更新」公开读接口（content 域）。
 *
 * <p>返回站内真实有更新的内容：运营策展看板打头，不足时用近期新增资源自动兜底补满。</p>
 */
@RestController
@RequestMapping("/api/daily")
public class DailyController {

    private final DailyUpdateService dailyService;

    public DailyController(DailyUpdateService dailyService) {
        this.dailyService = dailyService;
    }

    @RateLimit(key = "daily_feed", time = 60, count = 120, message = "请求太频繁了，请稍后再试")
    @GetMapping
    public Result<PageResult<DailyFeedItemVO>> feed(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "12") long size) {
        return Result.success(dailyService.publicFeed(page, size));
    }
}
