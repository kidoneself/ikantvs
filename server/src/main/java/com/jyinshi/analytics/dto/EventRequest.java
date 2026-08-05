package com.jyinshi.analytics.dto;

import lombok.Data;

/** 前台上报的行为事件（公开接口）。 */
@Data
public class EventRequest {

    /** search / link_click / card_click */
    private String type;

    private Long mediaId;
    private String keyword;
    private String tag;
    private Integer num;
}
