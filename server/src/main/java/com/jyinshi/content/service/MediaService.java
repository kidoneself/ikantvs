package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.client.DoubanClient;
import com.jyinshi.content.client.FetchedMetadata;
import com.jyinshi.content.client.TmdbClient;
import com.jyinshi.content.dto.AdminDashboardVO;
import com.jyinshi.content.dto.ManualMediaRequest;
import com.jyinshi.content.dto.MediaDetailVO;
import com.jyinshi.content.dto.MediaImportRequest;
import com.jyinshi.content.dto.MediaUpdateRequest;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.ops.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容域：媒体信息采集与查询。
 *
 * <p>三级元数据策略：TMDB(采集主源) → 豆瓣(补录兜底) → 仅录入标题(none)。
 * 链接不在这里，单独走 media_link（后续阶段）。
 */
@Slf4j
@Service
public class MediaService {

    private final MediaMapper mediaMapper;
    private final TmdbClient tmdbClient;
    private final DoubanClient doubanClient;
    private final PosterMirrorService posterMirrorService;
    private final SensitiveWordService sensitiveWordService;
    private final MediaSeasonService mediaSeasonService;
    private final MediaSearchCacheService searchCacheService;

    public MediaService(MediaMapper mediaMapper,
                        TmdbClient tmdbClient, DoubanClient doubanClient,
                        PosterMirrorService posterMirrorService, SensitiveWordService sensitiveWordService,
                        MediaSeasonService mediaSeasonService, MediaSearchCacheService searchCacheService) {
        this.mediaMapper = mediaMapper;
        this.tmdbClient = tmdbClient;
        this.doubanClient = doubanClient;
        this.posterMirrorService = posterMirrorService;
        this.sensitiveWordService = sensitiveWordService;
        this.mediaSeasonService = mediaSeasonService;
        this.searchCacheService = searchCacheService;
    }

    /**
     * 单条采集：给 TMDB 或 豆瓣 的链接/ID，采那一个源（不做跨源兜底）。
     */
    @Transactional
    public MediaVO importByExternalId(MediaImportRequest req) {
        Integer tmdbId = req.getTmdbId();
        String doubanId = req.getDoubanId();
        String type = req.getType();

        // 解析链接 → 源 + id + 类型
        if (StringUtils.hasText(req.getUrl())) {
            String[] tmdbRef = TmdbClient.parseRef(req.getUrl());
            String dbId = DoubanClient.parseId(req.getUrl());
            if (tmdbRef != null) {
                type = tmdbRef[0];
                tmdbId = Integer.valueOf(tmdbRef[1]);
            } else if (dbId != null) {
                doubanId = dbId;
            } else {
                throw new BizException("无法识别的链接，请用 TMDB 或 豆瓣 详情页链接");
            }
        }

        FetchedMetadata meta;
        if (tmdbId != null) {
            if (!tmdbClient.isConfigured()) {
                throw new BizException("TMDB 未配置 api-key，无法采集");
            }
            meta = tmdbClient.fetchById(tmdbId, type);
            if (meta == null) {
                throw new BizException("TMDB 未找到该条目（id=" + tmdbId + "）");
            }
        } else if (StringUtils.hasText(doubanId)) {
            meta = doubanClient.fetchById(doubanId, type);
            if (meta == null) {
                throw new BizException("豆瓣未找到或抓取失败（id=" + doubanId + "）");
            }
        } else {
            throw new BizException("请提供 TMDB/豆瓣 链接或 id");
        }

        Media saved = upsert(meta, Boolean.TRUE.equals(req.getPublish()));
        return MediaVO.from(saved);
    }

    /** 仅录入：都没有外部数据时，人工建「仅标题」条目。 */
    @Transactional
    public MediaVO createManual(ManualMediaRequest req) {
        Media m = new Media();
        m.setTitle(req.getTitle());
        m.setType(StringUtils.hasText(req.getType()) ? req.getType() : "movie");
        m.setYear(req.getYear());
        m.setPoster(req.getPoster());
        m.setOverview(req.getOverview());
        m.setGenres(req.getGenres());
        m.setMetaSource("none");
        m.setHot(0);
        m.setHotSeed(0);
        m.setTier(0);
        boolean allowPublish = Boolean.TRUE.equals(req.getPublish())
                && passSensitiveGate(req.getTitle());
        m.setPubStatus(allowPublish ? 1 : 0);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        mediaMapper.insert(m);
        posterMirrorService.mirrorMediaImages(m);
        bumpSearchCache();
        return MediaVO.from(m);
    }

