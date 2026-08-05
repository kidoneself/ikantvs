package com.jyinshi.analytics.dto;

import lombok.Data;

/** 媒体维度聚合：mediaId + 次数（+ 在 service 里补标题/海报）。 */
@Data
public class MediaStat {
    private Long mediaId;
    private Long cnt;
    private String title;
    private String poster;
    private String type;
}
