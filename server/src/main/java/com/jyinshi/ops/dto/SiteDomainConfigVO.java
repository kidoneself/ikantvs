package com.jyinshi.ops.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SiteDomainConfigVO {
    private Long id;
    private String host;
    private boolean enabled;
    /** slug → 是否开启 */
    private Map<String, Boolean> pans = new LinkedHashMap<>();
    private String remark;
}
