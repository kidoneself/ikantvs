package com.jyinshi.transfer.dto;

import lombok.Data;

/**
 * 后台手动回填迅雷授权码：回调域名不通时，运营在浏览器授权后把地址栏那串
 * （含 code 的完整 URL 或纯 code）贴回来，主站据此换 refresh_token 落号。
 */
@Data
public class XunleiCodeRequest {
    /** 发起授权时拿到的会话 id（= state）。 */
    private String sessionId;
    /** 授权后地址栏的完整 URL（含 ?code=...）或纯 code。 */
    private String code;
}
