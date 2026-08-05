package com.jyinshi.ops.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.dto.SensitiveCheckResult;
import com.jyinshi.ops.dto.SensitiveTestRequest;
import com.jyinshi.ops.dto.SensitiveWordBatchRequest;
import com.jyinshi.ops.dto.SensitiveWordSaveRequest;
import com.jyinshi.ops.dto.SensitiveWordVO;
import com.jyinshi.ops.service.SensitiveWordService;
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
 * 敏感词管理（后台）。仅管理员可读写。
 */
@RestController
@RequestMapping("/api/admin/sensitive")
public class SensitiveWordController {

    private final SensitiveWordService service;

    public SensitiveWordController(SensitiveWordService service) {
        this.service = service;
    }

    /** 可选项（分类/动作），给前端下拉用。 */
    @GetMapping("/meta")
    public Result<Map<String, List<String>>> meta() {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(Map.of(
                "categories", SensitiveWordService.CATEGORIES,
                "actions", List.of(
                        SensitiveWordService.ACTION_BLOCK,
                        SensitiveWordService.ACTION_REVIEW,
                        SensitiveWordService.ACTION_REPLACE,
                        SensitiveWordService.ACTION_WARN)));
    }

    @GetMapping
    public Result<PageResult<SensitiveWordVO>> list(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) String keyword) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.page(page, size, category, action, keyword));
    }

    @PostMapping
    public Result<SensitiveWordVO> create(@Valid @RequestBody SensitiveWordSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.create(req));
    }

    @PutMapping("/{id}")
    public Result<SensitiveWordVO> update(@PathVariable Long id,
                                          @Valid @RequestBody SensitiveWordSaveRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.ADMIN);
        service.delete(id);
        return Result.success();
    }

    /** 批量导入：一行一词，整批共用 category/action。返回新增条数。 */
    @PostMapping("/batch")
    public Result<Integer> importBatch(@Valid @RequestBody SensitiveWordBatchRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.importBatch(req));
    }

    /** 在线测试一段文本。 */
    @PostMapping("/test")
    public Result<SensitiveCheckResult> test(@Valid @RequestBody SensitiveTestRequest req) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(service.check(req.getText()));
    }
}
