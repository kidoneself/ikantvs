package com.jyinshi.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("doc_monitor_history")
public class DocMonitorHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String source;
    private String taskName;
    private Integer oldLinksCount;
    private Integer newLinksCount;
    private Integer linksCountDiff;
    private Integer oldTextLength;
    private Integer newTextLength;
    private Integer textLengthDiff;
    private String contentHash;
    private String checkType;
    private Integer hasUpdate;
    private String changeDescription;
    private LocalDateTime createdAt;
}
