package com.jyinshi.content.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.content.dto.RankingItemsRequest;
import com.jyinshi.content.dto.RankingSaveRequest;
import com.jyinshi.content.dto.RankingVO;
import com.jyinshi.content.service.RankingService;
import com.jyinshi.identity.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 榜单管理（后台）。至少审核员（reviewer）可运营。
 */
@RestController
@RequestMapping("/api/admin/rankings")
public class RankingAdminController {

    private final RankingService rankingService;

    public RankingAdminController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    public Result<List<RankingVO>> list() {
        AuthContext.requireRole(UserRole.REVIEWER);
        return Result.success(rankingService.adminList());
    }

    @GetMapping("/{id}")
    public Result<RankingVO> detail(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.REVIEWER);
        return Result.success(rankingService.adminGet(id));
    }

    @PostMapping
    public Result<RankingVO> save(@Valid @RequestBody RankingSaveRequest req) {
        AuthContext.requireRole(UserRole.REVIEWER);
        return Result.success(rankingService.save(req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthContext.requireRole(UserRole.REVIEWER);
        rankingService.delete(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/items")
    public Result<RankingVO> setItems(@PathVariable Long id,
                                      @Valid @RequestBody RankingItemsRequest req) {
        AuthContext.requireRole(UserRole.REVIEWER);
        return Result.success(rankingService.setItems(id, req.getMediaIds()));
    }
}
