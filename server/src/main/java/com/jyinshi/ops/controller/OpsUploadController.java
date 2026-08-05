package com.jyinshi.ops.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.identity.enums.UserRole;
import com.jyinshi.ops.service.OpsUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台运营图片上传（本地磁盘）。
 */
@RestController
@RequestMapping("/api/admin")
public class OpsUploadController {

    private final OpsUploadService uploadService;

    public OpsUploadController(OpsUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * @param file  图片
     * @param scene 场景子目录，默认 notice（公告）
     */
    @PostMapping("/upload")
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scene", defaultValue = "notice") String scene) {
        AuthContext.requireRole(UserRole.ADMIN);
        return Result.success(uploadService.uploadImage(file, scene));
    }
}
