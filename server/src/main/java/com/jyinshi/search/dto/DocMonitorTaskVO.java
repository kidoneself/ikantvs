package com.jyinshi.search.dto;

import com.jyinshi.search.docmonitor.ParseRules;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocMonitorTaskVO {
    private Long id;
    private String source;
    private String taskName;
    private String shareUrl;
    private String accessCode;
    private String category;
    private Integer status;
    private ParseRules parseRules;
    private String contentHash;
    private Integer linksCount;
    private Integer textLength;
    private Integer dramaCount;
    private LocalDateTime lastCheckTime;
    private LocalDateTime lastUpdateTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
