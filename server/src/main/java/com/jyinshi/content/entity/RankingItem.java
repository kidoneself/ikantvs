package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 榜单条目：某榜单里的一个影视条目及其名次。 */
@Data
@TableName("ranking_item")
public class RankingItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long rankingId;
    private Long mediaId;

    /** 榜内名次，小在前。 */
    private Integer rankNo;

    private LocalDateTime createdAt;
}
