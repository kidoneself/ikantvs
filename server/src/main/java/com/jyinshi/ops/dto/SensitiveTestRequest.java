package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 在线测试：给一段文本看命中情况。 */
@Data
public class SensitiveTestRequest {

    @NotBlank(message = "请输入要测试的文本")
    private String text;
}
