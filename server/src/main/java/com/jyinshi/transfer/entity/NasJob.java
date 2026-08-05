package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** NAS 灌盘任务：next 入队，千云 claim 执行。 */
@Data
@TableName("nas_job")
public class NasJob implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long baiduAccountId;
    private String xunleiFolderId;
    private String filesJson;
    private String title;
    private Long mediaLinkId;

    private String status;
    private Integer priority;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime availableAt;
    private String workerId;
    private LocalDateTime leaseUntil;

    private Integer totalFiles;
    private Integer doneFiles;
    private Integer failedFiles;
    private String resultJson;
    private String errorMsg;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
