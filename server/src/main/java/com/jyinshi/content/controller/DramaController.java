package com.jyinshi.content.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.security.ratelimit.RateLimit;
import com.jyinshi.content.dto.DramaVO;
import com.jyinshi.content.service.DramaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 短剧公开接口 + TGForwarder 导入（对齐老站 /api/drama/*）。
 */
@Slf4j
@RestController
@RequestMapping("/api/drama")
public class DramaController {

    private final DramaService dramaService;

    public DramaController(DramaService dramaService) {
        this.dramaService = dramaService;
    }

    /** 浏览页：对齐 media_list，避免无限滚动 + 刷新立刻撞墙。 */
    @RateLimit(key = "drama_list", time = 60, count = 240, message = "请求太频繁了")
    @GetMapping("/list")
    public Result<PageResult<DramaVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dramaService.listForUser(page, size));
    }

    @RateLimit(key = "drama_search", time = 60, count = 60, message = "搜索太频繁了")
    @GetMapping("/search")
    public Result<PageResult<DramaVO>> search(
            @RequestParam String kw,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dramaService.searchForUser(kw, page, size));
    }

    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(dramaService.countActive());
    }

    /**
     * TGForwarder：POST multipart，Header X-Import-Token。
     */
    @PostMapping("/import")
    public Result<String> importDrama(
            @RequestHeader(value = "X-Import-Token", required = false) String token,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String quarkLink,
            @RequestParam(required = false) String baiduLink,
            @RequestParam(required = false) Integer episodeCount,
            @RequestParam(required = false) String sourceChannel,
            @RequestParam(required = false) String messageTime,
            @RequestParam(required = false) MultipartFile cover) {
        // 先鉴权再校验参数：探测/漏参不会先被 Spring 打成「未捕获异常」
        if (!dramaService.validateToken(token)) {
            log.warn("[短剧导入] token无效");
            return Result.fail(ResultCode.FORBIDDEN.getCode(), "无权访问");
        }
        if (!StringUtils.hasText(title)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "标题不能为空");
        }
        if (!StringUtils.hasText(quarkLink)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "夸克链接不能为空");
        }
        try {
            dramaService.importDrama(title.trim(), quarkLink.trim(),
                    StringUtils.hasText(baiduLink) ? baiduLink.trim() : null,
                    episodeCount, sourceChannel, messageTime, cover);
            return Result.success("导入成功");
        } catch (Exception e) {
            log.error("[短剧导入] 失败: {}", e.getMessage(), e);
            return Result.fail(ResultCode.BIZ_ERROR.getCode(), "导入失败: " + e.getMessage());
        }
    }

    @PostMapping("/update-cover")
    public Result<String> updateCover(
            @RequestHeader(value = "X-Import-Token", required = false) String token,
            @RequestParam(required = false) String quarkLink,
            @RequestParam(required = false) MultipartFile cover) {
        if (!dramaService.validateToken(token)) {
            return Result.fail(ResultCode.FORBIDDEN.getCode(), "无权访问");
        }
        if (!StringUtils.hasText(quarkLink)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "夸克链接不能为空");
        }
        if (cover == null || cover.isEmpty()) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "封面文件不能为空");
        }
        try {
            int updated = dramaService.updateCoverByQuarkLink(quarkLink.trim(), cover);
            if (updated > 0) {
                return Result.success("更新成功，影响" + updated + "条");
            }
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "未找到匹配的记录");
        } catch (Exception e) {
            log.error("[短剧封面] 更新失败: {}", e.getMessage());
            return Result.fail(ResultCode.BIZ_ERROR.getCode(), "更新失败: " + e.getMessage());
        }
    }
}
