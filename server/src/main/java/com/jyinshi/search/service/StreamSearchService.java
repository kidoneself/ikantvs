package com.jyinshi.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.content.dto.SearchLinkItemVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.ingest.IngestProperties;
import com.jyinshi.content.ingest.IngestService;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.ingest.source.LinkSource;
import com.jyinshi.content.ingest.source.RawLink;
import com.jyinshi.content.service.InvalidShareService;
import com.jyinshi.content.service.MediaLinkUrlNormalizer;
import com.jyinshi.content.service.SearchService;
import com.jyinshi.ops.service.SensitiveWordService;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.search.dto.StreamSearchEvent;
import com.jyinshi.search.util.LinkEncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 老站式流式搜索：本地精选优先 → 缓存秒出 → 多源并行补搜（Gying/SeedHub/PanSou…）。
 * 搜到的外源结果异步沉淀进 media_link（不阻塞推前端）。
 */
@Slf4j
@Service
public class StreamSearchService {

    private static final long SSE_TIMEOUT_MS = 60_000L;
    private static final long SOURCE_TIMEOUT_SEC = 25L;
    /** 批量推送：去掉逐条 sleep 后，合并写 SSE 降低开销。 */
    private static final int SSE_BATCH_SIZE = 30;

    private final SearchService localSearchService;
    private final List<LinkSource> linkSources;
    private final IngestProperties ingestProperties;
    private final IngestService ingestService;
    private final SensitiveWordService sensitiveWordService;
    private final SysConfigService sysConfigService;
    private final InvalidShareService invalidShareService;
    private final StreamSearchCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Executor searchExecutor;
    private final Executor sourceSearchExecutor;
    private final Executor ingestExecutor;

    public StreamSearchService(SearchService localSearchService,
                               List<LinkSource> linkSources,
                               IngestProperties ingestProperties,
                               IngestService ingestService,
                               SensitiveWordService sensitiveWordService,
                               SysConfigService sysConfigService,
                               InvalidShareService invalidShareService,
                               StreamSearchCacheService cacheService,
                               ObjectMapper objectMapper,
                               @Qualifier("searchExecutor") Executor searchExecutor,
                               @Qualifier("sourceSearchExecutor") Executor sourceSearchExecutor,
                               @Qualifier("ingestExecutor") Executor ingestExecutor) {
        this.localSearchService = localSearchService;
        this.linkSources = linkSources != null ? linkSources : List.of();
        this.ingestProperties = ingestProperties;
        this.ingestService = ingestService;
        this.sensitiveWordService = sensitiveWordService;
        this.sysConfigService = sysConfigService;
        this.invalidShareService = invalidShareService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.searchExecutor = searchExecutor;
        this.sourceSearchExecutor = sourceSearchExecutor;
        this.ingestExecutor = ingestExecutor;
    }

    public SseEmitter stream(String kw, String cloudTypes, Set<String> allowedPans) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(e -> disconnected.set(true));

        final String keyword = kw == null ? "" : kw.trim();
        final String panFilter = StringUtils.hasText(cloudTypes) ? cloudTypes.trim().toLowerCase(Locale.ROOT) : null;
        // 必须在请求线程解析好；异步线程没有 HttpServletRequest
        final Set<String> allowed = allowedPans != null && !allowedPans.isEmpty()
                ? Set.copyOf(allowedPans)
                : Set.copyOf(sysConfigService.enabledPanTypes());

