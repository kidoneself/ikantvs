package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/** 批量更新系统配置：键 → 新值。 */
@Data
public class SysConfigUpdateRequest {

    @NotEmpty(message = "没有要更新的配置")
    private Map<String, String> values;
}
