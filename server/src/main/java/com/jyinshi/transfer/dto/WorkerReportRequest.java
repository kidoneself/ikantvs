package com.jyinshi.transfer.dto;

import lombok.Data;

/** worker 回报任务结果。 */
@Data
public class WorkerReportRequest {

    private Long jobId;
    private String workerId;
    private boolean success;

    /** probe 的时间戳/文件数快照，或 save 的结果快照（JSON 文本）。 */
    private String resultJson;
    /** 转存后我方分享链（save 成功时）。 */
    private String resultShareUrl;
    /** 转存落地夹 id（save 成功时，供后续追更复用）。 */
    private String resultFolderId;
    /** 失败原因。 */
    private String errorMsg;
}
