package com.jyinshi.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 活码访问日志。ops 域。 */
@Data
@TableName("live_qrcode_log")
public class LiveQrcodeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String source;

    private String ip;

    private String userAgent;

    private LocalDateTime createdAt;
}
