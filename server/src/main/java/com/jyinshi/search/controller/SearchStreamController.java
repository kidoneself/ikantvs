package com.jyinshi.search.controller;

import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.search.service.StreamSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式搜索（SSE）。对齐老站 {@code GET /api/search/stream?kw=}。
 * 一次推全盘结果，前端按 Tab 本地筛选；不传 cloudTypes。
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
public class SearchStreamController {

    private final StreamSearchService streamSearchService;
    private final SysConfigService sysConfigService;

    public SearchStreamController(StreamSearchService streamSearchService,
                                  SysConfigService sysConfigService) {
        this.streamSearchService = streamSearchService;
        this.sysConfigService = sysConfigService;
    }

    @RateLimit(key = "search_stream", time = 60, count = 30, message = "搜索太频繁了，请稍后再试")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String kw,
                             @RequestParam(required = false) String cloudTypes) {
        log.info("流式搜索 kw={} cloudTypes={}", kw, cloudTypes);
        return streamSearchService.stream(kw, cloudTypes, sysConfigService.enabledPanTypes());
    }
}
