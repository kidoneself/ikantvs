package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.DailyFeedItemVO;
import com.jyinshi.content.dto.DailyItemVO;
import com.jyinshi.content.dto.DailyLinkInput;
import com.jyinshi.content.dto.DailyMonitorVO;
import com.jyinshi.content.dto.DailyPatchRequest;
import com.jyinshi.content.dto.DailySaveRequest;
import com.jyinshi.content.dto.MediaVO;
import com.jyinshi.content.entity.DailyUpdate;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.mapper.DailyUpdateMapper;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.transfer.dto.MonitorLinkView;
import com.jyinshi.transfer.service.TransferMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 每日更新（content 域 · 策展看板）。
 *
 * <p>薄策展层：daily_update 只存「哪部剧 + 排序/置顶/上架/完结」。
 * 上游链在 transfer_monitor.share_url；自营展示链 media_link(source=self).url
 * 只存我方稳定分享，未首转前为空，绝不写上游。
 * 追更状态在 transfer 域；保存时写锚点并调 transfer 启用追更；
 * 展示时按 media_link id 向 transfer 聚合我方链/最新集数。跨域只走 transfer 的 service。</p>
 */
@Slf4j
@Service
public class DailyUpdateService {

    private static final String SELF = "self";
    private static final List<String> PAN_TYPES = List.of("quark", "baidu", "xunlei");

    private final DailyUpdateMapper dailyMapper;
    private final MediaMapper mediaMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final TransferMonitorService monitorService;

    public DailyUpdateService(DailyUpdateMapper dailyMapper, MediaMapper mediaMapper,
                              MediaLinkMapper mediaLinkMapper, TransferMonitorService monitorService) {
        this.dailyMapper = dailyMapper;
        this.mediaMapper = mediaMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.monitorService = monitorService;
    }

    // ---------------- 前台（公开） ----------------

    /**
     * 首页「已更新」：只返回后台录入的每日更新（daily_update），不按「新链出现」自动补位。
     * 排序：置顶 → 真实追更时间倒序。
     */
    public PageResult<DailyFeedItemVO> publicFeed(long page, long size) {
        long p = Math.max(1, page);
        long s = Math.max(1, Math.min(50, size));
        int from = (int) ((p - 1) * s);
        int to = from + (int) s;

        List<DailyFeedItemVO> curated = curatedFeed();
        int c = curated.size();
        List<DailyFeedItemVO> out = new ArrayList<>();
        for (int i = from; i < Math.min(to, c); i++) {
            out.add(curated.get(i));
        }
        return PageResult.of(c, p, s, out);
    }

    /** 取全部上架策展项，按 置顶 → 真实更新时间 倒序（看板体量有限，全量取出内存排序）。 */
    private List<DailyFeedItemVO> curatedFeed() {
        List<DailyUpdate> enabled = dailyMapper.selectList(
                Wrappers.<DailyUpdate>lambdaQuery().eq(DailyUpdate::getEnabled, 1));
        if (enabled.isEmpty()) {
            return new ArrayList<>();
        }
        List<DailyItemVO> infos = buildVOs(enabled);   // 与 enabled 同序
        List<Long> mediaIds = enabled.stream().map(DailyUpdate::getMediaId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Media> mediaById = new LinkedHashMap<>();
        for (Media m : mediaMapper.selectBatchIds(mediaIds)) {
            mediaById.put(m.getId(), m);
        }

        record Holder(int pinned, int sort, LocalDateTime time, DailyFeedItemVO vo) {
        }
        List<Holder> holders = new ArrayList<>();
        for (int i = 0; i < enabled.size(); i++) {
            DailyUpdate d = enabled.get(i);
            DailyItemVO info = infos.get(i);
            Media m = mediaById.get(d.getMediaId());
            if (m == null) {
                continue;   // 绑定的剧被删/不存在
            }
            LocalDateTime upAt = info.getLastUpdateAt() != null ? info.getLastUpdateAt() : d.getUpdatedAt();
            DailyFeedItemVO vo = new DailyFeedItemVO();
            MediaVO mv = MediaVO.from(m);
            mv.setUpdatedAt(upAt);   // 卡片展示的更新时间统一用真实更新时间
            vo.setMedia(mv);
            vo.setUpdateNote(info.getLatestEpisode());
            vo.setUpdatedAt(upAt);
            vo.setCurated(true);
            holders.add(new Holder(nz(d.getPinned()), nz(d.getSort()), upAt, vo));
        }
        holders.sort(Comparator
                .<Holder>comparingInt(Holder::pinned).reversed()
                .thenComparing(Holder::time, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.<Holder>comparingInt(Holder::sort).reversed()));
        return new ArrayList<>(holders.stream().map(Holder::vo).toList());
    }

    // ---------------- 后台 ----------------

    /** 分页（可按剧名搜；ended：null=全部，0=未完结，1=已完结）。 */
    public PageResult<DailyItemVO> adminPage(long page, long size, String keyword, Integer ended) {
        long p = Math.max(1, page);
        long s = Math.max(1, Math.min(100, size));
        var qw = Wrappers.<DailyUpdate>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            qw.apply("media_id IN (SELECT id FROM media WHERE deleted = 0"
                    + " AND (title LIKE {0} OR original_title LIKE {0}))", like);
        }
        if (ended != null) {
            if (ended == 0) {
                qw.and(w -> w.eq(DailyUpdate::getEnded, 0).or().isNull(DailyUpdate::getEnded));
            } else {
                qw.eq(DailyUpdate::getEnded, 1);
            }
        }
        // 后台列表：未完结在前、已完结沉底；同组内按最近编辑/更新倒序。
        qw.last("ORDER BY IFNULL(ended,0) ASC, updated_at DESC, id DESC");
        Page<DailyUpdate> res = dailyMapper.selectPage(new Page<>(p, s), qw);
        return PageResult.of(res.getTotal(), p, s, buildVOs(res.getRecords()));
    }

