package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 失效分享黑名单：按 {@code pan_type + share_id} 记住确定失效的分享。
 * 采集入库前查它过滤，转存失败回写它，越用越准。
 */
@Data
@TableName("invalid_share")
public class InvalidShare implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String panType;
    private String shareId;
    private String errorCode;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
