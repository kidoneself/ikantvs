package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 榜单（content 域）。 */
@Data
@TableName("ranking")
public class Ranking implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String slug;
    private String description;

    /** 榜单间展示顺序，大在前。 */
    private Integer sort;

    /** 1 上架 0 下架。 */
    private Integer enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
