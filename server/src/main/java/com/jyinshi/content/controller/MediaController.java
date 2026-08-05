package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.content.dto.DiscoverImportRequest;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.dto.TmdbDiscoverItemVO;
import com.jyinshi.content.ingest.IngestService;
import com.jyinshi.content.service.MediaDiscoveryService;
import com.jyinshi.content.service.MediaService;
import com.jyinshi.common.security.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 媒体信息公开读接口（前台信息流 / 片库浏览 / TMDB 发现）。只返回已发布内容。
 * 网盘链走 SSE 搜索沉淀 + 转存，不再提供详情页链接接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;
    private final MediaDiscoveryService discoveryService;
    private final IngestService ingestService;
    private final Executor ingestExecutor;

    public MediaController(MediaService mediaService,
                           MediaDiscoveryService discoveryService,
                           IngestService ingestService,
                           @Qualifier("ingestExecutor") Executor ingestExecutor) {
        this.mediaService = mediaService;
        this.discoveryService = discoveryService;
        this.ingestService = ingestService;
        this.ingestExecutor = ingestExecutor;
    }

    // 列表接口是前台读大头：首页并行拉 2 个、无限滚动每次 1 个，
    // 单个用户正常浏览一分钟就可能几十个，故给足额度。
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

    /**
     * 本地 0 命中时的 TMDB 发现候选（最多 8 条，不入库）。
     * 过短关键词、敏感词、TMDB 未配置时返回空列表。
     */
    @RateLimit(key = "media_discover", time = 60, count = 20, message = "请求太频繁了，请稍后再试")
    @GetMapping("/discover")
    public Result<List<TmdbDiscoverItemVO>> discover(@RequestParam String q,
                                                     @RequestParam(required = false) String type) {
        return Result.success(discoveryService.discover(q, type));
    }

    /**
     * 用户点击发现卡片：按 tmdbId 入库并发布，随后异步触发该片资源采集。
     * 已在库且可见则直接返回本地条目（幂等）。
     */
    @RateLimit(key = "discover_import", time = 60, count = 10, message = "操作太频繁，请稍后再试")
    @PostMapping("/discover/import")
    public Result<MediaVO> discoverImport(@Valid @RequestBody DiscoverImportRequest req) {
        MediaVO vo = discoveryService.importOnDemand(req.getTmdbId(), req.getType());
        if (vo.getId() != null) {
            try {
                ingestExecutor.execute(() -> ingestService.ingestForMedia(vo.getId()));
            } catch (Exception ignored) {
                // 线程池满则后台保鲜会补
            }
        }
        return Result.success(vo);
    }
}