    /** 详情（含各盘追更链，编辑回填用）。 */
    public DailyItemVO adminGet(Long id) {
        DailyUpdate d = require(id);
        return buildVOs(List.of(d)).get(0);
    }

    @Transactional
    public DailyItemVO save(DailySaveRequest req) {
        if (req.getMediaId() == null) {
            throw new BizException("请先选择要绑定的剧");
        }
        Media media = mediaMapper.selectById(req.getMediaId());
        if (media == null) {
            throw new BizException("绑定的剧不存在");
        }
        List<DailyLinkInput> links = normalizeLinks(req.getLinks());
        if (links.isEmpty()) {
            throw new BizException("至少填写一条上游分享链");
        }

        // 1. upsert daily_update（一部剧至多一条）
        DailyUpdate d = req.getId() != null ? require(req.getId())
                : dailyMapper.selectOne(Wrappers.<DailyUpdate>lambdaQuery()
                        .eq(DailyUpdate::getMediaId, req.getMediaId()).last("limit 1"));
        boolean isNew = (d == null);
        if (isNew) {
            d = new DailyUpdate();
            d.setMediaId(req.getMediaId());
            d.setEnded(0);
            d.setCreatedAt(LocalDateTime.now());
        }
        d.setMediaId(req.getMediaId());
        d.setPinned(nz(req.getPinned()));
        d.setSort(nz(req.getSort()));
        d.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        d.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            dailyMapper.insert(d);
        } else {
            dailyMapper.updateById(d);
        }

