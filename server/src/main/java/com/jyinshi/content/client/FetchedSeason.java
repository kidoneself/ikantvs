package com.jyinshi.content.client;

import lombok.Data;

/** TMDB /tv/{id} seasons[] 解析结果。 */
@Data
public class FetchedSeason {

    private Integer seasonNumber;
    private Integer tmdbSeasonId;
    private String name;
    private Integer episodeCount;
    private String airDate;
    private String poster;
    private String overview;
}
