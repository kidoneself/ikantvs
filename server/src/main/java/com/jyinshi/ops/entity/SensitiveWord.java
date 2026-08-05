package com.jyinshi.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 敏感词。ops 域。 */
@Data
@TableName("sensitive_word")
public class SensitiveWord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归一化后的词：去首尾空白、转小写。 */
    private String word;

    /** politics/porn/ad/violence/legacy/other。 */
    private String category;

    /** block / review / warn / replace。 */
    private String action;

    private Integer enabled;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
