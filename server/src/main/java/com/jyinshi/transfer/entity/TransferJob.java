package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转存任务。进程内领取执行后回报。
 *
 * <p>job_type：check(检查) / create(监控创建) / update(监控更新) / transfer(用户转存) / delete(清理)。</p>
 */
@Data
@TableName("transfer_job")
public class TransferJob implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobType;
    private String panType;
    /** 指定执行账号（监控创建/更新/清理用；用户转存为空=转存号池选）。 */
    private String accountName;
    private String shareUrl;
    private String sharePwd;
    private Long mediaLinkId;
    /** 监控更新用的固定夹 id（创建成功后回填）。 */
    private String targetFolderId;
    /** 创建/转存的顶层落地目录名（监控资源 / 临时转存）。 */
    private String landingDir;

    private String status;
    private Integer priority;
    private Integer attempts;
    private Integer maxAttempts;
    /** 最早可领取时间（重试退避/延迟执行）。 */
    private LocalDateTime availableAt;
    private String workerId;
    private LocalDateTime leaseUntil;

    private String resultJson;
    private String resultShareUrl;
    private String resultFolderId;
    private String errorMsg;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
