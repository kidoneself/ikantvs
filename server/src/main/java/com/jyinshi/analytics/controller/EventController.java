package com.jyinshi.analytics.controller;

import com.jyinshi.analytics.dto.EventRequest;
import com.jyinshi.analytics.service.AnalyticsService;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台行为埋点上报 + 公开榜单读（公开、匿名、即发即忘）。 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final AnalyticsService analyticsService;

    public EventController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @RateLimit(key = "event_track", time = 60, count = 120, message = "上报太频繁了")
    @PostMapping
    public Result<Void> track(@RequestBody EventRequest req,
                              @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        analyticsService.track(req, visitorId);
        return Result.success(null);
    }

    /** 「大家在搜」：近 N 天真实搜索热词（已过滤敏感/过短、按归一化去重、Redis 缓存）。 */
    @RateLimit(key = "hot_searches", time = 60, count = 60, message = "请求太频繁了")
    @GetMapping("/hot-searches")
    public Result<List<String>> hotSearches(@RequestParam(defaultValue = "14") int days,
                                            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(analyticsService.hotKeywords(days, limit));
    }
}
