package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 短剧资源（content 域）。 */
@Data
@TableName("drama")
public class Drama implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private Integer episodeCount;

    private String quarkLink;

    private String baiduLink;

    /** 如 /drama-covers/{hash}.jpg */
    private String coverImage;

    private String sourceChannel;

    private LocalDateTime messageTime;

    /** 0 下架 / 1 上架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
