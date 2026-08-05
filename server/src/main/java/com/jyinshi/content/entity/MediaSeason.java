package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 媒体季摘要（content 域）。一部剧多行，按 season_number 区分。 */
@Data
@TableName("media_season")
public class MediaSeason implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    private Integer seasonNumber;
    private Integer tmdbSeasonId;
    private String name;
    private Integer episodeCount;
    private String airDate;
    private String poster;
    private String overview;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
