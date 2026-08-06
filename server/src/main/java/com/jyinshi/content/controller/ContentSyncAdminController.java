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
 * 内容同步（后台手动触发）。开源版仅重建自动榜单。
 */
@RestController
@RequestMapping("/api/admin/content-sync")
public class ContentSyncAdminController {

    private final ContentSyncService sync;

    public ContentSyncAdminController(ContentSyncService sync) {
        this.sync = sync;
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
