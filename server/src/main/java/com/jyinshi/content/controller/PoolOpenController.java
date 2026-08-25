package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.content.dto.PoolIngestRequest;
import com.jyinshi.content.dto.PoolIngestResultVO;
import com.jyinshi.content.dto.PoolIngestRowVO;
import com.jyinshi.content.service.PoolIngestService;
import com.jyinshi.content.service.PoolTokenAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 脚本 / 助手：自营录入。开放 Token，跳过前台 Origin。 */
@RestController
@RequestMapping("/api/open/pool")
public class PoolOpenController {

    private final PoolIngestService poolIngestService;
    private final PoolTokenAuth poolTokenAuth;

    public PoolOpenController(PoolIngestService poolIngestService, PoolTokenAuth poolTokenAuth) {
        this.poolIngestService = poolIngestService;
        this.poolTokenAuth = poolTokenAuth;
    }

    @RateLimit(key = "open_pool_self", time = 60, count = 30, message = "入池太频繁了，请稍后再试")
    @PostMapping("/self")
    public Result<PoolIngestResultVO> ingestSelf(@RequestBody(required = false) PoolIngestRequest req,
                                                 HttpServletRequest request) {
        poolTokenAuth.requireOpenToken(request);
        return Result.success(poolIngestService.ingestSelf(req == null ? new PoolIngestRequest() : req));
    }

    @RateLimit(key = "open_pool_self_get", time = 60, count = 60, message = "查询太频繁了")
    @GetMapping("/self")
    public Result<PoolIngestRowVO> selfProgress(@RequestParam Long id, HttpServletRequest request) {
        poolTokenAuth.requireOpenToken(request);
        return Result.success(poolIngestService.selfProgress(id));
    }
}
