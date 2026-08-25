package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.dto.SearchLinkItemVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.ops.service.SensitiveWordService;
import com.jyinshi.transfer.dto.MonitorLinkView;
import com.jyinshi.transfer.service.TransferMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内链召回：供 search 域 SSE 优先推送已入库链接（含站长精选）。
 * 外源聚合不在本类，见 {@code com.jyinshi.search.service.StreamSearchService}。
 */
@Slf4j
@Service
public class SearchService {

    private final MediaMapper mediaMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final SensitiveWordService sensitiveWordService;
    private final TransferMonitorService transferMonitorService;

    public SearchService(MediaMapper mediaMapper, MediaLinkMapper mediaLinkMapper,
                         SensitiveWordService sensitiveWordService,
                         TransferMonitorService transferMonitorService) {
        this.mediaMapper = mediaMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.transferMonitorService = transferMonitorService;
    }

    /**
     * 供 search 域 SSE：站内已入库链（含自营精选）。不依赖 TMDB/外源；外源由流式搜索另行聚合。
     *
     * @param allowed 当前站点允许的 pan_type（由调用方按域名解析后传入，避免 SSE 异步丢 Request）
     */
    public List<SearchLinkItemVO> listLocalLinks(String keyword, Set<String> allowed) {
        if (!StringUtils.hasText(keyword) || sensitiveWordService.isBlocked(keyword)) {
            return List.of();
        }
        if (allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        List<Media> medias = recallMedia(keyword.trim());
        Map<Long, Media> mediaById = medias.stream()
                .collect(Collectors.toMap(Media::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        List<MediaLink> allLinks = new ArrayList<>(medias.isEmpty() ? List.of() : loadLinks(medias, allowed));
        allLinks.addAll(loadUnboundPool(keyword.trim(), allowed));
        if (allLinks.isEmpty()) {
            return List.of();
        }
        allLinks.sort(linkOrder(mediaById));
        List<Long> mediaIds = medias.stream().map(Media::getId).toList();
        List<MediaLink> selfLinks = mediaIds.isEmpty() ? List.of() : mediaLinkMapper.selectList(
                Wrappers.<MediaLink>lambdaQuery()
                .in(MediaLink::getMediaId, mediaIds)
                .eq(MediaLink::getStatus, "approved")
                .eq(MediaLink::getInvalid, 0)
                .and(w -> w.eq(MediaLink::getSource, "self").or().eq(MediaLink::getSource, "manual")));
        List<Long> localIds = selfLinks.stream().map(MediaLink::getId).toList();
        Map<Long, MonitorLinkView> monitors = transferMonitorService.viewsByMediaLinkIds(localIds);
        Map<Long, String> epByLink = new LinkedHashMap<>();
        Map<Long, String> epByMedia = new LinkedHashMap<>();
        for (MediaLink l : selfLinks) {
            MonitorLinkView view = monitors.get(l.getId());
            if (view == null) {
                continue;
            }
            String ep = EpisodeExtractor.extractDisplay(view.getLatestEpisode());
            if (!StringUtils.hasText(ep)) {
                continue;
            }
            epByLink.put(l.getId(), ep);
            Long mid = l.getMediaId();
            if (mid != null) {
                epByMedia.put(mid, EpisodeExtractor.pickLatest(epByMedia.get(mid), ep));
            }
        }
        List<SearchLinkItemVO> out = new ArrayList<>();
        for (MediaLink l : allLinks) {
            SearchLinkItemVO vo = toItem(l, mediaById.get(l.getMediaId()),
                    l.getMediaId() == null ? epByLink.get(l.getId())
                            : epByMedia.getOrDefault(l.getMediaId(), epByLink.get(l.getId())));
            // 自营：没有我方链就不推（绝不能露出上游大佬链）
            if ("self".equalsIgnoreCase(l.getSource()) && !StringUtils.hasText(vo.getUrl())) {
                continue;
            }
            out.add(vo);
        }
        return out;
    }

    /**
     * 关键词召回已上架片（供流式搜索结果沉淀入库等）。
     *
     * @param limit 最多返回条数，≤0 时按 40
     */
    public List<Media> recallPublishedMedia(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int lim = limit > 0 ? Math.min(limit, 40) : 40;
        LambdaQueryWrapper<Media> w = Wrappers.lambdaQuery();
        w.eq(Media::getPubStatus, 1);
        String today = LocalDate.now().toString();
        w.and(q -> q.isNull(Media::getReleaseDate).or().le(Media::getReleaseDate, today));
        w.and(q -> q.isNull(Media::getSearchHidden).or().eq(Media::getSearchHidden, 0));
        MediaSearchSupport.applyKeyword(w, keyword.trim());
        MediaSearchSupport.applySearchOrder(w, keyword.trim(), null);
        w.last("LIMIT " + lim);
        return mediaMapper.selectList(w).stream()
                .filter(m -> !sensitiveWordService.isBlocked(m.getTitle()))
                .toList();
    }

    private List<Media> recallMedia(String keyword) {
        return recallPublishedMedia(keyword, 40);
    }

    private List<MediaLink> loadLinks(List<Media> medias, Set<String> allowed) {
        List<Long> ids = medias.stream().map(Media::getId).toList();
        List<MediaLink> rows = mediaLinkMapper.selectList(Wrappers.<MediaLink>lambdaQuery()
                .in(MediaLink::getMediaId, ids)
                .eq(MediaLink::getStatus, "approved")
                .eq(MediaLink::getInvalid, 0));
        return rows.stream()
                .filter(l -> allowed.contains(normalizePan(l.getPanType())))
                .filter(l -> !MediaLinkAdFilter.isLikelyAd(l))
                .filter(l -> !sensitiveWordService.isBlocked(l.getNote()))
                .toList();
    }

    /** 未绑剧池：按 note（标题）检索，不查影视库片名。 */
    private List<MediaLink> loadUnboundPool(String keyword, Set<String> allowed) {
        List<MediaLink> rows = mediaLinkMapper.selectList(Wrappers.<MediaLink>lambdaQuery()
                .eq(MediaLink::getMediaId, 0L)
                .eq(MediaLink::getStatus, "approved")
                .eq(MediaLink::getInvalid, 0)
                .like(MediaLink::getNote, keyword)
                .last("LIMIT 40"));
        return rows.stream()
                .filter(l -> allowed.contains(normalizePan(l.getPanType())))
                .filter(l -> !MediaLinkAdFilter.isLikelyAd(l))
                .filter(l -> !sensitiveWordService.isBlocked(l.getNote()))
                .toList();
    }

    private static String normalizePan(String panType) {
        return panType == null ? "" : panType.trim().toLowerCase();
    }

    private static Comparator<MediaLink> linkOrder(Map<Long, Media> mediaById) {
        return Comparator
                .comparingInt((MediaLink l) -> sourceRank(l.getSource()))
                .thenComparing(Comparator.comparingInt((MediaLink l) -> {
                    Media m = mediaById.get(l.getMediaId());
                    return m == null ? 0 : MediaLinkRelevance.score(m, l);
                }).reversed())
                .thenComparing(MediaLink::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static final Map<String, Integer> SOURCE_ORDER = Map.of(
            "self", -1, "manual", 0, "gying", 1, "seedhub", 2, "pool", 3, "pansou", 4);

    private static int sourceRank(String source) {
        if (!StringUtils.hasText(source)) {
            return 5;
        }
        return SOURCE_ORDER.getOrDefault(source.trim().toLowerCase(), 5);
    }

    private static SearchLinkItemVO toItem(MediaLink link, Media media, String latestEpisode) {
        SearchLinkItemVO vo = new SearchLinkItemVO();
        vo.setId(link.getId());
        String note = link.getNote();
        String mediaTitle = media != null ? media.getTitle() : null;
        vo.setTitle(StringUtils.hasText(note) ? note.trim() : Objects.toString(mediaTitle, "未命名资源"));
        vo.setPanType(normalizePan(link.getPanType()));
        vo.setPanLabel(MediaLinkService.panLabel(link.getPanType()));
        vo.setSource(link.getSource());
        vo.setMediaId(link.getMediaId() != null && link.getMediaId() > 0 ? link.getMediaId() : null);
        vo.setMediaTitle(mediaTitle);
        boolean local = "self".equalsIgnoreCase(link.getSource()) || "manual".equalsIgnoreCase(link.getSource());
        vo.setLocal(local);
        if (local && StringUtils.hasText(latestEpisode)) {
            vo.setLatestEpisode(latestEpisode);
        }
        vo.setUrl(clientVisibleUrl(link));
        return vo;
    }

    /**
     * 前台可见链：自营/手动直接出 {@code media_link.url}——写入侧保证该字段只存我方稳定分享
     * （未首转前留空，绝不写上游）。空则不出链，由调用方过滤。
     * 外源 pansou/gying 等本接口不直接出链。
     */
    private static String clientVisibleUrl(MediaLink r) {
        String pan = normalizePan(r.getPanType());
        if ("magnet".equals(pan) || "ed2k".equals(pan)) {
            return MediaLinkUrlNormalizer.normalize(r.getUrl(), r.getPanType());
        }
        if ("self".equalsIgnoreCase(r.getSource()) || "manual".equalsIgnoreCase(r.getSource())) {
            return MediaLinkUrlNormalizer.normalize(r.getUrl(), r.getPanType());
        }
        return null;
    }
}
