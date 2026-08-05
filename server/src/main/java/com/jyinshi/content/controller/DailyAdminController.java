package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.DailyItemVO;
import com.jyinshi.content.dto.DailyPatchRequest;
import com.jyinshi.content.dto.DailySaveRequest;
import com.jyinshi.content.service.DailyUpdateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日更新管理（后台 · content 域）。运营录入剧 + 上游链 + 监控账号，系统自动追更。
 */
@RestController
@RequestMapping("/api/admin/daily")
public class DailyAdminController {

    private final DailyUpdateService dailyService;

    public DailyAdminController(DailyUpdateService dailyService) {
        this.dailyService = dailyService;
    }

    @GetMapping
    public Result<PageResult<DailyItemVO>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer ended) {
        AuthContext.requireStaff();
        return Result.success(dailyService.adminPage(page, size, keyword, ended));
    }

    @GetMapping("/{id}")
    public Result<DailyItemVO> detail(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(dailyService.adminGet(id));
    }

    @PostMapping
    public Result<DailyItemVO> save(@RequestBody DailySaveRequest req) {
        AuthContext.requireStaff();
        return Result.success(dailyService.save(req));
    }

    @PutMapping("/{id}")
    public Result<DailyItemVO> patch(@PathVariable Long id, @RequestBody DailyPatchRequest req) {
        AuthContext.requireStaff();
        return Result.success(dailyService.patch(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthContext.requireStaff();
        dailyService.delete(id);
        return Result.success(null);
    }

    /** 立即检查该剧的追更（无视时段，手动补一轮 probe）。 */
    @PostMapping("/{id}/check")
    public Result<java.util.Map<String, Integer>> check(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(java.util.Map.of("enqueued", dailyService.triggerCheck(id)));
    }
}
