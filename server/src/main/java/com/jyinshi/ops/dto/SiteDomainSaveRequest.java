package com.jyinshi.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SiteDomainSaveRequest {

    @NotBlank(message = "域名不能为空")
    private String host;

    @NotNull
    private Boolean enabled = true;

    /** slug → 是否开启；缺省 slug 视为 false */
    private Map<String, Boolean> pans = new LinkedHashMap<>();

    private String remark;
}
