package com.jyinshi.analytics.dto;

import lombok.Data;

/** 单片近 N 天行为热度分（热度回写 job 用）。 */
@Data
public class MediaHeat {

    private Long mediaId;

    /** 加权衰减后的行为分（view=1，link_click=3，按天衰减）。 */
    private Integer score;
}
