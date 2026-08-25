package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
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

/**
 * 后台入池：同行录入（JWT 或开放 Token）+ 自营录入（仅 JWT）。
 */
@RestController
@RequestMapping("/api/admin/pool")
public class PoolAdminController {

    private final PoolIngestService poolIngestService;
    private final PoolTokenAuth poolTokenAuth;

    public PoolAdminController(PoolIngestService poolIngestService, PoolTokenAuth poolTokenAuth) {
        this.poolIngestService = poolIngestService;
        this.poolTokenAuth = poolTokenAuth;
    }

    @RateLimit(key = "pool_ingest", time = 60, count = 30, message = "入池太频繁了，请稍后再试")
    @PostMapping("/ingest")
    public Result<PoolIngestResultVO> ingestPeer(@RequestBody(required = false) PoolIngestRequest req,
                                                 HttpServletRequest request) {
        poolTokenAuth.requireStaffOrOpenToken(request);
        return Result.success(poolIngestService.ingestPeer(req == null ? new PoolIngestRequest() : req));
    }

    @RateLimit(key = "pool_self", time = 60, count = 30, message = "入池太频繁了，请稍后再试")
    @PostMapping("/self")
    public Result<PoolIngestResultVO> ingestSelf(@RequestBody(required = false) PoolIngestRequest req) {
        AuthContext.requireStaff();
        return Result.success(poolIngestService.ingestSelf(req == null ? new PoolIngestRequest() : req));
    }

    @RateLimit(key = "pool_self_get", time = 60, count = 60, message = "查询太频繁了")
    @GetMapping("/self")
    public Result<PoolIngestRowVO> selfProgress(@RequestParam Long id) {
        AuthContext.requireStaff();
        return Result.success(poolIngestService.selfProgress(id));
    }
}
