package com.jyinshi.ops.dto;

import com.jyinshi.ops.entity.SensitiveWord;
import lombok.Data;

import java.time.LocalDateTime;

/** 敏感词对外 VO。 */
@Data
public class SensitiveWordVO {

    private Long id;
    private String word;
    private String category;
    private String action;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SensitiveWordVO from(SensitiveWord w) {
        SensitiveWordVO vo = new SensitiveWordVO();
        vo.id = w.getId();
        vo.word = w.getWord();
        vo.category = w.getCategory();
        vo.action = w.getAction();
        vo.enabled = w.getEnabled() != null && w.getEnabled() == 1;
        vo.remark = w.getRemark();
        vo.createdAt = w.getCreatedAt();
        vo.updatedAt = w.getUpdatedAt();
        return vo;
    }
}
