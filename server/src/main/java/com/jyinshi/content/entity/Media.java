package com.jyinshi.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 媒体信息（content 域）。一部影视/一个条目一行，元数据单独存。
 *
 * <p>身份锚定外部 id：优先 {@link #tmdbId}（采集主源），其次 {@link #doubanId}（补录），
 * 都没有则建「仅标题」条目（{@link #metaSource} = none）。网盘链接不在这里，单独挂在 media_link。
 */
@Data
@TableName("media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer tmdbId;
    private String doubanId;
    private Integer bangumiId;

    /** movie/tv/anime/variety */
    private String type;

    private String title;
    private String originalTitle;
    private Integer year;

    private String poster;
    private String posterThumb;
    private String backdrop;
    private BigDecimal rating;
    private String overview;

    /** 题材标签，逗号分隔 */
    private String genres;
    /** 国家/地区，逗号分隔 */
    private String country;
    /** 主演，逗号分隔 */
    private String actors;
    /** 导演，逗号分隔 */
    private String directors;

    private String releaseDate;
    private Integer episodeCount;

    /** 季数（不含 Specials），仅剧集类 */
    private Integer seasonCount;
    /** Ended / Returning Series 等 */
    private String seriesStatus;
    /** 是否仍在制作/播出 */
    private Boolean inProduction;
    private String lastAirDate;
    private Integer lastSeasonNumber;
    private Integer lastEpisodeNumber;

    /** 热度（榜单/排序用）= hot_seed + 近 N 天行为分，由热度回写 job 刷新 */
    private Integer hot;

    /** 基线热度：TMDB popularity 种子 / 后台手工设定，行为分在其上叠加 */
    private Integer hotSeed;

    /** 0普通 1精品 2专区（付费分层预留） */
    private Integer tier;

    /** 元数据来源：tmdb/douban/quark/manual/none */
    private String metaSource;

    /** 发布状态：0草稿 1已发布 2下架 */
    private Integer pubStatus;

    /** 1=前台隐藏：搜索/分类/首页/详情均不可见（后台仍可编辑） */
    private Integer searchHidden;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
