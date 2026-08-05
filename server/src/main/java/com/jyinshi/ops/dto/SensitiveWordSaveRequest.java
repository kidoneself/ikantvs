package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新增/编辑单条敏感词。 */
@Data
public class SensitiveWordSaveRequest {

    @NotBlank(message = "词不能为空")
    private String word;

    /** 不填默认 other。 */
    private String category;

    /** 不填默认 block（手动添加的是运营明确要拦的）。 */
    private String action;

    /** 不填默认启用。 */
    private Boolean enabled;

    private String remark;
}
