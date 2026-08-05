package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户转存记录 / 缓存。按 (pan_type, share_id) 唯一，命中即复用我方分享链。
 * 非永久记录到期由清理任务删除对应网盘文件。
 */
@Data
@TableName("transfer_record")
public class TransferRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String panType;
    /** 首转用的账号名（清理删除时用回同一个号）。 */
    private String accountName;
    private String shareId;
    private String shareUrl;
    private String sharePwd;

    /** 转存后我方分享链（返回给用户）。 */
    private String myShareUrl;
    private String mySharePwd;

    /** 落地夹 id（清理时删它；百度为路径）。 */
    private String folderId;

    /** 批量清理任务 id：清理入队时写入，delete 任务回报后按此回写状态。 */
    private Long deleteJobId;

    /** active/deleting/deleted/delete_failed。 */
    private String status;
    /** 1=永久保留，不参与清理。 */
    private Boolean isPermanent;

    private LocalDateTime transferTime;
    private LocalDateTime expireTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
