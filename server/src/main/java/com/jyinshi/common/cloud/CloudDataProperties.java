package com.jyinshi.common.cloud;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 云端数据源：开源壳对接运营方片库/取链的「一份链接」。
 *
 * <p>{@code enabled=false} 时走本地库（演示种子或自建采集）；
 * 打开后通过 {@code base-url} + {@code api-key} 对接上游。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.cloud")
public class CloudDataProperties {

    /** 是否启用云端数据源。 */
    private boolean enabled = false;

    /** 上游 API 根地址，如 https://api.example.com/api */
    private String baseUrl = "";

    /** 上游下发的站点密钥。 */
    private String apiKey = "";
}
