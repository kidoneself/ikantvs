package com.jyinshi.search.dto;

import com.jyinshi.search.docmonitor.ParseRules;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocMonitorSaveRequest {

    /** flowus / kdocs；可空，按 URL 推断 */
    private String source;

    private String taskName;

    @NotBlank(message = "分享链接不能为空")
    private String shareUrl;

    private String accessCode;
    private String category;
    private Integer status;
    private String remark;

    /** 解析规则；空则套用 source 默认模板 */
    private ParseRules parseRules;

    /** 选用内置模板名（flowus-default / kdocs-default），会覆盖 parseRules 中未填部分的默认 */
    private String template;
}