        // 2. reconcile 自营链 + 追更监控（含每剧追更节奏）
        syncSelfLinks(media, links, req.getCheckDays(), req.getCheckHours(), req.getCheckInterval());
        // 已完结的剧：保存会走 enable 把监控重新拉起，再按完结态压回去，避免误恢复巡检
        if (nz(d.getEnded()) == 1) {
            for (MediaLink link : selfLinksOf(List.of(d.getMediaId()))) {
                monitorService.pauseByMediaLink(link.getId());
            }
        }
        return adminGet(d.getId());
    }

    /** 立即检查：给该剧所有自营链各入队一轮 probe（无视时段），返回入队条数。 */
    public int triggerCheck(Long id) {
        DailyUpdate d = require(id);
        if (nz(d.getEnded()) == 1) {
            throw new BizException("该剧已完结，无需检查；取消完结后再追更");
        }
        int n = 0;
        for (MediaLink link : selfLinksOf(List.of(d.getMediaId()))) {
            if (monitorService.probeByMediaLink(link.getId())) {
                n++;
            }
        }
        return n;
    }

    @Transactional
    public DailyItemVO patch(Long id, DailyPatchRequest req) {
        DailyUpdate d = require(id);
        if (req.getEnabled() != null) {
            d.setEnabled(req.getEnabled());
        }
        if (req.getPinned() != null) {
            d.setPinned(req.getPinned());
        }
        if (req.getSort() != null) {
            d.setSort(req.getSort());
        }
        if (req.getManualEpisode() != null) {
            String v = req.getManualEpisode().trim();
            d.setManualEpisode(v.isEmpty() ? null : v);
        }
        boolean ending = false;
        boolean unending = false;
        if (req.getEnded() != null) {
            int next = req.getEnded() != 0 ? 1 : 0;
            int prev = nz(d.getEnded());
            if (next != prev) {
                ending = next == 1;
                unending = next == 0;
            }
            d.setEnded(next);
        }
        d.setUpdatedAt(LocalDateTime.now());
        dailyMapper.updateById(d);

        // 完结 ↔ 取消：联动 transfer 停/启巡检，不动夹与我方链
        if (ending) {
            for (MediaLink link : selfLinksOf(List.of(d.getMediaId()))) {
                monitorService.pauseByMediaLink(link.getId());
            }
        } else if (unending) {
            for (MediaLink link : selfLinksOf(List.of(d.getMediaId()))) {
                monitorService.resumeByMediaLink(link.getId());
            }
        }
        return adminGet(id);
    }

    /** 仅移除看板条目 + 停对应追更；不删影视库与已转存文件。 */
    @Transactional
    public void delete(Long id) {
        DailyUpdate d = require(id);
        for (MediaLink link : selfLinksOf(List.of(d.getMediaId()))) {
            monitorService.removeByMediaLink(link.getId());
        }
        dailyMapper.deleteById(id);
    }

    /**
     * 自营锚点首转成功后回调（电影专用）：停该盘继续巡检；各盘都首转完后剧级自动标完结。
     * 电视剧/动漫/综艺不自动完结，仍靠运营点「完结」。
     */
    @Transactional
    public void onMovieFirstSaveReady(Long mediaLinkId) {
        if (mediaLinkId == null) {
            return;
        }
        MediaLink link = mediaLinkMapper.selectById(mediaLinkId);
        if (link == null || !"self".equals(link.getSource()) || link.getMediaId() == null) {
            return;
        }
        Media media = mediaMapper.selectById(link.getMediaId());
        if (media == null || !"movie".equals(media.getType())) {
            return;
        }
        // 本盘首转已成：电影无需再追，停该链巡检
        monitorService.pauseByMediaLink(mediaLinkId);

        DailyUpdate d = dailyMapper.selectOne(Wrappers.<DailyUpdate>lambdaQuery()
                .eq(DailyUpdate::getMediaId, media.getId()).last("limit 1"));
        if (d == null || nz(d.getEnded()) == 1) {
            return;
        }
        List<MediaLink> selfs = selfLinksOf(List.of(media.getId()));
        List<Long> linkIds = selfs.stream().map(MediaLink::getId).toList();
        Map<Long, MonitorLinkView> views = monitorService.viewsByMediaLinkIds(linkIds);
        boolean anyReady = false;
        boolean waiting = false;
        for (MediaLink l : selfs) {
            MonitorLinkView v = views.get(l.getId());
            if (v == null) {
                continue;
            }
            if (StringUtils.hasText(v.getMyShareUrl())) {
                anyReady = true;
            } else if (!"invalid".equals(v.getStatus())) {
                // 还有盘没首转成功、也没判失效 → 等它们
                waiting = true;
            }
        }
        if (!anyReady || waiting) {
            return;
        }
        d.setEnded(1);
        d.setUpdatedAt(LocalDateTime.now());
        dailyMapper.updateById(d);
        for (MediaLink l : selfs) {
            monitorService.pauseByMediaLink(l.getId());
        }
        log.info("[每日更新] 电影首转齐套，自动完结: dailyId={}, mediaId={}, title={}",
                d.getId(), media.getId(), media.getTitle());
    }

    // ---------------- 内部 ----------------

    /**
     * 把本次录入的上游链与该剧现有自营链对齐：
     * 命中(media_id+pan+shareId)则更新并续/建追更；多出的旧自营链删链 + 停追更。
     */
    private void syncSelfLinks(Media media, List<DailyLinkInput> inputs,
                               String checkDays, String checkHours, Integer checkInterval) {
        List<MediaLink> existing = selfLinksOf(List.of(media.getId()));
        Map<String, MediaLink> byKey = new LinkedHashMap<>();
        for (MediaLink l : existing) {
            byKey.put(l.getPanType() + "|" + l.getShareId(), l);
        }
        Set<Long> keep = new HashSet<>();
        String note = media.getTitle();
        for (DailyLinkInput in : inputs) {
            String pan = in.getPanType().toLowerCase();
            String shareId = ShareIdExtractor.extract(in.getShareUrl(), pan);
            String key = pan + "|" + shareId;
            MediaLink link = byKey.remove(key);
            if (link == null) {
                // 该剧可能已被 pansou/gying 采过同一条分享（非自营）。唯一键 uk_link=(media_id,pan_type,share_id)
                // 不含 source，直接 insert 会撞键报「系统异常」。命中则收编为自营，避免重复建链。
                link = mediaLinkMapper.selectOne(Wrappers.<MediaLink>lambdaQuery()
                        .eq(MediaLink::getMediaId, media.getId())
                        .eq(MediaLink::getPanType, pan)
                        .eq(MediaLink::getShareId, shareId)
                        .last("limit 1"));
            }
            if (link == null) {
                link = new MediaLink();
                link.setMediaId(media.getId());
                link.setPanType(pan);
                link.setShareId(shareId);
                link.setSource(SELF);
                link.setStatus("approved");
                link.setInvalid(0);
                // 展示 url 只允许我方链；尚未首转前留空，绝不写上游大佬链
                link.setUrl("");
                link.setNote(note);
                link.setLastSeenAt(LocalDateTime.now());
                link.setCreatedAt(LocalDateTime.now());
                link.setUpdatedAt(LocalDateTime.now());
                mediaLinkMapper.insert(link);
            } else {
                link.setSource(SELF);
                // 勿用上游覆盖展示 url；下面 enable 后只写我方链
                link.setNote(note);
                link.setInvalid(0);
                link.setStatus("approved");
                link.setLastSeenAt(LocalDateTime.now());
                link.setUpdatedAt(LocalDateTime.now());
                mediaLinkMapper.updateById(link);
            }
            keep.add(link.getId());
            // 启用监控转存（账号固定 role=monitor，不选手动号）
            var mon = monitorService.enable(link.getId(), pan, in.getShareUrl(), in.getSharePwd(),
                    null, media.getId());
            // 每剧检查节奏（各盘链共用同一套；空=沿用全局）
            monitorService.updateSchedule(link.getId(), checkDays, checkHours, checkInterval);
            // 铁律：自营展示 url = 我方稳定链；没有就空着，绝不回退上游
            String mine = mon != null ? mon.getMyShareUrl() : null;
            String want = StringUtils.hasText(mine) ? mine : "";
            if (!Objects.equals(want, link.getUrl() == null ? "" : link.getUrl())) {
                link.setUrl(want);
                mediaLinkMapper.updateById(link);
            }
        }
        // 本次没保留的旧自营链：停追更 + 删链。
        // 但「已有我方链且没有追更监控」的不是本看板录入产出（如灌盘落地分享），保留。
        Map<Long, MonitorLinkView> staleViews =
                monitorService.viewsByMediaLinkIds(byKey.values().stream().map(MediaLink::getId).toList());
        for (MediaLink stale : byKey.values()) {
            if (StringUtils.hasText(stale.getUrl()) && !staleViews.containsKey(stale.getId())) {
                continue;
            }
            monitorService.removeByMediaLink(stale.getId());
            mediaLinkMapper.deleteById(stale.getId());
        }
    }

    private List<MediaLink> selfLinksOf(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        return mediaLinkMapper.selectList(Wrappers.<MediaLink>lambdaQuery()
                .in(MediaLink::getMediaId, mediaIds)
                .eq(MediaLink::getSource, SELF));
    }

    /** 批量组装 VO：媒体信息 + 自营链锚点 + transfer 追更态聚合。 */
    private List<DailyItemVO> buildVOs(List<DailyUpdate> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> mediaIds = rows.stream().map(DailyUpdate::getMediaId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Media> mediaById = new LinkedHashMap<>();
        for (Media m : mediaMapper.selectBatchIds(mediaIds)) {
            mediaById.put(m.getId(), m);
        }
        List<MediaLink> selfLinks = selfLinksOf(mediaIds);
        Map<Long, List<MediaLink>> linksByMedia = new LinkedHashMap<>();
        for (MediaLink l : selfLinks) {
            linksByMedia.computeIfAbsent(l.getMediaId(), k -> new ArrayList<>()).add(l);
        }
        List<Long> linkIds = selfLinks.stream().map(MediaLink::getId).toList();
        Map<Long, MonitorLinkView> viewByLink = monitorService.viewsByMediaLinkIds(linkIds);

        List<DailyItemVO> out = new ArrayList<>();
        for (DailyUpdate d : rows) {
            DailyItemVO vo = new DailyItemVO();
            vo.setId(d.getId());
            vo.setMediaId(d.getMediaId());
            vo.setPinned(nz(d.getPinned()));
            vo.setSort(nz(d.getSort()));
            vo.setEnabled(d.getEnabled() != null ? d.getEnabled() : 1);
            vo.setEnded(nz(d.getEnded()));
            Media m = mediaById.get(d.getMediaId());
            if (m != null) {
                vo.setTitle(m.getTitle());
                vo.setPoster(m.getPoster());
                vo.setType(m.getType());
                vo.setYear(m.getYear());
            }
            List<DailyMonitorVO> monitors = new ArrayList<>();
            String latest = null;
            LocalDateTime lastUpdate = null;   // 真正更新时间（last_content_at）
            LocalDateTime lastCheck = null;    // 上次检查时间（last_probe_at）
            boolean anyActive = false;
            boolean anyInvalid = false;
            boolean anyMonitor = false;
            for (MediaLink l : linksByMedia.getOrDefault(d.getMediaId(), List.of())) {
                MonitorLinkView view = viewByLink.get(l.getId());
                DailyMonitorVO mv = new DailyMonitorVO();
                mv.setPanType(l.getPanType());
                if (view != null) {
                    anyMonitor = true;
                    mv.setShareUrl(view.getShareUrl());
                    mv.setAccountName(view.getAccountName());
                    mv.setStatus(view.getStatus());
                    mv.setMyShareUrl(view.getMyShareUrl());
                    String ep = EpisodeExtractor.extractDisplay(view.getLatestEpisode());
                    mv.setLatestEpisode(ep);
                    latest = EpisodeExtractor.pickLatest(latest, ep);
                    if (view.getLastContentAt() != null
                            && (lastUpdate == null || view.getLastContentAt().isAfter(lastUpdate))) {
                        lastUpdate = view.getLastContentAt();
                    }
                    if (view.getLastCheckAt() != null
                            && (lastCheck == null || view.getLastCheckAt().isAfter(lastCheck))) {
                        lastCheck = view.getLastCheckAt();
                    }
                    if ("invalid".equals(view.getStatus())) {
                        anyInvalid = true;
                    } else if ("active".equals(view.getStatus())) {
                        anyActive = true;
                    }
                    // 追更节奏：各盘共用一套，取第一个非空的回填
                    if (vo.getCheckHours() == null && StringUtils.hasText(view.getCheckHours())) {
                        vo.setCheckDays(view.getCheckDays());
                        vo.setCheckHours(view.getCheckHours());
                        vo.setCheckInterval(view.getCheckInterval());
                    }
                } else {
                    // 没有追更监控 = 没有上游源（如百度灌盘产出的迅雷落地分享），url 就是我方链
                    mv.setMyShareUrl(l.getUrl());
                }
                monitors.add(mv);
            }
            vo.setMonitors(monitors);
            // 整体状态：完结优先；任一盘源失效优先标出（方便换源），否则 active / paused / none
            if (nz(d.getEnded()) == 1) {
                vo.setStatus("ended");
            } else {
                vo.setStatus(!anyMonitor ? "none" : anyInvalid ? "invalid" : anyActive ? "active" : "paused");
            }
            vo.setLastCheckAt(lastCheck);
            // 展示集数：有手动值则以手动为准，仅当自动聚合值"严格更新"时才盖过手动（保护手动纠正 + 只增不减）
            String manual = d.getManualEpisode();
            vo.setManualEpisode(manual);
            vo.setLatestEpisode(StringUtils.hasText(manual)
                    ? EpisodeExtractor.pickLatest(manual, latest)
                    : latest);
            vo.setLastUpdateAt(lastUpdate);
            out.add(vo);
        }
        return out;
    }

    private List<DailyLinkInput> normalizeLinks(List<DailyLinkInput> links) {
        List<DailyLinkInput> out = new ArrayList<>();
        if (links == null) {
            return out;
        }
        for (DailyLinkInput l : links) {
            if (l == null || !StringUtils.hasText(l.getShareUrl())) {
                continue;
            }
            String pan = StringUtils.hasText(l.getPanType()) ? l.getPanType().trim().toLowerCase() : "quark";
            if (!PAN_TYPES.contains(pan)) {
                throw new BizException("不支持的网盘类型：" + pan);
            }
            DailyLinkInput n = new DailyLinkInput();
            n.setPanType(pan);
            n.setShareUrl(l.getShareUrl().trim());
            n.setSharePwd(StringUtils.hasText(l.getSharePwd()) ? l.getSharePwd().trim() : null);
            out.add(n);
        }
        return out;
    }

    private DailyUpdate require(Long id) {
        DailyUpdate d = id == null ? null : dailyMapper.selectById(id);
        if (d == null) {
            throw new BizException("每日更新条目不存在");
        }
        return d;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
