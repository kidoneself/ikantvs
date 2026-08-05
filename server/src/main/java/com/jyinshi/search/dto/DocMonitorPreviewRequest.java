package com.jyinshi.search.dto;

import com.jyinshi.search.docmonitor.ParseRules;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocMonitorPreviewRequest {

    private String source;

    @NotBlank(message = "分享链接不能为空")
    private String shareUrl;

    private String accessCode;
    private ParseRules parseRules;
    private String template;
}