    /** 重新抓取（刷新元数据），按已存的 tmdbId/doubanId 重拉。 */
    @Transactional
    public MediaVO refresh(Long id) {
        Media existing = mediaMapper.selectById(id);
        if (existing == null) {
            throw new BizException("内容不存在");
        }
        // 按内容自身的源刷新，不跨源
        FetchedMetadata meta;
        if ("douban".equals(existing.getMetaSource()) && StringUtils.hasText(existing.getDoubanId())) {
            meta = doubanClient.fetchById(existing.getDoubanId(), existing.getType());
        } else if (existing.getTmdbId() != null) {
            meta = tmdbClient.fetchById(existing.getTmdbId(), existing.getType());
        } else if (StringUtils.hasText(existing.getDoubanId())) {
            meta = doubanClient.fetchById(existing.getDoubanId(), existing.getType());
        } else {
            throw new BizException("该内容无外部 id，无法刷新");
        }
        if (meta == null) {
            throw new BizException("重新抓取失败（源不可用）");
        }
        applyMeta(existing, meta);
        syncSeasonsIfPresent(existing.getId(), meta);
        tryResolveComplementaryDoubanId(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        mediaMapper.updateById(existing);
        posterMirrorService.mirrorMediaImages(existing);
        bumpSearchCache();
        return MediaVO.from(existing);
    }

    /** 后台详情：media + 季列表（链接仍挂整部剧，不按季拆）。含未发布/敏感内容。 */
    public MediaDetailVO getDetail(Long id) {
        Media m = mediaMapper.selectById(id);
        if (m == null) {
            throw new BizException("内容不存在");
        }
        return MediaDetailVO.of(m, mediaSeasonService.listByMediaId(id));
    }

    public MediaVO get(Long id) {
        Media m = mediaMapper.selectById(id);
        if (m == null) {
            throw new BizException("内容不存在");
        }
        return MediaVO.from(m);
    }

    /** 前台收藏等场景：须已发布且未命中 block 级敏感词。 */
    public void requirePublished(Long id) {
        Media m = mediaMapper.selectById(id);
        if (!MediaPublicVisibility.isVisible(m, sensitiveWordService)) {
            throw new BizException("内容不存在");
        }
    }

    /** 按 id 顺序返回已发布内容；下架/删除的条目自动跳过。 */
    public List<MediaVO> listPublishedByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Media> byId = mediaMapper.selectBatchIds(ids).stream()
                .filter(m -> MediaPublicVisibility.isVisible(m, sensitiveWordService))
                .collect(Collectors.toMap(Media::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return ids.stream()
                .filter(byId::containsKey)
                .map(id -> {
                    Media m = byId.get(id);
                    MediaPublicVisibility.maskOverview(m, sensitiveWordService);
                    return MediaVO.from(m);
                })
                .toList();
    }

    /** 后台列表：含草稿/下架；{@code hiddenOnly=true} 时只返回前台隐藏的条目。 */
    public PageResult<MediaVO> pageAdmin(long page, long size, String type, String keyword, Boolean hiddenOnly) {
        LambdaQueryWrapper<Media> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(type)) {
            w.eq(Media::getType, type);
        }
        if (Boolean.TRUE.equals(hiddenOnly)) {
            w.eq(Media::getSearchHidden, 1);
        }
        if (StringUtils.hasText(keyword)) {
            MediaSearchSupport.applyKeyword(w, keyword.trim());
            MediaSearchSupport.applySearchOrder(w, keyword.trim(), null);
        } else {
            w.orderByDesc(Media::getUpdatedAt).orderByDesc(Media::getId);
        }
        IPage<Media> p = mediaMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size,
                p.getRecords().stream().map(MediaVO::from).toList());
    }

    /** 列表分页。onlyPublished=true 时只返回已发布（前台用）。 */
    public PageResult<MediaVO> page(long page, long size, String type, Boolean onlyPublished) {
        return page(page, size, type, onlyPublished, null, null);
    }

    public PageResult<MediaVO> page(long page, long size, String type, Boolean onlyPublished, String keyword) {
        return page(page, size, type, onlyPublished, keyword, null);
    }

