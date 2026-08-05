package com.jyinshi.content.dto;

import com.jyinshi.content.entity.MediaSeason;
import lombok.Data;

import java.util.List;

@Data
public class SeasonVO {

    private Integer seasonNumber;
    private String name;
    private Integer episodeCount;
    private String airDate;
    private String poster;
    private String overview;

    public static SeasonVO from(MediaSeason s) {
        SeasonVO vo = new SeasonVO();
        vo.seasonNumber = s.getSeasonNumber();
        vo.name = s.getName();
        vo.episodeCount = s.getEpisodeCount();
        vo.airDate = s.getAirDate();
        vo.poster = s.getPoster();
        vo.overview = s.getOverview();
        return vo;
    }

    public static List<SeasonVO> fromList(List<MediaSeason> list) {
        return list.stream().map(SeasonVO::from).toList();
    }
}
