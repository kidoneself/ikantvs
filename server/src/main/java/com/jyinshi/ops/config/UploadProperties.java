package com.jyinshi.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 运营本地上传（公告图等）——落广州磁盘，经 {@code /api/uploads/**} 直出，不走 R2。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.upload")
public class UploadProperties {

    /** 容器内落盘目录。 */
    private String path = "/app/uploads";

    /**
     * 对外路径前缀（挂在 /api 下，复用现有反代，无需改 Caddy）。
     * 例：{@code /api/uploads/notice/xxx.png}
     */
    private String urlPrefix = "/api/uploads";

    /**
     * 写入公告 HTML 时用的公网前缀（多域名前台也能加载）。
     * 空则只返回相对路径。
     */
    private String publicBase = "";

    /** 单文件上限（字节），默认 5MB。 */
    private long maxBytes = 5 * 1024 * 1024L;
}
