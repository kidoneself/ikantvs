package com.jyinshi.transfer.dto;

import lombok.Data;

/**
 * 转存结果（用户侧）。异步：首次转存 status=transferring，前端拿 jobId 轮询 result。
 */
@Data
public class TransferResultVO {

    /** transferring / done / failed。 */
    private String status;

    /** 轮询用任务 id（transferring 时有值）。 */
    private Long jobId;

    /** 我方分享链（done 时有值）。 */
    private String shareUrl;
    /** 我方提取码（done 时可能有值）。 */
    private String password;

    /** 失败原因 / 提示。 */
    private String message;

    public static TransferResultVO transferring(Long jobId) {
        TransferResultVO v = new TransferResultVO();
        v.status = "transferring";
        v.jobId = jobId;
        return v;
    }

    public static TransferResultVO done(String shareUrl, String password) {
        TransferResultVO v = new TransferResultVO();
        v.status = "done";
        v.shareUrl = shareUrl;
        v.password = password;
        return v;
    }

    public static TransferResultVO failed(String message) {
        TransferResultVO v = new TransferResultVO();
        v.status = "failed";
        v.message = message;
        return v;
    }
}
