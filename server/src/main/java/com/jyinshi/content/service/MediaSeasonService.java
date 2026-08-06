package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.entity.MediaSeason;
import com.jyinshi.content.mapper.MediaSeasonMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 媒体季摘要查询（开源不再从 TMDB 同步季）。 */
@Service
public class MediaSeasonService {

    private final MediaSeasonMapper mediaSeasonMapper;

    public MediaSeasonService(MediaSeasonMapper mediaSeasonMapper) {
        this.mediaSeasonMapper = mediaSeasonMapper;
    }

    public List<MediaSeason> listByMediaId(Long mediaId) {
        return mediaSeasonMapper.selectList(Wrappers.<MediaSeason>lambdaQuery()
                .eq(MediaSeason::getMediaId, mediaId)
                .orderByAsc(MediaSeason::getSeasonNumber));
    }
}
