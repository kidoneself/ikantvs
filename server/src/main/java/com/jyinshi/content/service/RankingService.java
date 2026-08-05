package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.dto.RankingSaveRequest;
import com.jyinshi.content.dto.RankingVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.Ranking;
import com.jyinshi.content.entity.RankingItem;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.content.mapper.RankingItemMapper;
import com.jyinshi.content.mapper.RankingMapper;
import com.jyinshi.ops.service.SensitiveWordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 榜单（content 域）：策划榜单的查询与运营。
 *
 * <p>榜单 = 一组人工挑选并排序的影视条目。前台展示已上架榜单；后台增删改 + 调序。
 */
@Service
public class RankingService {

    private final RankingMapper rankingMapper;
    private final RankingItemMapper itemMapper;
    private final MediaMapper mediaMapper;
    private final SensitiveWordService sensitiveWordService;

    public RankingService(RankingMapper rankingMapper, RankingItemMapper itemMapper, MediaMapper mediaMapper,
                          SensitiveWordService sensitiveWordService) {
        this.rankingMapper = rankingMapper;
        this.itemMapper = itemMapper;
        this.mediaMapper = mediaMapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    // ---------------- 公开 ----------------

    /** 前台：所有已上架榜单（含条目，仅已发布内容）。 */
    public List<RankingVO> listPublic() {
        List<Ranking> rankings = rankingMapper.selectList(Wrappers.<Ranking>lambdaQuery()
                .eq(Ranking::getEnabled, 1)
                .orderByDesc(Ranking::getSort).orderByAsc(Ranking::getId));
        List<RankingVO> out = new ArrayList<>();
        for (Ranking r : rankings) {
            RankingVO vo = RankingVO.from(r);
            vo.setItems(itemsOf(r.getId(), true));
            vo.setItemCount(vo.getItems().size());
            out.add(vo);
        }
        return out;
    }

    // ---------------- 后台 ----------------

    /** 后台：全部榜单（不含条目，带条目数）。 */
    public List<RankingVO> adminList() {
        List<Ranking> rankings = rankingMapper.selectList(Wrappers.<Ranking>lambdaQuery()
                .orderByDesc(Ranking::getSort).orderByAsc(Ranking::getId));
        List<RankingVO> out = new ArrayList<>();
        for (Ranking r : rankings) {
            RankingVO vo = RankingVO.from(r);
            vo.setItemCount(Math.toIntExact(itemMapper.selectCount(
                    Wrappers.<RankingItem>lambdaQuery().eq(RankingItem::getRankingId, r.getId()))));
            out.add(vo);
        }
        return out;
    }

    /** 后台：榜单详情 + 条目（含未发布，便于运营核对）。 */
    public RankingVO adminGet(Long id) {
        Ranking r = requireRanking(id);
        RankingVO vo = RankingVO.from(r);
        vo.setItems(itemsOf(id, false));
        vo.setItemCount(vo.getItems().size());
        return vo;
    }

    @Transactional
    public RankingVO save(RankingSaveRequest req) {
        String slug = req.getSlug().trim();
        Ranking exist = rankingMapper.selectOne(Wrappers.<Ranking>lambdaQuery()
                .eq(Ranking::getSlug, slug).last("limit 1"));
        if (exist != null && !exist.getId().equals(req.getId())) {
            throw new BizException("slug 已被占用：" + slug);
        }
        Ranking r = req.getId() != null ? requireRanking(req.getId()) : new Ranking();
        r.setName(req.getName().trim());
        r.setSlug(slug);
        r.setDescription(req.getDescription());
        r.setSort(req.getSort() != null ? req.getSort() : 0);
        r.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        r.setUpdatedAt(LocalDateTime.now());
        if (r.getId() == null) {
            r.setCreatedAt(LocalDateTime.now());
            rankingMapper.insert(r);
        } else {
            rankingMapper.updateById(r);
        }
        return adminGet(r.getId());
    }

    @Transactional
    public void delete(Long id) {
        requireRanking(id);
        itemMapper.delete(Wrappers.<RankingItem>lambdaQuery().eq(RankingItem::getRankingId, id));
        rankingMapper.deleteById(id);
    }

    /** 整表替换榜单条目，数组顺序即名次。 */
    @Transactional
    public RankingVO setItems(Long rankingId, List<Long> mediaIds) {
        requireRanking(rankingId);
        itemMapper.delete(Wrappers.<RankingItem>lambdaQuery().eq(RankingItem::getRankingId, rankingId));
        int rank = 0;
        for (Long mediaId : dedup(mediaIds)) {
            RankingItem it = new RankingItem();
            it.setRankingId(rankingId);
            it.setMediaId(mediaId);
            it.setRankNo(rank++);
            it.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(it);
        }
        return adminGet(rankingId);
    }

    /**
     * 自动榜单：按 slug 存在则复用、不存在则新建，随后整表替换条目。
     * 仅首次创建时写 name/description/sort/enabled，之后只刷新条目——
     * 保留运营对该榜「是否上架/名称/排序」的手动调整。
     */
    @Transactional
    public void upsertSystemRanking(String slug, String name, String description, int sort, List<Long> mediaIds) {
        Ranking r = rankingMapper.selectOne(Wrappers.<Ranking>lambdaQuery()
                .eq(Ranking::getSlug, slug).last("limit 1"));
        if (r == null) {
            r = new Ranking();
            r.setSlug(slug);
            r.setName(name);
            r.setDescription(description);
            r.setSort(sort);
            r.setEnabled(1);
            r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            rankingMapper.insert(r);
        } else {
            r.setUpdatedAt(LocalDateTime.now());
            rankingMapper.updateById(r);
        }
        itemMapper.delete(Wrappers.<RankingItem>lambdaQuery().eq(RankingItem::getRankingId, r.getId()));
        int rank = 0;
        for (Long mediaId : dedup(mediaIds)) {
            RankingItem it = new RankingItem();
            it.setRankingId(r.getId());
            it.setMediaId(mediaId);
            it.setRankNo(rank++);
            it.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(it);
        }
    }

    // ---------------- 内部 ----------------

    /** 取榜单条目对应的 MediaVO，按 rank_no 排序。onlyPublished=true 时过滤未发布。 */
    private List<MediaVO> itemsOf(Long rankingId, boolean onlyPublished) {
        List<RankingItem> items = itemMapper.selectList(Wrappers.<RankingItem>lambdaQuery()
                .eq(RankingItem::getRankingId, rankingId)
                .orderByAsc(RankingItem::getRankNo).orderByAsc(RankingItem::getId));
        if (items.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = items.stream().map(RankingItem::getMediaId).toList();
        Map<Long, Media> byId = new LinkedHashMap<>();
        for (Media m : mediaMapper.selectBatchIds(ids)) {
            byId.put(m.getId(), m);
        }
        List<MediaVO> out = new ArrayList<>();
        for (RankingItem it : items) {
            Media m = byId.get(it.getMediaId());
            if (m == null) {
                continue;
            }
            if (onlyPublished) {
                if (!MediaPublicVisibility.isVisible(m, sensitiveWordService)) {
                    continue;
                }
                MediaPublicVisibility.maskOverview(m, sensitiveWordService);
            }
            out.add(MediaVO.from(m));
        }
        return out;
    }

    private Ranking requireRanking(Long id) {
        Ranking r = rankingMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "榜单不存在");
        }
        return r;
    }

    private static List<Long> dedup(List<Long> ids) {
        List<Long> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (Long id : ids) {
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
