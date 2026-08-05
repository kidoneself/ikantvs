package com.jyinshi.content.dto;

import com.jyinshi.content.entity.Media;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 媒体信息对外 VO。实体不直接暴露给前端（架构规则 2）。
 */
@Data
public class MediaVO {

    private Long id;
    private Integer tmdbId;
    private String doubanId;
    private String type;
    private String title;
    private String originalTitle;
    private Integer year;
    private String poster;
    private String posterThumb;
    private String backdrop;
    private BigDecimal rating;
    private String overview;
    private List<String> genres;
    private List<String> country;
    private List<String> actors;
    private List<String> directors;
    private String releaseDate;
    private Integer episodeCount;
    private Integer seasonCount;
    private String seriesStatus;
    private Boolean inProduction;
    private String lastAirDate;
    private Integer lastSeasonNumber;
    private Integer lastEpisodeNumber;
    private Integer hot;
    private Integer tier;
    private String metaSource;
    private Integer pubStatus;
    private Integer searchHidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MediaVO from(Media m) {
        MediaVO vo = new MediaVO();
        vo.id = m.getId();
        vo.tmdbId = m.getTmdbId();
        vo.doubanId = m.getDoubanId();
        vo.type = m.getType();
        vo.title = m.getTitle();
        vo.originalTitle = m.getOriginalTitle();
        vo.year = m.getYear();
        vo.poster = m.getPoster();
        vo.posterThumb = m.getPosterThumb();
        vo.backdrop = m.getBackdrop();
        vo.rating = m.getRating();
        vo.overview = m.getOverview();
        vo.genres = split(m.getGenres());
        vo.country = split(m.getCountry());
        vo.actors = split(m.getActors());
        vo.directors = split(m.getDirectors());
        vo.releaseDate = m.getReleaseDate();
        vo.episodeCount = m.getEpisodeCount();
        vo.seasonCount = m.getSeasonCount();
        vo.seriesStatus = m.getSeriesStatus();
        vo.inProduction = m.getInProduction();
        vo.lastAirDate = m.getLastAirDate();
        vo.lastSeasonNumber = m.getLastSeasonNumber();
        vo.lastEpisodeNumber = m.getLastEpisodeNumber();
        vo.hot = m.getHot();
        vo.tier = m.getTier();
        vo.metaSource = m.getMetaSource();
        vo.pubStatus = m.getPubStatus();
        vo.searchHidden = m.getSearchHidden();
        vo.createdAt = m.getCreatedAt();
        vo.updatedAt = m.getUpdatedAt();
        return vo;
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toList();
    }
}
