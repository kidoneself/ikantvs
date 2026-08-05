package com.jyinshi.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.drama")
public class DramaProperties {

    /** 封面落盘目录（与老站共用宿主机目录时可一致）。 */
    private String coverPath = "/app/drama-covers";

    /** 对外 URL 前缀。 */
    private String coverUrlPrefix = "/drama-covers";

    /** TGForwarder 导入 token（Header: X-Import-Token）。 */
    private String importToken = "drama_import_2026";
}
