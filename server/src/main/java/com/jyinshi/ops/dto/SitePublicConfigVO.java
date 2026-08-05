package com.jyinshi.ops.dto;

import lombok.Data;

import java.util.List;

/** 前台公开站点配置（不含敏感后台项）。 */
@Data
public class SitePublicConfigVO {

    /** 是否启用迅雷 JS-SDK（磁力等资源「高速下载」）。 */
    private boolean xunleiSdkEnabled;
    /** 迅雷推广达人数字账号，对应 SDK extra.custom（结算用）。 */
    private String xunleiPartnerCustom;
    /** 允许前台展示的网盘类型 label（按页签顺序）；不在此列表的网盘不显示。 */
    private List<String> enabledPans;

    /** 是否展示网站公告（顶栏 + 弹窗）。 */
    private boolean noticeEnabled;
    private String noticeTitle;
    /** 公告 HTML 正文。 */
    private String noticeContent;
    /** 关闭后是否记住不再弹（改内容后重新弹）。 */
    private boolean noticeShowOnce;

    /** 站内加群/联系（与活码共用配置；禁用时前台不展示入口）。 */
    private boolean contactEnabled;
    private String contactTitle;
    private String contactTip;
    /** 微信群二维码图 URL。 */
    private String contactGroupQrcode;
    /** 公众号二维码图 URL。 */
    private String contactMpQrcode;
}
