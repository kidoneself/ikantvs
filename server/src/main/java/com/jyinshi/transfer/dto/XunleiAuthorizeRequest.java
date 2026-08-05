package com.jyinshi.transfer.dto;

import lombok.Data;

/** 后台发起迅雷授权加号/换号。 */
@Data
public class XunleiAuthorizeRequest {
    private String workerId;
    /** 目标账号名：填已有号=重新授权覆盖 refresh_token；留空=新增号。 */
    private String accountName;
}
