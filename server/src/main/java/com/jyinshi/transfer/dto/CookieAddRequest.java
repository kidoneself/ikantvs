package com.jyinshi.transfer.dto;

import lombok.Data;

/** 后台粘贴 cookie 加号/换号（夸克/百度）。 */
@Data
public class CookieAddRequest {
    private String workerId;
    private String panType;
    /** 目标账号名：填已有号=覆盖其 cookie（换号/续期）；留空=新增号。 */
    private String accountName;
    /** 从浏览器复制的整段 cookie。 */
    private String cookie;
}
