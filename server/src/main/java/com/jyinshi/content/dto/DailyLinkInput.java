package com.jyinshi.content.dto;

import lombok.Data;

/** 每日更新录入/编辑时的一条上游分享链（一部剧可填多套：夸克/百度/备用）。 */
@Data
public class DailyLinkInput {

    /** quark/baidu/xunlei。 */
    private String panType;
    /** 上游分享链（监控转存的源）。 */
    private String shareUrl;
    private String sharePwd;
}
