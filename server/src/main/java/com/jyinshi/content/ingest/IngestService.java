package com.jyinshi.content.ingest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.source.LinkSource;
import com.jyinshi.content.ingest.source.RawLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.content.service.InvalidShareService;
import com.jyinshi.content.service.MediaLinkAdFilter;
import com.jyinshi.content.service.MediaLinkRelevance;
import com.jyinshi.ops.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 资源聚合入库编排（content 域）。
 *
 * <p>把所有启用的 {@link LinkSource} 插件按剧名搜到的候选，过「广告/敏感/相关性」门槛后
 * 幂等 upsert 进 {@code media_link}。<b>只入库不判活死</b>（死活在转存点击时判）。
 * 每部片带冷却，防止短时间反复打来源。
 */
@Slf4j
@Service
public class IngestService {

    private static final String COOLDOWN_KEY = "jyinshi:ingest:cd:";

    private final IngestProperties props;
    private final IngestPanFilter panFilter;
    private final List<LinkSource> sources;
    private final MediaMapper mediaMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final SensitiveWordService sensitiveWordService;
    private final InvalidShareService invalidShareService;
    private final StringRedisTemplate redis;

    public IngestService(IngestProperties props, IngestPanFilter panFilter, List<LinkSource> sources,
                         MediaMapper mediaMapper, MediaLinkMapper mediaLinkMapper,
                         SensitiveWordService sensitiveWordService,
                         InvalidShareService invalidShareService, StringRedisTemplate redis) {
        this.props = props;
        this.panFilter = panFilter;
        this.sources = sources;
        this.mediaMapper = mediaMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.invalidShareService = invalidShareService;
        this.redis = redis;
    }