    /**
     * 列表分页。{@code sort}：
     * <ul>
     *   <li>{@code new} —— 最新，按上映/开播时间 release_date 倒序（不是入库时间）</li>
     *   <li>{@code rating} —— 高分，按评分倒序</li>
     *   <li>{@code hot}（默认）—— 最热，按热度倒序</li>
     * </ul>
     */
    public PageResult<MediaVO> page(long page, long size, String type, Boolean onlyPublished,
                                    String keyword, String sort) {
        return page(page, size, type, onlyPublished, keyword, sort, null, null, null, null, null);
    }

    public PageResult<MediaVO> page(long page, long size, String type, Boolean onlyPublished,
                                    String keyword, String sort,
                                    Integer yearFrom, Integer yearTo, String genre) {
        return page(page, size, type, onlyPublished, keyword, sort, yearFrom, yearTo, genre, null, null);
    }

    /**
     * 列表分页（带筛选）。
     *
     * @param yearFrom  年份下限（含），null 不限
     * @param yearTo    年份上限（含），null 不限
     * @param genre     题材关键词（按 genres 模糊匹配），null 不限
     * @param country   地区（ISO 码，如 CN/US/JP，按 country 集合精确匹配），null 不限
     * @param minRating 最低评分（含），null 不限
     */
    public PageResult<MediaVO> page(long page, long size, String type, Boolean onlyPublished,
                                    String keyword, String sort,
                                    Integer yearFrom, Integer yearTo, String genre,
                                    String country, java.math.BigDecimal minRating) {
        // 前台搜索词命中敏感词(block) → 直接返回空，对外只表现为"无相关内容"，不暴露被拦。
        // 后台(onlyPublished!=true)不拦，管理员要能搜到全部。
        if (Boolean.TRUE.equals(onlyPublished) && StringUtils.hasText(keyword)
                && sensitiveWordService.isBlocked(keyword)) {
            log.info("搜索词命中敏感词，已拦截：{}", keyword);
            return PageResult.of(0, page, size, List.of());
        }
        MediaSearchCacheKey cacheKey = null;
        if (Boolean.TRUE.equals(onlyPublished) && searchCacheService.isEnabled()) {
            cacheKey = MediaSearchCacheKey.of(page, size, type, keyword, sort,
                    yearFrom, yearTo, genre, country, minRating);
            var cached = searchCacheService.get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        PageResult<MediaVO> result = queryPage(page, size, type, onlyPublished, keyword, sort,
                yearFrom, yearTo, genre, country, minRating);
        if (cacheKey != null) {
            searchCacheService.put(cacheKey, result);
        }
        return result;
    }

    private PageResult<MediaVO> queryPage(long page, long size, String type, Boolean onlyPublished,
                                          String keyword, String sort,
                                          Integer yearFrom, Integer yearTo, String genre,
                                          String country, java.math.BigDecimal minRating) {
        LambdaQueryWrapper<Media> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(type)) {
            w.eq(Media::getType, type);
        }
        if (Boolean.TRUE.equals(onlyPublished)) {
            w.eq(Media::getPubStatus, 1);
            // 前台不推还没上映的：日期未知(null)或已上映(<=今天)才展示
            String today = LocalDate.now().toString();
            w.and(q -> q.isNull(Media::getReleaseDate).or().le(Media::getReleaseDate, today));
            // 运营标记隐藏的条目前台一律不可见（搜索/分类/首页/详情）
            w.and(q -> q.isNull(Media::getSearchHidden).or().eq(Media::getSearchHidden, 0));
            // 标题命中 block 级敏感词的整条隐藏（如违禁片名）改到「查出来后对当前页内存 DFA 过滤」，
            // 不再把上千个 block 词塞进 SQL：否则 MyBatis-Plus 分页的 COUNT(*) 会拿每个词做全表 LIKE
            // 扫描（用不上索引、无法提前停），单次列表查询就要数秒。命中 block 的违禁片名极少，
            // 内存里对当页那几条跑一次 DFA 成本可忽略。
        }
        boolean hasKeyword = StringUtils.hasText(keyword);
        if (hasKeyword) {
            MediaSearchSupport.applyKeyword(w, keyword.trim());
        }
        if (yearFrom != null) {
            w.ge(Media::getYear, yearFrom);
        }
        if (yearTo != null) {
            w.le(Media::getYear, yearTo);
        }
        if (StringUtils.hasText(genre)) {
            w.like(Media::getGenres, genre.trim());
        }
        if (StringUtils.hasText(country)) {
            // country 存逗号分隔的 ISO 码，用 FIND_IN_SET 精确匹配单个码
            w.apply("FIND_IN_SET({0}, country)", country.trim());
        }
        if (minRating != null) {
            w.ge(Media::getRating, minRating);
        }
        if (hasKeyword) {
            MediaSearchSupport.applySearchOrder(w, keyword.trim(), sort);
        } else {
            applySort(w, sort);
        }
        IPage<Media> p = mediaMapper.selectPage(new Page<>(page, size), w);
        boolean pub = Boolean.TRUE.equals(onlyPublished);
        // 发布/隐藏/上映已下沉到 SQL。标题 block 词在这里对当前页内存过滤（命中极少，通常整库个位数），
        // 因此 total 用 DB 分页数、页内偶尔少一条属可接受偏差——换来的是列表 SQL 不再被上千个 LIKE 拖垮。
        // 简介里的敏感词不隐藏整条，展示时打码。
        List<MediaVO> records = p.getRecords().stream()
                .filter(m -> !pub || !sensitiveWordService.isBlocked(m.getTitle()))
                .map(m -> {
                    if (pub) {
                        MediaPublicVisibility.maskOverview(m, sensitiveWordService);
                    }
                    return MediaVO.from(m);
                })
                .toList();
        return PageResult.of(p.getTotal(), page, size, records);
    }

