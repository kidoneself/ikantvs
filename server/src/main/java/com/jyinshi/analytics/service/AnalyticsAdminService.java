package com.jyinshi.analytics.service;

import com.jyinshi.analytics.dto.AnalyticsOverviewVO;
import com.jyinshi.analytics.dto.MediaStat;
import com.jyinshi.analytics.dto.MetricDelta;
import com.jyinshi.analytics.mapper.AnalyticsStatMapper;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.mapper.MediaMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据洞察（后台报表）。读 content_event 聚合，跨域取媒体标题走 content 的 mapper（只读补字段）。
 */
@Service
public class AnalyticsAdminService {

    private final AnalyticsStatMapper statMapper;
    private final MediaMapper mediaMapper;

    public AnalyticsAdminService(AnalyticsStatMapper statMapper, MediaMapper mediaMapper) {
        this.statMapper = statMapper;
        this.mediaMapper = mediaMapper;
    }

    public AnalyticsOverviewVO overview(int days) {
        int d = days <= 0 ? 7 : Math.min(days, 90);
        LocalDateTime until = LocalDateTime.now();
        LocalDateTime since = until.minusDays(d);
        LocalDateTime prevSince = since.minusDays(d);

        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        vo.setDays(d);
        vo.setVisitors(MetricDelta.of(
                statMapper.countVisitors(since, until),
                statMapper.countVisitors(prevSince, since)));
        vo.setSearches(delta(AnalyticsService.EVENT_SEARCH, since, until, prevSince));
        vo.setCardClicks(delta(AnalyticsService.EVENT_CARD_CLICK, since, until, prevSince));
        vo.setLinkClicks(delta(AnalyticsService.EVENT_LINK_CLICK, since, until, prevSince));

        vo.setTopSearches(statMapper.topSearches(since, until, 20));
        vo.setDemandGaps(statMapper.zeroResultSearches(since, until, 20));
        vo.setTopCardClicked(enrich(
                statMapper.topMedia(AnalyticsService.EVENT_CARD_CLICK, since, until, 10)));
        vo.setTopLinkClicked(enrich(
                statMapper.topMedia(AnalyticsService.EVENT_LINK_CLICK, since, until, 10)));
        return vo;
    }

    private MetricDelta delta(String type, LocalDateTime since, LocalDateTime until,
                              LocalDateTime prevSince) {
        return MetricDelta.of(
                statMapper.countByType(type, since, until),
                statMapper.countByType(type, prevSince, since));
    }

    /** 给媒体维度统计补标题/海报/类型。 */
    private List<MediaStat> enrich(List<MediaStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return stats;
        }
        List<Long> ids = stats.stream().map(MediaStat::getMediaId).toList();
        Map<Long, Media> byId = new LinkedHashMap<>();
        for (Media m : mediaMapper.selectBatchIds(ids)) {
            byId.put(m.getId(), m);
        }
        for (MediaStat s : stats) {
            Media m = byId.get(s.getMediaId());
            if (m != null) {
                s.setTitle(m.getTitle());
                s.setPoster(m.getPoster());
                s.setType(m.getType());
            }
        }
        return stats;
    }
}
