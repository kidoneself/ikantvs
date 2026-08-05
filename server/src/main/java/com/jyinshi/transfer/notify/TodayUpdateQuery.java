package com.jyinshi.transfer.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.content.service.EpisodeExtractor;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.mapper.TransferMonitorMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 今日有内容更新的剧（按 last_content_at，最新在上）。
 */
@Service
public class TodayUpdateQuery {

    private final TransferMonitorMapper monitorMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final MediaMapper mediaMapper;

    public TodayUpdateQuery(TransferMonitorMapper monitorMapper,
                            MediaLinkMapper mediaLinkMapper,
                            MediaMapper mediaMapper) {
        this.monitorMapper = monitorMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.mediaMapper = mediaMapper;
    }

    /** 今天 last_content_at 有更新的剧，最新在上；同剧多盘合并取最新集数。 */
    public List<UpdateNotifyTexts.Item> listToday() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<TransferMonitor> rows = monitorMapper.selectList(new LambdaQueryWrapper<TransferMonitor>()
                .ge(TransferMonitor::getLastContentAt, start)
                .orderByDesc(TransferMonitor::getLastContentAt));
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, Agg> byKey = new HashMap<>();
        for (TransferMonitor m : rows) {
            Resolved r = resolve(m);
            Agg agg = byKey.get(r.key());
            if (agg == null) {
                agg = new Agg(r.title(), m.getLastContentAt());
                byKey.put(r.key(), agg);
            } else if (m.getLastContentAt() != null
                    && (agg.touched == null || m.getLastContentAt().isAfter(agg.touched))) {
                agg.touched = m.getLastContentAt();
            }
            String ep = EpisodeExtractor.extractDisplay(m.getLatestEpisode());
            if (!StringUtils.hasText(ep) && StringUtils.hasText(m.getLatestEpisode())) {
                ep = m.getLatestEpisode().trim();
            }
            if (StringUtils.hasText(ep)) {
                agg.episode = EpisodeExtractor.pickLatest(agg.episode, ep);
                if (!StringUtils.hasText(agg.episode)) {
                    agg.episode = ep;
                }
            }
        }

        List<Agg> list = new ArrayList<>(byKey.values());
        list.sort(Comparator.comparing((Agg a) -> a.touched,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<UpdateNotifyTexts.Item> out = new ArrayList<>(list.size());
        for (Agg a : list) {
            out.add(new UpdateNotifyTexts.Item(a.title, a.episode));
        }
        return out;
    }

    private Resolved resolve(TransferMonitor monitor) {
        Long linkId = monitor.getMediaLinkId();
        if (linkId != null) {
            MediaLink link = mediaLinkMapper.selectById(linkId);
            if (link != null && link.getMediaId() != null) {
                Media media = mediaMapper.selectById(link.getMediaId());
                if (media != null && StringUtils.hasText(media.getTitle())) {
                    return new Resolved("m:" + media.getId(), media.getTitle().trim());
                }
                if (StringUtils.hasText(link.getNote())) {
                    return new Resolved("m:" + link.getMediaId(), link.getNote().trim());
                }
                return new Resolved("m:" + link.getMediaId(), "未命名");
            }
            if (link != null && StringUtils.hasText(link.getNote())) {
                return new Resolved("l:" + linkId, link.getNote().trim());
            }
        }
        if (StringUtils.hasText(monitor.getLastTitle())) {
            return new Resolved("t:" + monitor.getLastTitle().trim(), monitor.getLastTitle().trim());
        }
        return new Resolved("l:" + (linkId != null ? linkId : "x"), "未命名资源");
    }

    private record Resolved(String key, String title) {
    }

    private static final class Agg {
        final String title;
        String episode;
        LocalDateTime touched;

        Agg(String title, LocalDateTime touched) {
            this.title = title;
            this.touched = touched;
        }
    }
}
