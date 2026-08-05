package com.jyinshi.transfer.dto;

import lombok.Data;

/** 入队一个转存任务（程序内部调用）。 */
@Data
public class JobEnqueueRequest {

    /** check / create / update / transfer / delete。 */
    private String jobType;
    private String panType;
    /** 指定执行账号（监控创建/更新/清理用；用户转存留空由池选）。 */
    private String accountName;
    private String shareUrl;
    private String sharePwd;
    private Long mediaLinkId;
    /** 监控更新必填的固定夹；创建/转存可空。 */
    private String targetFolderId;
    /**
     * 创建/转存的顶层落地目录名（监控资源 / 临时转存）。
     * 在账号根下确保该夹存在，把内容落进去，实现监控与临时物理隔离。
     */
    private String landingDir;
    private Integer priority;
}
