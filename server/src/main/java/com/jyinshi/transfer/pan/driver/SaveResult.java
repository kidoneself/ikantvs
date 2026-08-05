package com.jyinshi.transfer.pan.driver;

import lombok.Data;

/** 转存结果：成功则带回我们自己账号下的新分享链（推广链）。 */
@Data
public class SaveResult {

    private boolean success;

    /** 转存后我们账号生成的新分享链（要展示/返回给用户的推广链）。 */
    private String myShareUrl;

    /** 新分享的提取码（夸克通常无码，百度有）。 */
    private String myPassword;

    /** 转存落地的文件夹 fid（追更补转时作为目标目录复用）。 */
    private String savedFolderId;

    /** 用哪个账号转的（回传主站记账/排查用）。 */
    private String accountName;

    /** 首转落地夹里"最新"的文件名（追更进度展示，如最新集数；主站据此回填集数）。 */
    private String latestFileName;

    private String errorCode;
    private String errorMessage;

    public static SaveResult ok(String myShareUrl, String myPassword, String savedFolderId, String accountName) {
        SaveResult r = new SaveResult();
        r.success = true;
        r.myShareUrl = myShareUrl;
        r.myPassword = myPassword;
        r.savedFolderId = savedFolderId;
        r.accountName = accountName;
        return r;
    }

    public static SaveResult error(String code, String message) {
        SaveResult r = new SaveResult();
        r.success = false;
        r.errorCode = code;
        r.errorMessage = message;
        return r;
    }
}
