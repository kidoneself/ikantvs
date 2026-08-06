package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.client.FetchedMetadata;
import com.jyinshi.content.client.TmdbClient;
import com.jyinshi.content.dto.ManualMediaRequest;
import com.jyinshi.content.dto.MediaImportRequest;
import com.jyinshi.content.dto.MediaUpdateRequest;
import com.jyinshi.content.dto.MediaDetailVO;
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

import java.util.List;
import java.util.Map;

/**
 * 内容采集/补录（后台）。当前要求登录；角色细分（录入员/审核员/管理员）随阶段 2.5 补。
 */
@RestController
@RequestMapping("/api/admin/media")
public class MediaAdminController {

    private final MediaService mediaService;
    private final TmdbClient tmdbClient;

    public MediaAdminController(MediaService mediaService, TmdbClient tmdbClient) {
        this.mediaService = mediaService;
        this.tmdbClient = tmdbClient;
    }

    /** 补录：喂 tmdbId 或 TMDB 链接抓元数据入库。 */
    @PostMapping("/import")
    public Result<MediaVO> importByExternalId(@RequestBody MediaImportRequest req) {
        AuthContext.requireStaff();
        return Result.success(mediaService.importByExternalId(req));
    }

    /** 仅录入：人工建条目（可填海报）。 */
    @PostMapping("/manual")
    public Result<MediaVO> manual(@Valid @RequestBody ManualMediaRequest req) {
        AuthContext.requireStaff();
        return Result.success(mediaService.createManual(req));
    }

    /** 后台详情（含季列表，与前台 detail 结构一致）。 */
    @GetMapping("/{id}")
    public Result<MediaDetailVO> detail(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(mediaService.getDetail(id));
    }

    /** 后台人工编辑。 */
    @PutMapping("/{id}")
    public Result<MediaVO> update(@PathVariable Long id, @RequestBody MediaUpdateRequest req) {
        AuthContext.requireStaff();
        return Result.success(mediaService.updateAdmin(id, req));
    }

    /** 刷新：按已存外部 id 重新抓取。 */
    @PostMapping("/{id}/refresh")
    public Result<MediaVO> refresh(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(mediaService.refresh(id));
    }

    /** TMDB 搜索（补录时找 tmdbId 用）。 */
    @GetMapping("/tmdb-search")
    public Result<List<FetchedMetadata>> tmdbSearch(@RequestParam String q) {
        AuthContext.requireStaff();
        return Result.success(tmdbClient.searchMulti(q));
    }

    /** 批量补季海报镜像（仍为外链的 media_season）。 */
    @PostMapping("/backfill-season-posters")
    public Result<Map<String, Integer>> backfillSeasonPosters(@RequestParam(defaultValue = "100") int limit) {
        AuthContext.requireStaff();
        int done = mediaService.backfillSeasonPosters(limit);
        return Result.success(Map.of("processed", done));
    }

    /** 批量补列表缩略图（不影响已有 poster）。 */
    @PostMapping("/backfill-thumbs")
    public Result<Map<String, Integer>> backfillThumbs(@RequestParam(defaultValue = "100") int limit) {
        AuthContext.requireStaff();
        int done = mediaService.backfillPosterThumbs(limit);
        return Result.success(Map.of("processed", done));
    }

    /** R2 海报存储是否已就绪。 */
    @GetMapping("/storage-status")
    public Result<Map<String, Boolean>> storageStatus() {
        AuthContext.requireStaff();
        return Result.success(Map.of("ready", mediaService.isPosterStorageReady()));
    }

    /** 后台列表（含草稿）。hidden=true 时仅前台隐藏条目。 */
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