    /**
     * 流式搜索沉淀：把本轮已搜到的外源结果挂到召回的片上。
     * <p>不重打 pansou/gying（候选已由 SSE 搜过）。相关度门槛仍在，防挂错片。
     *
     * @param medias     关键词召回的片（通常 1～5 部）；空则跳过
     * @param candidates 本轮外源结果
     */
    public void persistStreamResults(List<Media> medias, List<RawLink> candidates) {
        if (!props.isEnabled() || medias == null || medias.isEmpty()
                || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (Media media : medias) {
            if (media == null || media.getId() == null) {
                continue;
            }
            IngestResult r = ingestCandidates(media, candidates);
            if (r.getAdded() > 0 || r.getUpdated() > 0) {
                // 写完也打冷却，避免紧接着详情页又全量 gather 打一遍外源
                overrideCooldown(media.getId(), props.getCooldownMinutes());
            }
        }
    }

    /**
     * 为某部片按剧名采集入库（发现入库 / 后台保鲜）。
     * SSE 搜索沉淀走 {@link #persistStreamResults}，不重打外源。
     */
    public IngestResult ingestForMedia(Long mediaId) {
        if (!props.isEnabled()) {
            return IngestResult.status("disabled");
        }
        Media media = mediaId == null ? null : mediaMapper.selectById(mediaId);
        if (media == null || !StringUtils.hasText(media.getTitle())) {
            return IngestResult.status("no_media");
        }
        if (!acquireCooldown(mediaId)) {
            return IngestResult.status("cooldown");
        }

        List<RawLink> candidates = gather(buildKeywords(media));
        if (candidates.isEmpty()) {
            // 没搜到 → 缩短冷却，让「暂无资源」的片能被多次访问持续重试
            overrideCooldown(mediaId, props.getEmptyCooldownMinutes());
            return IngestResult.status("no_result");
        }
        IngestResult r = ingestCandidates(media, candidates);
        if (r.getAdded() == 0 && r.getUpdated() == 0) {
            // 全被门槛丢弃、一条没入库 → 同样按空结果对待，缩短冷却
            overrideCooldown(mediaId, props.getEmptyCooldownMinutes());
        }
        return r;
    }

    /**
     * 采集用的关键词：中文名 + 原名（英文/外文原名，去重）。
     *
     * <p>很多资源在 pansou 里只按英文原名收录，只用中文名会漏。原名搜回来的候选照样过相关性门槛
     * （{@link MediaLinkRelevance} 已同时按中文名与原名打分），不会挂错片。
     */
    private List<String> buildKeywords(Media media) {
        java.util.LinkedHashSet<String> kws = new java.util.LinkedHashSet<>();
        if (StringUtils.hasText(media.getTitle())) {
            kws.add(media.getTitle().trim());
        }
        String orig = media.getOriginalTitle();
        if (StringUtils.hasText(orig)) {
            String o = orig.trim();
            // 原名与中文名规范化后相同则不重复搜
            boolean same = kws.stream().anyMatch(k -> k.equalsIgnoreCase(o));
            if (!same) {
                kws.add(o);
            }
        }
        return new java.util.ArrayList<>(kws);
    }

    /** 汇总所有启用来源、所有关键词的候选（各来源自吞异常，互不影响；重复候选由下游 shareId 去重）。 */
    private List<RawLink> gather(List<String> keywords) {
        java.util.ArrayList<RawLink> all = new java.util.ArrayList<>();
        for (LinkSource s : sources) {
            if (!s.isEnabled()) {
                continue;
            }
            for (String keyword : keywords) {
                try {
                    List<RawLink> got = s.search(keyword);
                    if (got != null) {
                        all.addAll(got);
                    }
                } catch (Exception e) {
                    log.warn("[ingest] 来源 {} 搜索异常 kw={}: {}", s.sourceName(), keyword, e.getMessage());
                }
            }
        }
        return all;
    }

    private IngestResult ingestCandidates(Media media, List<RawLink> candidates) {
        Set<String> existing = loadExistingKeys(media.getId());
        Set<String> deadKeys = loadDeadKeys(candidates);
        Set<String> seenThisRun = new HashSet<>();
        int added = 0;
        int updated = 0;
        int skipped = 0;

        for (RawLink raw : candidates) {
            String pan = raw.getPanType() == null ? "" : raw.getPanType().trim().toLowerCase();
            if (!StringUtils.hasText(pan) || !StringUtils.hasText(raw.getUrl())) {
                skipped++;
                continue;
            }
            if (!panFilter.allows(pan, "")) {
                skipped++;
                continue;
            }
            if (!passesGates(media, raw)) {
                skipped++;
                continue;
            }
            String shareId = ShareIdExtractor.extract(raw.getUrl(), pan);
            String dedupKey = pan + "|" + shareId;
            // 已知失效的分享直接不入库（等价老系统 filterInvalidLinks），避免死链反复被采回。
            if (deadKeys.contains(dedupKey)) {
                skipped++;
                continue;
            }
            if (!seenThisRun.add(dedupKey)) {
                continue;
            }
            String storedUrl = combineUrl(raw.getUrl(), raw.getPassword());
            try {
                mediaLinkMapper.upsert(media.getId(), pan, storedUrl, shareId,
                        trimNote(raw.getNote()), raw.getSource());
                if (existing.contains(dedupKey)) {
                    updated++;
                } else {
                    added++;
                }
            } catch (Exception e) {
                log.warn("[ingest] upsert 失败 media={} pan={} share={}: {}",
                        media.getId(), pan, shareId, e.getMessage());
            }
        }

        IngestResult r = IngestResult.status("done");
        r.setAdded(added);
        r.setUpdated(updated);
        r.setSkipped(skipped);
        if (added > 0 || updated > 0) {
            log.info("[ingest] media={}({}) 入库完成 新增={} 刷新={} 丢弃={}",
                    media.getId(), media.getTitle(), added, updated, skipped);
        }
        return r;
    }

    /** 准入门槛：广告 / 敏感词 / 相关性（防挂错片）。 */
    private boolean passesGates(Media media, RawLink raw) {
        String pan = raw.getPanType() == null ? "" : raw.getPanType().trim().toLowerCase();
        String note = raw.getNote();
        MediaLink probe = new MediaLink();
        probe.setSource(raw.getSource() != null ? raw.getSource() : "pansou");
        probe.setPanType(pan);
        probe.setNote(note);
        if (MediaLinkAdFilter.isLikelyAd(probe)) {
            return false;
        }
        if (sensitiveWordService.isBlocked(note)) {
            return false;
        }
        int score = MediaLinkRelevance.score(media, probe);
        int threshold = props.getRelevanceThreshold();
        if (score < threshold && StringUtils.hasText(raw.getMatchTitle())) {
            // 详情页标题已与片库命中（如 gying 搜「昨夜将至」进详情），页内纯英文磁力名也视为同片。
            int pageScore = MediaLinkRelevance.scoreTitlePair(raw.getMatchTitle(), media.getTitle());
            if (pageScore < threshold && StringUtils.hasText(media.getOriginalTitle())) {
                pageScore = Math.max(pageScore,
                        MediaLinkRelevance.scoreTitlePair(raw.getMatchTitle(), media.getOriginalTitle()));
            }
            if (pageScore >= threshold) {
                score = threshold;
            }
        }
        return score >= threshold;
    }

    /**
     * 本轮候选里命中失效黑名单的 (panType|shareId) 集合，入库前据此跳过。
     * 按 pan 分组批量查库，一次 IN 查询搞定，避免逐条打库。
     */
    private Set<String> loadDeadKeys(List<RawLink> candidates) {
        if (!props.isFilterInvalidShare() || candidates == null || candidates.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.Map<String, Set<String>> byPan = new java.util.HashMap<>();
        for (RawLink raw : candidates) {
            String pan = raw.getPanType() == null ? "" : raw.getPanType().trim().toLowerCase();
            if (!StringUtils.hasText(pan) || !StringUtils.hasText(raw.getUrl())) {
                continue;
            }
            String shareId = ShareIdExtractor.extract(raw.getUrl(), pan);
            if (StringUtils.hasText(shareId)) {
                byPan.computeIfAbsent(pan, k -> new HashSet<>()).add(shareId);
            }
        }
        Set<String> deadKeys = new HashSet<>();
        for (java.util.Map.Entry<String, Set<String>> e : byPan.entrySet()) {
            for (String dead : invalidShareService.knownInvalid(e.getKey(), e.getValue())) {
                deadKeys.add(e.getKey() + "|" + dead);
            }
        }
        return deadKeys;
    }

    /** 已有链接的 (panType|shareId) 集合，用于精确区分新增/刷新。 */
    private Set<String> loadExistingKeys(Long mediaId) {
        List<MediaLink> rows = mediaLinkMapper.selectList(Wrappers.<MediaLink>lambdaQuery()
                .select(MediaLink::getPanType, MediaLink::getShareId)
                .eq(MediaLink::getMediaId, mediaId));
        Set<String> keys = new HashSet<>();
        for (MediaLink row : rows) {
            keys.add((row.getPanType() == null ? "" : row.getPanType()) + "|" + row.getShareId());
        }
        return keys;
    }

    /** 覆盖冷却时长（用于空结果缩短冷却）。分钟≤0 则直接清掉冷却，允许立即重试。 */
    private void overrideCooldown(Long mediaId, int minutes) {
        try {
            String key = COOLDOWN_KEY + mediaId;
            if (minutes <= 0) {
                redis.delete(key);
            } else {
                redis.opsForValue().set(key, "1", Duration.ofMinutes(minutes));
            }
        } catch (Exception ignore) {
            // Redis 不可用无所谓，下次照常尝试
        }
    }

    /** 抢冷却锁：成功=可采，失败=冷却内。 */
    private boolean acquireCooldown(Long mediaId) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    COOLDOWN_KEY + mediaId, "1",
                    Duration.ofMinutes(Math.max(1, props.getCooldownMinutes())));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用时不拦截，允许采集（宁可多采不误杀）
            return true;
        }
    }

    /** 把提取码并进 url，存成自包含链接（读侧 normalizer 再统一处理）。 */
    private static String combineUrl(String url, String password) {
        String u = url.trim();
        if (!StringUtils.hasText(password)) {
            return truncate(u);
        }
        String lower = u.toLowerCase();
        if (lower.contains("pwd=") || lower.contains("password=") || u.contains("|")) {
            return truncate(u);
        }
        String pwd = password.trim();
        return truncate(u + (u.contains("?") ? "&" : "?") + "pwd=" + pwd);
    }

    private static String trimNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String n = note.replaceAll("https?://\\S+", "").trim();
        return n.length() <= 255 ? n : n.substring(0, 255);
    }

    private static String truncate(String s) {
        return s.length() <= 1024 ? s : s.substring(0, 1024);
    }
}
