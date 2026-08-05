package com.jyinshi.transfer.dto;

import com.jyinshi.transfer.entity.TransferJob;
import lombok.Data;

/** 下发给 worker 的任务视图（只给执行所需字段，不暴露内部状态列）。 */
@Data
public class WorkerJobView {

    private Long id;
    private String jobType;
    private String panType;
    /** 指定执行账号名（sync/delete 用回首转的号；为空 worker 走账号池）。 */
    private String accountName;
    private String shareUrl;
    private String sharePwd;
    private String targetFolderId;
    /** 首转顶层落地目录名（追更=追更资源 / 用户转存=临时转存）。 */
    private String landingDir;
    /** 附带载荷：delete 批量清理时装 {"ids":[...]}（多个落地夹一次删）。 */
    private String resultJson;

    public static WorkerJobView of(TransferJob job) {
        WorkerJobView v = new WorkerJobView();
        v.id = job.getId();
        v.jobType = job.getJobType();
        v.panType = job.getPanType();
        v.accountName = job.getAccountName();
        v.shareUrl = job.getShareUrl();
        v.sharePwd = job.getSharePwd();
        v.targetFolderId = job.getTargetFolderId();
        v.landingDir = job.getLandingDir();
        v.resultJson = job.getResultJson();
        return v;
    }
}
