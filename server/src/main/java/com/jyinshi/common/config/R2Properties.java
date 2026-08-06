package com.jyinshi.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cloudflare R2 对象存储（S3 兼容）。全站静态资源走同一 bucket + {@code publicBase}。
 *
 * <p>当前：content 海报/背景图镜像。后续：identity 头像、ops 附件等，共用本配置与 {@link com.jyinshi.common.storage.R2StorageService}。
 * 未启用或未配齐密钥时，业务层降级（如海报继续用上游 URL）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.storage.r2")
public class R2Properties {

    /** 是否启用 R2 镜像（上传海报到 R2 并改写 poster URL）。 */
    private boolean enabled = false;

    /** S3 API 端点，如 https://&lt;account_id&gt;.r2.cloudflarestorage.com */
    private String endpoint = "";

    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "jyinshi-posters";

    /**
     * 对外访问前缀（自定义域名或 R2 公开域名），无尾斜杠。
     * 例：https://img.example.com
     */
    private String publicBase = "";

    private int downloadTimeoutMs = 15000;

    /** 列表缩略图最大宽度（px）。 */
    private int thumbWidth = 256;

    /** 回填缩略图时每批条数。 */
    private int backfillBatchSize = 200;

    /** 回填缩略图并行线程数。 */
    private int backfillConcurrency = 12;

    public boolean isReady() {
        return enabled
                && StringUtils.hasText(endpoint)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(bucket)
                && StringUtils.hasText(publicBase);
    }

    /** 是否已是 R2 公网地址（避免重复上传）。 */
    public boolean isOwnUrl(String url) {
        return StringUtils.hasText(url)
                && StringUtils.hasText(publicBase)
                && url.startsWith(publicBase);
    }
}
