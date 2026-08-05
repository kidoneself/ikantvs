package com.jyinshi.ops.service;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.ops.config.UploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 运营图片本地上传（公告 / 二维码等）。
 */
@Slf4j
@Service
public class OpsUploadService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final UploadProperties props;

    public OpsUploadService(UploadProperties props) {
        this.props = props;
    }

    /**
     * @param subdir 子目录，如 {@code notice}；仅允许字母数字下划线
     * @return 可嵌入 HTML 的公网 URL
     */
    public String uploadImage(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException("只能上传图片");
        }
        if (file.getSize() > props.getMaxBytes()) {
            throw new BizException("图片不能超过 " + (props.getMaxBytes() / 1024 / 1024) + "MB");
        }
        String dir = sanitizeSubdir(subdir);
        String ext = extensionOf(file.getOriginalFilename(), ct);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("不支持的图片格式，请用 jpg/png/gif/webp");
        }
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
        Path destDir = Path.of(props.getPath(), dir).toAbsolutePath().normalize();
        Path dest = destDir.resolve(name).normalize();
        if (!dest.startsWith(destDir)) {
            throw new BizException("非法路径");
        }
        try {
            Files.createDirectories(destDir);
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("[上传] 保存失败 {}", dest, e);
            throw new BizException("上传失败");
        }
        String path = trimSlash(props.getUrlPrefix()) + "/" + dir + "/" + name;
        String base = props.getPublicBase() == null ? "" : props.getPublicBase().trim();
        if (StringUtils.hasText(base)) {
            return trimSlash(base) + path;
        }
        return path;
    }

    private static String sanitizeSubdir(String subdir) {
        String d = (subdir == null || subdir.isBlank()) ? "notice" : subdir.trim().toLowerCase(Locale.ROOT);
        if (!d.matches("[a-z0-9_]{1,32}")) {
            throw new BizException("非法目录");
        }
        return d;
    }

    private static String extensionOf(String original, String contentType) {
        if (original != null && original.contains(".")) {
            String e = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (ALLOWED_EXT.contains(e)) {
                return "jpeg".equals(e) ? "jpg" : e;
            }
        }
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                case "image/bmp" -> "bmp";
                default -> "png";
            };
        }
        return "png";
    }

    private static String trimSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
