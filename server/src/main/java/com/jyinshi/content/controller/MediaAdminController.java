package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.ManualMediaRequest;
import com.jyinshi.content.dto.MediaDetailVO;
import com.jyinshi.content.dto.MediaUpdateRequest;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 影视库后台：列表 / 手工录入 / 编辑（开源无 TMDB/豆瓣采集）。
 */
@RestController
@RequestMapping("/api/admin/media")
public class MediaAdminController {

    private final MediaService mediaService;

    public MediaAdminController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** 手工录入（可填海报 URL；建议先走 /api/admin/upload?scene=poster）。 */
    @PostMapping("/manual")
    public Result<MediaVO> manual(@Valid @RequestBody ManualMediaRequest req) {
        AuthContext.requireStaff();
        return Result.success(mediaService.createManual(req));
    }

    @GetMapping("/{id}")
    public Result<MediaDetailVO> detail(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(mediaService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<MediaVO> update(@PathVariable Long id, @RequestBody MediaUpdateRequest req) {
        AuthContext.requireStaff();
        return Result.success(mediaService.updateAdmin(id, req));
    }

    @PostMapping("/backfill-season-posters")
    public Result<Map<String, Integer>> backfillSeasonPosters(@RequestParam(defaultValue = "100") int limit) {
        AuthContext.requireStaff();
        int done = mediaService.backfillSeasonPosters(limit);
        return Result.success(Map.of("processed", done));
    }

    @PostMapping("/backfill-thumbs")
    public Result<Map<String, Integer>> backfillThumbs(@RequestParam(defaultValue = "100") int limit) {
        AuthContext.requireStaff();
        int done = mediaService.backfillPosterThumbs(limit);
        return Result.success(Map.of("processed", done));
    }

    @GetMapping("/storage-status")
    public Result<Map<String, Boolean>> storageStatus() {
        AuthContext.requireStaff();
        return Result.success(Map.of("ready", mediaService.isPosterStorageReady()));
    }

    @GetMapping
    public Result<PageResult<MediaVO>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean hidden) {
        AuthContext.requireStaff();
        return Result.success(mediaService.pageAdmin(page, size, type, q, hidden));
    }
}