    /** 统一排序口径。支持升/降序。release_date/rating 为空的条目在降序里自然靠后。 */
    private void applySort(LambdaQueryWrapper<Media> w, String sort) {
        switch (sort == null ? "" : sort) {
            case "new" -> w.orderByDesc(Media::getReleaseDate).orderByDesc(Media::getId);
            case "release_asc" -> w.orderByAsc(Media::getReleaseDate).orderByAsc(Media::getId);
            case "rating" -> w.orderByDesc(Media::getRating).orderByDesc(Media::getId);
            case "rating_asc" -> w.orderByAsc(Media::getRating).orderByAsc(Media::getId);
            case "hot_asc" -> w.orderByAsc(Media::getHot).orderByAsc(Media::getId);
            default -> w.orderByDesc(Media::getHot).orderByDesc(Media::getId);
        }
    }

    /** 后台人工更新（部分字段）。 */
    @Transactional
    public MediaVO updateAdmin(Long id, MediaUpdateRequest req) {
        Media m = mediaMapper.selectById(id);
        if (m == null) {
            throw new BizException("内容不存在");
        }
        if (StringUtils.hasText(req.getTitle())) {
            m.setTitle(req.getTitle().trim());
        }
        if (req.getOverview() != null) {
            m.setOverview(req.getOverview());
        }
        if (req.getPoster() != null) {
            m.setPoster(req.getPoster().isBlank() ? null : req.getPoster().trim());
        }
        if (req.getYear() != null) {
            m.setYear(req.getYear());
        }
        if (req.getPubStatus() != null) {
            m.setPubStatus(req.getPubStatus());
        }
        if (req.getHot() != null) {
            // 后台手工设定的是基线热度；行为分由热度回写 job 在其上叠加，故同步写 hot_seed。
            m.setHot(req.getHot());
            m.setHotSeed(req.getHot());
        }
        if (req.getTier() != null) {
            m.setTier(req.getTier());
        }
        if (req.getSearchHidden() != null) {
            m.setSearchHidden(req.getSearchHidden() != 0 ? 1 : 0);
        }
        if (req.getTmdbId() != null) {
            assertExternalIdAvailable(m.getId(), m.getType(), req.getTmdbId(), null);
            m.setTmdbId(req.getTmdbId());
        }
        if (req.getDoubanId() != null) {
            String db = req.getDoubanId().trim();
            if (StringUtils.hasText(db)) {
                assertExternalIdAvailable(m.getId(), m.getType(), null, db);
                m.setDoubanId(db);
            } else {
                m.setDoubanId(null);
            }
        }
        m.setUpdatedAt(LocalDateTime.now());
        mediaMapper.updateById(m);
        bumpSearchCache();
        return MediaVO.from(m);
    }

    private void bumpSearchCache() {
        searchCacheService.invalidateAll();
    }

    // ---------------- 内容自动同步支撑（content 域内部复用） ----------------

    /** 是否已存在该 TMDB 条目（定时拉新去重用）。 */
    public boolean existsByTmdb(int tmdbId, String type) {
        return mediaMapper.selectCount(Wrappers.<Media>lambdaQuery()
                .eq(Media::getTmdbId, tmdbId)
                .eq(StringUtils.hasText(type), Media::getType, type)) > 0;
    }

