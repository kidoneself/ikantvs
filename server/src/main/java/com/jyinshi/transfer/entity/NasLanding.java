package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 一部剧一个迅雷落地夹：百度灌盘写入，迅雷上游监控复用。 */
@Data
@TableName("nas_landing")
public class NasLanding implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    private Long sourceMediaLinkId;
    private String xunleiFolderId;
    private String xunleiShareUrl;
    /**
     * 建夹时百度相对路径文件名快照（JSON 字符串数组）。
     * NAS 只灌「不在基线、也不在迅雷夹」的新文件；老集靠迅雷上游补进同一夹。
     */
    private String baselineJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