        CompletableFuture.runAsync(() -> runStream(emitter, disconnected, keyword, panFilter, allowed), searchExecutor);
        return emitter;
    }

    private void runStream(SseEmitter emitter, AtomicBoolean disconnected, String keyword,
                           String panFilter, Set<String> allowed) {
        AtomicInteger sent = new AtomicInteger(0);
        Set<String> pushedUrls = ConcurrentHashMap.newKeySet();
        try {
            if (!StringUtils.hasText(keyword)) {
                send(emitter, StreamSearchEvent.error("system", "请输入搜索关键词"));
                emitter.complete();
                return;
            }
            if (sensitiveWordService.isBlocked(keyword)) {
                send(emitter, StreamSearchEvent.error("system", "搜索词包含违规内容，请换个关键词试试"));
                emitter.complete();
                return;
            }

            send(emitter, StreamSearchEvent.start("开始搜索资源..."));

            SseBatch batch = new SseBatch(emitter, disconnected, sent);

            // 1) 本地已入库链（含站长精选）——有就优先推
            pushLocal(batch, pushedUrls, keyword, panFilter, allowed);
            batch.flush("local");

            // 2) Redis 缓存秒出（整批发，不再一条 sleep 一次）
            StreamSearchCacheService.CachedBundle cache = cacheService.get(keyword);
            if (cache != null && cache.getItems() != null) {
                pushCached(batch, pushedUrls, cache.getItems(), panFilter, allowed, "cache");
                batch.flush("cache");
            }

            // 3) 多源并行（缓存新鲜则跳过，对齐老站冷却）
            boolean refresh = cache == null || !cacheService.isFresh(cache);
            List<StreamSearchCacheService.CachedItem> freshItems = new ArrayList<>();
            if (refresh && !disconnected.get()) {
                freshItems = searchExternalParallel(batch, pushedUrls, keyword, panFilter, allowed);
                batch.flush("stream");
                if (!freshItems.isEmpty()) {
                    List<StreamSearchCacheService.CachedItem> merged = mergeCache(cache, freshItems);
                    cacheService.save(keyword, merged);
                }
            }

            // 4) 每次搜索都沉淀：外源结果异步 upsert 进片库（不挡 SSE 完成）
            List<StreamSearchCacheService.CachedItem> toPersist = !freshItems.isEmpty()
                    ? freshItems
                    : (cache != null ? cache.getItems() : List.of());
            schedulePersist(keyword, toPersist);

            if (!disconnected.get()) {
                batch.flush("stream");
                send(emitter, StreamSearchEvent.complete("搜索完成", sent.get()));
                emitter.complete();
            }
            log.info("[stream-search] 完成 kw={} sent={}", keyword, sent.get());
        } catch (Exception e) {
            if (disconnected.get() || isClientGone(e)) {
                disconnected.set(true);
                log.info("[stream-search] 用户已离开搜索页 kw={} sent={}", keyword, sent.get());
                return;
            }
            log.error("[stream-search] 异常 kw={}", keyword, e);
            try {
                String msg = e.getMessage();
                send(emitter, StreamSearchEvent.error("search",
                        StringUtils.hasText(msg) ? msg : e.getClass().getSimpleName()));
            } catch (Exception ignore) {
                // ignore
            }
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    /** 客户端关掉 SSE / 刷新页面：Broken pipe、Connection reset 等。 */
    private static boolean isClientGone(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.contains("ClientAbortException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("异步关闭")
                    || msg.contains("AsyncRequestNotUsable"))) {
                return true;
            }
        }
        return false;
    }

    private void pushLocal(SseBatch batch, Set<String> pushedUrls, String keyword,
                           String panFilter, Set<String> allowed) throws Exception {
        List<SearchLinkItemVO> locals = localSearchService.listLocalLinks(keyword, allowed);
        for (SearchLinkItemVO vo : locals) {
            if (batch.disconnected()) {
                return;
            }
            String pan = norm(vo.getPanType());
            if (!allowed.contains(pan)) {
                continue;
            }
            if (panFilter != null && !panFilter.equals(pan)) {
                continue;
            }
            String dedupeKey = vo.getId() != null ? "id:" + vo.getId() : ("u:" + vo.getUrl());
            if (!pushedUrls.add(dedupeKey)) {
                continue;
            }

            boolean magnetLike = "magnet".equals(pan) || "ed2k".equals(pan);
            boolean local = vo.isLocal();
            String outUrl;
            if (magnetLike || (local && StringUtils.hasText(vo.getUrl()))) {
                outUrl = vo.getUrl();
            } else if (vo.getId() != null) {
                outUrl = null;
            } else {
                continue;
            }

            StreamSearchEvent.ResourceItem item = new StreamSearchEvent.ResourceItem(
                    vo.getTitle(), outUrl, pan, false, local, vo.getId(), vo.getMediaId(),
                    vo.getLatestEpisode());
            String source = StringUtils.hasText(vo.getSource()) ? vo.getSource() : "local";
            batch.offer(source, item, 40);
        }
    }

    private void pushCached(SseBatch batch, Set<String> pushedUrls,
                            List<StreamSearchCacheService.CachedItem> items,
                            String panFilter, Set<String> allowed, String label) throws Exception {
        for (StreamSearchCacheService.CachedItem c : items) {
            if (batch.disconnected()) {
                return;
            }
            pushRaw(batch, pushedUrls, c.getTitle(), c.getUrl(), c.getPassword(),
                    c.getPanType(), c.getSource() != null ? c.getSource() : label, panFilter, allowed);
        }
    }

    private List<StreamSearchCacheService.CachedItem> searchExternalParallel(
            SseBatch batch, Set<String> pushedUrls,
            String keyword, String panFilter, Set<String> allowed) {
        List<StreamSearchCacheService.CachedItem> collected = new ArrayList<>();
        Object lock = new Object();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (LinkSource source : linkSources) {
            if (!liveSourceEnabled(source)) {
                continue;
            }
            String label = source.sourceName();
            CompletableFuture<Void> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return source.search(keyword);
                } catch (Exception e) {
                    log.warn("[stream-search] 来源 {} 异常: {}", label, e.getMessage());
                    return List.<RawLink>of();
                }
            }, sourceSearchExecutor).thenAcceptAsync(raws -> {
                if (raws == null || raws.isEmpty() || batch.disconnected()) {
                    return;
                }
                for (RawLink raw : raws) {
                    if (batch.disconnected()) {
                        return;
                    }
                    StreamSearchCacheService.CachedItem cached = toCached(raw, label);
                    if (cached == null) {
                        continue;
                    }
                    synchronized (lock) {
                        collected.add(cached);
                    }
                    try {
                        pushRaw(batch, pushedUrls,
                                cached.getTitle(), cached.getUrl(), cached.getPassword(),
                                cached.getPanType(), label, panFilter, allowed);
                    } catch (Exception e) {
                        batch.markDisconnected();
                    }
                }
            }, sourceSearchExecutor);
            futures.add(f);
        }

        if (futures.isEmpty()) {
            return collected;
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(SOURCE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[stream-search] 多源超时（{}s）kw={}", SOURCE_TIMEOUT_SEC, keyword);
        } catch (Exception e) {
            log.warn("[stream-search] 多源异常 kw={}: {}", keyword, e.getMessage());
        }
        return collected;
    }

    private void pushRaw(SseBatch batch, Set<String> pushedUrls, String title, String url, String password,
                         String panType, String sourceLabel, String panFilter, Set<String> allowed)
            throws Exception {
        if (batch.disconnected() || !StringUtils.hasText(url)) {
            return;
        }
        String pan = norm(panType);
        if (!StringUtils.hasText(pan) || !allowed.contains(pan)) {
            return;
        }
        if (panFilter != null && !panFilter.equals(pan)) {
            return;
        }
        String normalized = MediaLinkUrlNormalizer.normalize(url, pan);
        if (!StringUtils.hasText(normalized)) {
            normalized = url.trim();
        }
        String shareId = ShareIdExtractor.extract(normalized, pan);
        if (StringUtils.hasText(shareId) && invalidShareService.isInvalid(pan, shareId)) {
            return;
        }
        if (!pushedUrls.add(normalized)) {
            return;
        }

        boolean magnetLike = "magnet".equals(pan) || "ed2k".equals(pan);
        String outUrl = magnetLike
                ? normalized
                : LinkEncryptUtil.encrypt(normalized, password, pan);

        StreamSearchEvent.ResourceItem item = new StreamSearchEvent.ResourceItem(
                StringUtils.hasText(title) ? title.trim() : "未命名资源",
                outUrl, pan, false, false, null, null);
        batch.offer(sourceLabel, item, 95);
    }

    /** 线程安全的 SSE 批量缓冲：满一批立刻刷，结束时再 flush。 */
    private final class SseBatch {
        private final SseEmitter emitter;
        private final AtomicBoolean disconnected;
        private final AtomicInteger sent;
        private final List<StreamSearchEvent.ResourceItem> buf = new ArrayList<>(SSE_BATCH_SIZE);
        private String bufSource = "stream";

        SseBatch(SseEmitter emitter, AtomicBoolean disconnected, AtomicInteger sent) {
            this.emitter = emitter;
            this.disconnected = disconnected;
            this.sent = sent;
        }

        boolean disconnected() {
            return disconnected.get();
        }

        void markDisconnected() {
            disconnected.set(true);
        }

        synchronized void offer(String source, StreamSearchEvent.ResourceItem item, int progressCap)
                throws IOException {
            if (disconnected.get()) {
                return;
            }
            if (buf.isEmpty() && StringUtils.hasText(source)) {
                bufSource = source;
            }
            buf.add(item);
            sent.incrementAndGet();
            if (buf.size() >= SSE_BATCH_SIZE) {
                flushLocked(progressCap);
            }
        }

        void flush(String source) throws IOException {
            synchronized (this) {
                if (StringUtils.hasText(source)) {
                    bufSource = source;
                }
                flushLocked(95);
            }
        }

        private void flushLocked(int progressCap) throws IOException {
            if (buf.isEmpty() || disconnected.get()) {
                buf.clear();
                return;
            }
            List<StreamSearchEvent.ResourceItem> out = new ArrayList<>(buf);
            buf.clear();
            int progress = Math.min(progressCap, Math.max(3, sent.get() * 2));
            send(emitter, StreamSearchEvent.items(bufSource, out, progress));
        }
    }

    /** 异步落库：有匹配片才写；池满则丢弃本轮沉淀（下次搜还会再试）。 */
    private void schedulePersist(String keyword, List<StreamSearchCacheService.CachedItem> items) {
        if (!StringUtils.hasText(keyword) || items == null || items.isEmpty()) {
            return;
        }
        List<RawLink> raws = items.stream()
                .map(StreamSearchService::cachedToRaw)
                .filter(Objects::nonNull)
                .toList();
        if (raws.isEmpty()) {
            return;
        }
        try {
            ingestExecutor.execute(() -> {
                try {
                    List<Media> medias = localSearchService.recallPublishedMedia(keyword, 5);
                    if (medias.isEmpty()) {
                        log.info("[stream-search] 无匹配片，跳过落库 kw={} candidates={}", keyword, raws.size());
                        return;
                    }
                    ingestService.persistStreamResults(medias, raws);
                } catch (Exception e) {
                    log.warn("[stream-search] 落库失败 kw={}: {}", keyword, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[stream-search] 入库线程池满，跳过落库 kw={}", keyword);
        }
    }

    private static RawLink cachedToRaw(StreamSearchCacheService.CachedItem c) {
        if (c == null || !StringUtils.hasText(c.getUrl())) {
            return null;
        }
        return RawLink.of(c.getPanType(), c.getUrl(), c.getPassword(), c.getTitle(),
                StringUtils.hasText(c.getSource()) ? c.getSource() : "stream");
    }

    private static StreamSearchCacheService.CachedItem toCached(RawLink raw, String fallbackSource) {
        if (raw == null || !StringUtils.hasText(raw.getUrl())) {
            return null;
        }
        String pan = norm(raw.getPanType());
        if (!StringUtils.hasText(pan)) {
            return null;
        }
        String url = MediaLinkUrlNormalizer.normalize(raw.getUrl().trim(), pan);
        if (!StringUtils.hasText(url)) {
            url = raw.getUrl().trim();
        }
        String title = StringUtils.hasText(raw.getNote()) ? raw.getNote().trim()
                : (StringUtils.hasText(raw.getMatchTitle()) ? raw.getMatchTitle().trim() : "未命名资源");
        String source = StringUtils.hasText(raw.getSource()) ? raw.getSource() : fallbackSource;
        String dt = raw.getPublishedAt() != null ? raw.getPublishedAt().toString() : null;
        return StreamSearchCacheService.CachedItem.of(title, url, raw.getPassword(), pan, source, dt);
    }

    private static List<StreamSearchCacheService.CachedItem> mergeCache(
            StreamSearchCacheService.CachedBundle old, List<StreamSearchCacheService.CachedItem> fresh) {
        List<StreamSearchCacheService.CachedItem> out = new ArrayList<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        if (old != null && old.getItems() != null) {
            for (StreamSearchCacheService.CachedItem c : old.getItems()) {
                if (c != null && StringUtils.hasText(c.getUrl()) && seen.add(c.getUrl())) {
                    out.add(c);
                }
            }
        }
        for (StreamSearchCacheService.CachedItem c : fresh) {
            if (c != null && StringUtils.hasText(c.getUrl()) && seen.add(c.getUrl())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * 实时搜索：只看各来源自身开关，不依赖入库总开关（用户搜链与后台采集解耦）。
     */
    private boolean liveSourceEnabled(LinkSource source) {
        if (source == null) {
            return false;
        }
        return switch (source.sourceName()) {
            case "pansou" -> ingestProperties.getPansou().isEnabled();
            case "gying" -> ingestProperties.getGying().isEnabled();
            case "seedhub" -> ingestProperties.getSeedhub().isEnabled();
            default -> source.isEnabled();
        };
    }

    private void send(SseEmitter emitter, StreamSearchEvent event) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event)));
        }
    }

    private static String norm(String pan) {
        return pan == null ? "" : pan.trim().toLowerCase(Locale.ROOT);
    }
}
