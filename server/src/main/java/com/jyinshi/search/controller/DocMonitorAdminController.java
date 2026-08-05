package com.jyinshi.search.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.search.dto.DocMonitorCheckResultVO;
import com.jyinshi.search.dto.DocMonitorHistoryVO;
import com.jyinshi.search.dto.DocMonitorPreviewRequest;
import com.jyinshi.search.dto.DocMonitorPreviewVO;
import com.jyinshi.search.dto.DocMonitorSaveRequest;
import com.jyinshi.search.dto.DocMonitorTaskVO;
import com.jyinshi.search.service.DocMonitorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 文档资源发现（FlowUs / kdocs）后台管理。
 * 任务级 parse_rules 可配置，改格式无需发版；新平台靠 DocFetcher 插拔。
 */
@RestController
@RequestMapping("/api/admin/doc-monitor")
public class DocMonitorAdminController {

    private final DocMonitorService service;

    public DocMonitorAdminController(DocMonitorService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.meta());
    }

    @GetMapping("/tasks")
    public Result<PageResult<DocMonitorTaskVO>> list(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String source) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.page(page, size, keyword, source));
    }

    @GetMapping("/tasks/{id}")
    public Result<DocMonitorTaskVO> get(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.get(id));
    }

    @PostMapping("/tasks")
    public Result<DocMonitorTaskVO> create(@Valid @RequestBody DocMonitorSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.create(req));
    }

    @PutMapping("/tasks/{id}")
    public Result<DocMonitorTaskVO> update(@PathVariable Long id,
                                           @Valid @RequestBody DocMonitorSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.update(id, req));
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.ADMIN);
        service.delete(id);
        return Result.success();
    }

    @PutMapping("/tasks/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        AuthContext.requireRole(UserRole.ADMIN);
        Boolean enabled = body == null ? null : body.get("enabled");
        if (enabled == null) {
            return Result.fail(400, "缺少 enabled");
        }
        service.updateStatus(id, enabled);
        return Result.success();
    }

    /** 试解析：不落库，用于配规则时预览剧目挂链是否正确。 */
    @PostMapping("/preview")
    public Result<DocMonitorPreviewVO> preview(@Valid @RequestBody DocMonitorPreviewRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.preview(req));
    }

    @PostMapping("/tasks/{id}/check")
    public Result<DocMonitorCheckResultVO> check(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.checkTask(id, "manual"));
    }

    @PostMapping("/check-all")
    public Result<List<DocMonitorCheckResultVO>> checkAll() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.checkAll("manual"));
    }

    @GetMapping("/tasks/{id}/history")
    public Result<List<DocMonitorHistoryVO>> history(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "30") int limit) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.history(id, limit));
    }
}