    /**
     * 取「连载中」剧集 id，供定时刷新集数：最久未更新的优先，
     * 跳过近 {@code minIntervalHours} 小时内刚刷过的，避免重复打 TMDB。
     */
    public List<Long> listAiringForRefresh(int limit, int minIntervalHours) {
        int cap = Math.max(1, Math.min(500, limit));
        LocalDateTime staleBefore = LocalDateTime.now().minusHours(Math.max(0, minIntervalHours));
        return mediaMapper.selectList(Wrappers.<Media>lambdaQuery()
                        .eq(Media::getInProduction, true)
                        .in(Media::getType, List.of("tv", "anime", "variety"))
                        .and(q -> q.isNull(Media::getUpdatedAt).or().lt(Media::getUpdatedAt, staleBefore))
                        .orderByAsc(Media::getUpdatedAt)
                        .last("limit " + cap))
                .stream().map(Media::getId).toList();
    }

    /**
     * 自动榜单取数：返回符合口径的已发布、前台可见 media id。
     * kind：airing 正在热播 / new 最新上映 / top 高分推荐 / hot 最热。
     */
    public List<Long> listBoardIds(String kind, int limit) {
        int cap = Math.max(1, Math.min(100, limit));
        String today = LocalDate.now().toString();
        LambdaQueryWrapper<Media> w = Wrappers.<Media>lambdaQuery()
                .eq(Media::getPubStatus, 1)
                .and(q -> q.isNull(Media::getSearchHidden).or().eq(Media::getSearchHidden, 0));
        switch (kind == null ? "" : kind) {
            case "airing" -> w.eq(Media::getInProduction, true)
                    .in(Media::getType, List.of("tv", "anime", "variety"))
                    .orderByDesc(Media::getLastAirDate).orderByDesc(Media::getHot);
            case "new" -> w.isNotNull(Media::getReleaseDate).le(Media::getReleaseDate, today)
                    .orderByDesc(Media::getReleaseDate).orderByDesc(Media::getId);
            case "top" -> w.isNotNull(Media::getRating)
                    .and(q -> q.isNull(Media::getReleaseDate).or().le(Media::getReleaseDate, today))
                    .orderByDesc(Media::getRating).orderByDesc(Media::getHot);
            default -> w.orderByDesc(Media::getHot).orderByDesc(Media::getId);
        }
        // 多取一些，敏感词可见性过滤后再截断
        w.last("limit " + (cap * 2));
        return mediaMapper.selectList(w).stream()
                .filter(m -> MediaPublicVisibility.isVisible(m, sensitiveWordService))
                .map(Media::getId)
                .limit(cap)
                .toList();
    }

    /** 批量补列表缩略图（已有 poster、缺 poster_thumb）。 */
    public int backfillPosterThumbs(int limit) {
        return posterMirrorService.backfillThumbs(limit);
    }

    public int backfillSeasonPosters(int limit) {
        return posterMirrorService.backfillSeasonPosters(limit);
    }

    public boolean isPosterStorageReady() {
        return posterMirrorService.isEnabled();
    }

