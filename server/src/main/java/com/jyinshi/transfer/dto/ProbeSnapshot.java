package com.jyinshi.transfer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 反序列化 worker probe 回传的 ShareInfo（只取追更/检测需要的字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProbeSnapshot {

    private boolean ok;
    /** ok/bad/locked/unsupported/uncertain。 */
    private String checkState;
    private String title;
    private Long updatedAt;
    private Integer fileCount;
    private Long size;
    private Long expiredAt;
    private String message;
}
