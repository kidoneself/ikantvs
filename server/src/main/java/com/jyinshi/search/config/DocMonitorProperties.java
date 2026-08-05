package com.jyinshi.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jyinshi.doc-monitor")
public class DocMonitorProperties {

    /** 定时检查总开关（发版前可先 false） */
    private boolean enabled = true;

    /** 默认每小时 30 分 */
    private String cron = "0 30 * * * ?";
}