    /** 后台仪表盘统计。 */
    public AdminDashboardVO dashboardStats() {
        long total = mediaMapper.selectCount(Wrappers.lambdaQuery());
        long published = mediaMapper.selectCount(
                Wrappers.<Media>lambdaQuery().eq(Media::getPubStatus, 1));
        long draft = mediaMapper.selectCount(
                Wrappers.<Media>lambdaQuery().eq(Media::getPubStatus, 0));
        long offline = mediaMapper.selectCount(
                Wrappers.<Media>lambdaQuery().eq(Media::getPubStatus, 2));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String type : List.of("movie", "tv", "anime", "variety")) {
            byType.put(type, mediaMapper.selectCount(
                    Wrappers.<Media>lambdaQuery().eq(Media::getType, type)));
        }
        return AdminDashboardVO.builder()
                .total(total)
                .published(published)
                .draft(draft)
                .offline(offline)
                .byType(byType)
                .r2Ready(posterMirrorService.isEnabled())
                .build();
    }

    // ---------------- 内部 ----------------

    /** 按外部 id 查已存在则更新元数据，否则新建。 */
    private Media upsert(FetchedMetadata meta, boolean publish) {
        boolean allowPublish = publish && passSensitiveGate(meta.getTitle());
        Media existing = findExisting(meta);
        if (existing != null) {
            applyMeta(existing, meta);
            seedHotIfEmpty(existing, meta);
            tryResolveComplementaryDoubanId(existing);
            if (allowPublish && (existing.getPubStatus() == null || existing.getPubStatus() == 0)) {
                existing.setPubStatus(1);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            mediaMapper.updateById(existing);
            syncSeasonsIfPresent(existing.getId(), meta);
            posterMirrorService.mirrorMediaImages(existing);
            bumpSearchCache();
            return existing;
        }
        Media m = new Media();
        applyMeta(m, meta);
        seedHotIfEmpty(m, meta);
        tryResolveComplementaryDoubanId(m);
        if (m.getHot() == null) {
            m.setHot(0);
        }
        if (m.getHotSeed() == null) {
            m.setHotSeed(m.getHot());
        }
        m.setTier(0);
        m.setPubStatus(allowPublish ? 1 : 0);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        mediaMapper.insert(m);
        syncSeasonsIfPresent(m.getId(), meta);
        posterMirrorService.mirrorMediaImages(m);
        bumpSearchCache();
        return m;
    }

    private void syncSeasonsIfPresent(Long mediaId, FetchedMetadata meta) {
        if (meta.getSeasons() != null && !meta.getSeasons().isEmpty()) {
            mediaSeasonService.syncFromFetched(mediaId, meta.getSeasons());
            posterMirrorService.mirrorSeasonPostersForMedia(mediaId, meta.getSource());
        }
    }

    /**
     * 展示发布门槛：仅当<b>标题</b>命中敏感词(block) → 不予自动发布（留草稿待人工处理）。
     * 简介命中不拦（口径同前台可见性：简介只打码不隐藏），避免正常影视因剧情简介被卡成草稿。
     *
     * @return true 允许发布；false 标题命中 block，应留草稿
     */
    private boolean passSensitiveGate(String title) {
        if (!StringUtils.hasText(title)) {
            return true;
        }
        if (sensitiveWordService.isBlocked(title)) {
            log.warn("标题命中敏感词(block)，不自动发布：{}", title);
            return false;
        }
        return true;
    }

    private Media findExisting(FetchedMetadata meta) {
        if (meta.getTmdbId() != null) {
            Media byTmdb = mediaMapper.selectOne(Wrappers.<Media>lambdaQuery()
                    .eq(Media::getTmdbId, meta.getTmdbId())
                    .eq(Media::getType, meta.getType())
                    .last("limit 1"));
            if (byTmdb != null) {
                return byTmdb;
            }
        }
        if (StringUtils.hasText(meta.getDoubanId())) {
            return mediaMapper.selectOne(Wrappers.<Media>lambdaQuery()
                    .eq(Media::getDoubanId, meta.getDoubanId())
                    .last("limit 1"));
        }
        return null;
    }

    /**
     * 录入 TMDB 条目时，经 IMDb 反查豆瓣 id 并写入（不覆盖元数据）。
     * 若豆瓣 id 已被其它 media 占用则跳过，避免 uk 冲突。
     */
    private void tryResolveComplementaryDoubanId(Media m) {
        if (m.getTmdbId() == null || StringUtils.hasText(m.getDoubanId())) {
            return;
        }
        if (!doubanClient.isEnabled() || !tmdbClient.isConfigured()) {
            return;
        }
        String imdb = tmdbClient.fetchImdbId(m.getTmdbId(), m.getType());
        if (!StringUtils.hasText(imdb)) {
            return;
        }
        String doubanId = doubanClient.resolveDoubanIdByImdb(imdb);
        if (!StringUtils.hasText(doubanId)) {
            return;
        }
        try {
            assertExternalIdAvailable(m.getId(), m.getType(), null, doubanId);
            m.setDoubanId(doubanId);
            log.info("自动关联豆瓣 id：media={} tmdb={} douban={}", m.getId(), m.getTmdbId(), doubanId);
        } catch (BizException e) {
            log.info("自动关联豆瓣 id 跳过：media={} douban={} 原因={}", m.getId(), doubanId, e.getMessage());
        }
    }

    private void assertExternalIdAvailable(Long mediaId, String type, Integer tmdbId, String doubanId) {
        if (tmdbId != null && StringUtils.hasText(type)) {
            Media taken = mediaMapper.selectOne(Wrappers.<Media>lambdaQuery()
                    .eq(Media::getTmdbId, tmdbId)
                    .eq(Media::getType, type)
                    .ne(mediaId != null, Media::getId, mediaId)
                    .last("LIMIT 1"));
            if (taken != null) {
                throw new BizException("TMDB id 已被条目「" + taken.getTitle() + "」(id=" + taken.getId() + ") 占用");
            }
        }
        if (StringUtils.hasText(doubanId)) {
            Media taken = mediaMapper.selectOne(Wrappers.<Media>lambdaQuery()
                    .eq(Media::getDoubanId, doubanId)
                    .ne(mediaId != null, Media::getId, mediaId)
                    .last("LIMIT 1"));
            if (taken != null) {
                throw new BizException("豆瓣 id 已被条目「" + taken.getTitle() + "」(id=" + taken.getId() + ") 占用");
            }
        }
    }

    /** 把抓取结果覆盖到实体（只覆盖非空字段，保留人工已填的）。 */
    private void applyMeta(Media m, FetchedMetadata meta) {
        m.setMetaSource(meta.getSource());
        if (meta.getTmdbId() != null) {
            m.setTmdbId(meta.getTmdbId());
        }
        if (StringUtils.hasText(meta.getDoubanId())) {
            m.setDoubanId(meta.getDoubanId());
        }
        if (StringUtils.hasText(meta.getType())) {
            m.setType(meta.getType());
        }
        if (StringUtils.hasText(meta.getTitle())) {
            m.setTitle(meta.getTitle());
        }
        if (StringUtils.hasText(meta.getOriginalTitle())) {
            m.setOriginalTitle(meta.getOriginalTitle());
        }
        if (meta.getYear() != null) {
            m.setYear(meta.getYear());
        }
        if (StringUtils.hasText(meta.getPoster()) && !posterMirrorService.isOwnUrl(m.getPoster())) {
            m.setPoster(meta.getPoster());
        }
        if (StringUtils.hasText(meta.getBackdrop()) && !posterMirrorService.isOwnUrl(m.getBackdrop())) {
            m.setBackdrop(meta.getBackdrop());
        }
        if (meta.getRating() != null) {
            m.setRating(meta.getRating());
        }
        if (StringUtils.hasText(meta.getOverview())) {
            m.setOverview(meta.getOverview());
        }
        if (StringUtils.hasText(meta.getGenres())) {
            m.setGenres(meta.getGenres());
        }
        if (StringUtils.hasText(meta.getCountry())) {
            m.setCountry(meta.getCountry());
        }
        if (StringUtils.hasText(meta.getActors())) {
            m.setActors(meta.getActors());
        }
        if (StringUtils.hasText(meta.getDirectors())) {
            m.setDirectors(meta.getDirectors());
        }
        if (StringUtils.hasText(meta.getReleaseDate())) {
            m.setReleaseDate(meta.getReleaseDate());
        }
        if (meta.getEpisodeCount() != null) {
            m.setEpisodeCount(meta.getEpisodeCount());
        }
        if (meta.getSeasonCount() != null) {
            m.setSeasonCount(meta.getSeasonCount());
        }
        if (StringUtils.hasText(meta.getSeriesStatus())) {
            m.setSeriesStatus(meta.getSeriesStatus());
        }
        if (meta.getInProduction() != null) {
            m.setInProduction(meta.getInProduction());
        }
        if (StringUtils.hasText(meta.getLastAirDate())) {
            m.setLastAirDate(meta.getLastAirDate());
        }
        if (meta.getLastSeasonNumber() != null) {
            m.setLastSeasonNumber(meta.getLastSeasonNumber());
        }
        if (meta.getLastEpisodeNumber() != null) {
            m.setLastEpisodeNumber(meta.getLastEpisodeNumber());
        }
        if (m.getTitle() == null) {
            throw new BizException("抓取结果缺少标题");
        }
    }

    /**
     * 仅当 hot 为空/0 时用 TMDB popularity 做种子。
     * 这样新片有冷启动热度，而已有热度（如老库搜索回填、后续行为分）刷新时不会被覆盖。
     */
    private void seedHotIfEmpty(Media m, FetchedMetadata meta) {
        if ((m.getHot() == null || m.getHot() == 0) && meta.getPopularity() != null) {
            m.setHot(meta.getPopularity());
            m.setHotSeed(meta.getPopularity());
        }
    }
}
