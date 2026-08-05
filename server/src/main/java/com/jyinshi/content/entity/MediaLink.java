package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("media_link")
public class MediaLink implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    /** quark/baidu/aliyun/xunlei/uc/magnet */
    private String panType;
    private String url;
    /** 规范化分享 id，去重键（media_id + pan_type + share_id 唯一）。 */
    private String shareId;
    private String note;
    /** manual / pansou / crawl */
    private String source;
    /** pending / approved / rejected */
    private String status;
    private Long contributorId;
    private Integer invalid;
    /** 检测状态：ok/bad/locked/unsupported/uncertain，null=未检。 */
    private String checkState;
    private LocalDateTime checkedAt;
    private String checkSummary;
    /** 来源最近一次出现时间（新鲜度）。 */
    private LocalDateTime lastSeenAt;
    private Integer reportCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
