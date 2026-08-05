package com.jyinshi.common.storage;

import com.jyinshi.common.config.R2Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Cloudflare R2 上传网关（S3 兼容，跨域复用）。
 *
 * <p>职责：put object + 拼公网 URL。key 前缀见 {@link StoragePaths}，各域 service 自行决定何时上传、从哪下载。
 * 未启用 R2 时 upload 返回 null，业务层可降级为远程 URL。
 */
@Slf4j
@Service
public class R2StorageService {

    private final R2Properties props;
    private volatile S3Client client;

    public R2StorageService(R2Properties props) {
        this.props = props;
    }

    /**
     * 上传对象，返回公网 URL；未就绪时返回 null。
     */
    public String upload(String key, byte[] data, String contentType) {
        if (!props.isReady() || data == null || data.length == 0) {
            return null;
        }
        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=2592000")
                    .build();
            client().putObject(req, RequestBody.fromBytes(data));
            return publicUrl(key);
        } catch (Exception e) {
            log.warn("R2 上传失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public String publicUrl(String key) {
        String base = props.getPublicBase();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + key;
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .endpointOverride(URI.create(props.getEndpoint()))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                            .region(Region.of("auto"))
                            .forcePathStyle(true)
                            .build();
                }
            }
        }
        return client;
    }
}
