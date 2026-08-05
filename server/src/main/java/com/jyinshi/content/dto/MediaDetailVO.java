package com.jyinshi.content.dto;

import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaSeason;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/** 详情页：整部剧 media + 季列表（链接仍挂 media，不按季拆池）。 */
@Data
public class MediaDetailVO {

    private MediaVO media;
    private List<SeasonVO> seasons;

    public static MediaDetailVO of(Media m, List<MediaSeason> seasons) {
        MediaDetailVO vo = new MediaDetailVO();
        vo.media = MediaVO.from(m);
        vo.seasons = seasons == null || seasons.isEmpty()
                ? Collections.emptyList()
                : SeasonVO.fromList(seasons);
        return vo;
    }
}
