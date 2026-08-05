package com.jyinshi.transfer.dto;

import lombok.Data;

/** 启用/更新一条链接的追更。 */
@Data
public class MonitorEnableRequest {

    private Long mediaLinkId;
    private String panType;
    private String shareUrl;
    private String sharePwd;
}
