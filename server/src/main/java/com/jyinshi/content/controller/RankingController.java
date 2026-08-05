package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.content.dto.RankingVO;
import com.jyinshi.content.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 榜单公开读接口（前台）。只返回已上架榜单 + 已发布条目。 */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @RateLimit(key = "ranking_list", time = 60, count = 20, message = "请求太频繁了")
    @GetMapping
    public Result<List<RankingVO>> list() {
        return Result.success(rankingService.listPublic());
    }
}
