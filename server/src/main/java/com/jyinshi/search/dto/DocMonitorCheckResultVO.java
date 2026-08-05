package com.jyinshi.search.dto;

import lombok.Data;

@Data
public class DocMonitorCheckResultVO {
    private Long taskId;
    private String taskName;
    private String source;
    private boolean success;
    private boolean unchanged;
    private boolean updated;
    private String message;
    private Integer linksCount;
    private Integer dramaCount;
}
