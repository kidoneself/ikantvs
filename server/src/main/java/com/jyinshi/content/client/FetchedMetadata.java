package com.jyinshi.content.client;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部源（TMDB）抓回来的元数据统一载体。
 * 各 client 解析自家 JSON 后填充本对象，service 再映射到 Media 实体。
 */
@Data
public class FetchedMetadata {

    /** 来源：tmdb */
    private String source;

    private Integer tmdbId;

    /** movie/tv/anime/variety */
    private String type;

    private String title;
    private String originalTitle;
    private Integer year;

    private String poster;
    private String backdrop;
    private BigDecimal rating;
    private String overview;

    /** 逗号分隔 */
    private String genres;
    private String country;
    private String actors;
    private String directors;

    private String releaseDate;
    private Integer episodeCount;

    /** 季数（不含 Specials），仅剧集类。 */
    private Integer seasonCount;
    /** Ended / Returning Series 等。 */
    private String seriesStatus;
    private Boolean inProduction;
    private String lastAirDate;
    private Integer lastSeasonNumber;
    private Integer lastEpisodeNumber;

    /** TMDB seasons[] 解析结果；电影为空。 */
    private List<FetchedSeason> seasons = new ArrayList<>();

    /** 人气值（TMDB popularity 取整），用作 hot 热度排序。 */
    private Integer popularity;
}
