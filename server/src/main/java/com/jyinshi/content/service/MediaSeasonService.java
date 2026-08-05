package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.client.FetchedSeason;
import com.jyinshi.content.entity.MediaSeason;
import com.jyinshi.content.mapper.MediaSeasonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 媒体季摘要：import/refresh 时从 TMDB 同步到 media_season。 */
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

    /**
     * 用 TMDB 抓取的季列表 upsert；并删除 TMDB 已不存在的季号。
     * 豆瓣等无季数据时不调用，保留已有记录。
     */
    @Transactional
    public void syncFromFetched(Long mediaId, List<FetchedSeason> fetched) {
        if (mediaId == null || fetched == null || fetched.isEmpty()) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (FetchedSeason f : fetched) {
            if (f.getSeasonNumber() == null || f.getSeasonNumber() <= 0) {
                continue;
            }
            seen.add(f.getSeasonNumber());
            MediaSeason existing = mediaSeasonMapper.selectOne(Wrappers.<MediaSeason>lambdaQuery()
                    .eq(MediaSeason::getMediaId, mediaId)
                    .eq(MediaSeason::getSeasonNumber, f.getSeasonNumber())
                    .last("limit 1"));
            if (existing != null) {
                applyFetched(existing, f);
                existing.setUpdatedAt(now);
                mediaSeasonMapper.updateById(existing);
            } else {
                MediaSeason row = new MediaSeason();
                row.setMediaId(mediaId);
                row.setSeasonNumber(f.getSeasonNumber());
                applyFetched(row, f);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                mediaSeasonMapper.insert(row);
            }
        }
        if (!seen.isEmpty()) {
            mediaSeasonMapper.delete(Wrappers.<MediaSeason>lambdaQuery()
                    .eq(MediaSeason::getMediaId, mediaId)
                    .notIn(MediaSeason::getSeasonNumber, seen));
        }
    }

    private void applyFetched(MediaSeason row, FetchedSeason f) {
        row.setTmdbSeasonId(f.getTmdbSeasonId());
        if (StringUtils.hasText(f.getName())) {
            row.setName(f.getName());
        }
        if (f.getEpisodeCount() != null) {
            row.setEpisodeCount(f.getEpisodeCount());
        }
        if (StringUtils.hasText(f.getAirDate())) {
            row.setAirDate(f.getAirDate());
        }
        if (StringUtils.hasText(f.getPoster())) {
            row.setPoster(f.getPoster());
        }
        if (StringUtils.hasText(f.getOverview())) {
            row.setOverview(f.getOverview());
        }
    }
}
