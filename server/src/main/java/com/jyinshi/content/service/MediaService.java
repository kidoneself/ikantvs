package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.AdminDashboardVO;
import com.jyinshi.content.dto.ManualMediaRequest;
import com.jyinshi.content.dto.MediaDetailVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容域：影视库查询与手工录入。
 *
 * <p>开源默认元数据来自夸克热榜；后台可手工建条目并上传海报。无 TMDB/豆瓣采集。
 */
@Slf4j
@Service
public class MediaService {

    private final MediaMapper mediaMapper;
    private final PosterMirrorService posterMirrorService;
    private final SensitiveWordService sensitiveWordService;
    private final MediaSeasonService mediaSeasonService;
    private final MediaSearchCacheService searchCacheService;

    public MediaService(MediaMapper mediaMapper,
                        PosterMirrorService posterMirrorService, SensitiveWordService sensitiveWordService,
                        MediaSeasonService mediaSeasonService, MediaSearchCacheService searchCacheService) {
        this.mediaMapper = mediaMapper;
        this.posterMirrorService = posterMirrorService;
        this.sensitiveWordService = sensitiveWordService;
        this.mediaSeasonService = mediaSeasonService;
        this.searchCacheService = searchCacheService;
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
        m.setMetaSource("manual");
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
            m.setTmdbId(req.getTmdbId());
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
}
