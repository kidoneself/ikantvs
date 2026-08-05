package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 批量导入敏感词：一行一个词（也用于从老库迁入）。
 * 整批共用一个 category / action。
 */
@Data
public class SensitiveWordBatchRequest {

    /** 多行文本，一行一个词；空行/重复自动忽略。 */
    @NotBlank(message = "请粘贴要导入的词")
    private String text;

    /** 不填默认 legacy（迁入/批量的默认归类）。 */
    private String category;

    /** 不填默认 warn（先观察不拦截）。 */
    private String action;
}
