package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.ContentSyncStatus;
import com.jyinshi.content.service.ContentSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容自动同步（后台手动触发）。定时任务之外，运营可随时手动拉新 / 刷新连载 / 重建榜单。
 *
 * <p>三个触发接口均为**异步**：立即返回当前进度，实际执行在后台单线程串行进行，
 * 前端轮询 {@link #status()} 查看进度与结果。有任务在跑时再次触发会被拒绝（忙）。
 */
@RestController
@RequestMapping("/api/admin/content-sync")
public class ContentSyncAdminController {

    private final ContentSyncService sync;

    public ContentSyncAdminController(ContentSyncService sync) {
        this.sync = sync;
    }

    /** 立即拉新片 / 热播（异步）。 */
    @PostMapping("/discover")
    public Result<ContentSyncStatus> discover() {
        AuthContext.requireStaff();
        return Result.success(sync.submit("discover"));
    }

    /** 立即刷新连载剧集数（异步）。 */
    @PostMapping("/refresh-airing")
    public Result<ContentSyncStatus> refreshAiring() {
        AuthContext.requireStaff();
        return Result.success(sync.submit("refresh"));
    }

    /** 立即重建自动榜单（异步）。 */
    @PostMapping("/rebuild-rankings")
    public Result<ContentSyncStatus> rebuildRankings() {
        AuthContext.requireStaff();
        return Result.success(sync.submit("rankings"));
    }

    /** 查询当前同步进度（前端轮询）。 */
    @GetMapping("/status")
    public Result<ContentSyncStatus> status() {
        AuthContext.requireStaff();
        return Result.success(sync.getStatus());
    }
}
