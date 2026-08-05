package com.jyinshi.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("doc_monitor_task")
public class DocMonitorTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** flowus / kdocs / … */
    private String source;
    private String taskName;
    private String shareUrl;
    private String accessCode;
    private String category;
    /** 0 禁用 1 启用 */
    private Integer status;
    /** JSON 字符串：ParseRules */
    private String parseRules;
    private String contentHash;
    private Integer linksCount;
    private Integer textLength;
    private Integer dramaCount;
    /** 最近一次成功解析的 DramaEntry 列表 JSON */
    private String entriesJson;
    private LocalDateTime lastCheckTime;
    private LocalDateTime lastUpdateTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean enabled() {
        return status != null && status == 1;
    }
}
