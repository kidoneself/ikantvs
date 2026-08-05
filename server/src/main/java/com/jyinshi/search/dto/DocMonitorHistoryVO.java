package com.jyinshi.search.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocMonitorHistoryVO {
    private Long id;
    private Long taskId;
    private String source;
    private String taskName;
    private Integer oldLinksCount;
    private Integer newLinksCount;
    private Integer linksCountDiff;
    private Integer oldTextLength;
    private Integer newTextLength;
    private Integer textLengthDiff;
    private String contentHash;
    private String checkType;
    private Integer hasUpdate;
    private String changeDescription;
    private LocalDateTime createdAt;
}
