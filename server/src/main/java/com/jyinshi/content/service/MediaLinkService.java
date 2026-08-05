package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.content.dto.MediaLinkAdminVO;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 网盘链接：转存源链解析、失效标记、后台检索。前台展示走 SSE 搜索，不再按片拉链接列表。 */
@Slf4j
@Service
public class MediaLinkService {

    private static final Map<String, String> PAN_LABELS = Map.ofEntries(
            Map.entry("magnet", "磁力"),
            Map.entry("baidu", "百度"),
            Map.entry("quark", "夸克"),
            Map.entry("xunlei", "迅雷"),
            Map.entry("uc", "UC"),
            Map.entry("aliyun", "阿里"),
            Map.entry("tianyi", "天翼"),
            Map.entry("mobile", "移动"),
            Map.entry("115", "115"),
            Map.entry("123", "123"),
            Map.entry("pikpak", "PikPak"),
            Map.entry("ed2k", "电驴")
    );

    private final MediaLinkMapper mediaLinkMapper;
    private final MediaMapper mediaMapper;
    private final InvalidShareService invalidShareService;

    public MediaLinkService(MediaLinkMapper mediaLinkMapper, MediaMapper mediaMapper,
                            InvalidShareService invalidShareService) {
        this.mediaLinkMapper = mediaLinkMapper;
        this.mediaMapper = mediaMapper;
        this.invalidShareService = invalidShareService;
    }

    /**
     * 转存源链解析结果：panType（库里权威值）+ 规范化后的源分享链 + 提取码 + source（链接来源）。
     *
     * <p>{@code source='self'} 表示自营链/站长精选。注意：{@code shareUrl} 来自 {@code media_link.url}，
     * 可能被采集脏写；调用方（transfer）对 self 必须再以 monitor.my_share_url 为准，绝不回退上游。
     */
    public record TransferSource(String panType, String shareUrl, String password, String source) {
    }

    /**
     * 供 transfer 域按 media_link.id 解析源分享链（前端不再传源链，杜绝源链外泄与借账号滥用）。
     * 仅返回 approved 且未失效的链接。
     */
    public TransferSource resolveTransferSource(Long linkId) {
        if (linkId == null) {
            throw new BizException("链接不存在");
        }
        MediaLink row = mediaLinkMapper.selectById(linkId);
        if (row == null || Integer.valueOf(1).equals(row.getInvalid())
                || !"approved".equals(row.getStatus())) {
            throw new BizException("链接不存在或已失效");
        }
        String url = MediaLinkUrlNormalizer.normalize(row.getUrl(), row.getPanType());
        return new TransferSource(row.getPanType(), url, extractPwd(url), row.getSource());
    }

    private static final java.util.regex.Pattern PWD_QUERY =
            java.util.regex.Pattern.compile("[?&]pwd=([^\\s&#]+)", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** 从规范化后的链接里提取提取码（转存网盘归一化时已折进 ?pwd=）。 */
    private static String extractPwd(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        java.util.regex.Matcher m = PWD_QUERY.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 转存首转确定因链接/分享失效失败时，标记对应 media_link 为 invalid。
     *
     * @param mediaLinkId 优先按 id 精确匹配
     * @param panType     与 shareId 组合兜底；也会批量标记同 share 的全库引用
     * @param shareUrl    原始分享链接；share_id 由 content 域自己的规则算，保证与入库存的一致
     */
    public void markInvalidFromTransferFailure(Long mediaLinkId, String panType, String shareUrl,
                                               String summary) {
        String pan = StringUtils.hasText(panType) ? panType.toLowerCase() : null;
        // 用 content 自己的 ShareIdExtractor（与 upsert 入库同一套规则），避免跨域算法差异漏标同 share 的其它引用
        String shareId = StringUtils.hasText(shareUrl) ? ShareIdExtractor.extract(shareUrl, pan) : null;
        String reason = truncateSummary(summary);
        LocalDateTime now = LocalDateTime.now();
        int marked = 0;

        if (mediaLinkId != null) {
            MediaLink row = mediaLinkMapper.selectById(mediaLinkId);
            if (row != null && !Integer.valueOf(1).equals(row.getInvalid())) {
                applyInvalid(row, reason, now);
                marked++;
            }
        }
        if (StringUtils.hasText(pan) && StringUtils.hasText(shareId)) {
            List<MediaLink> sameShare = mediaLinkMapper.selectList(
                    Wrappers.<MediaLink>lambdaQuery()
                            .eq(MediaLink::getPanType, pan)
                            .eq(MediaLink::getShareId, shareId)
                            .eq(MediaLink::getInvalid, 0));
            for (MediaLink row : sameShare) {
                if (mediaLinkId != null && mediaLinkId.equals(row.getId())) {
                    continue;
                }
                applyInvalid(row, reason, now);
                marked++;
            }
        }
        // 回写全局失效黑名单：以后采集入库前就能直接过滤掉这个 share，不用再等转存踩雷。
        if (StringUtils.hasText(pan) && StringUtils.hasText(shareId)) {
            invalidShareService.mark(pan, shareId, null, reason);
        }
        if (marked > 0) {
            log.info("[content] 转存失败标记链接失效 pan={}, shareId={}, mediaLinkId={}, count={}, reason={}",
                    pan, shareId, mediaLinkId, marked, reason);
        }
    }

    private void applyInvalid(MediaLink row, String summary, LocalDateTime now) {
        row.setInvalid(1);
        row.setCheckState("bad");
        row.setCheckSummary(summary);
        row.setCheckedAt(now);
        mediaLinkMapper.updateById(row);
    }

    private static String truncateSummary(String s) {
        if (!StringUtils.hasText(s)) {
            return "转存失败：链接可能已失效";
        }
        String t = s.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }

    /**
     * 后台链接搜索（分页）。至少需提供关键词或一项筛选，避免无条件下扫全表。
     */
    public PageResult<MediaLinkAdminVO> pageAdmin(long page, long size, String keyword,
                                                    String panType, String source,
                                                    Integer invalid, Long mediaId) {
        if (!hasSearchCriteria(keyword, panType, source, invalid, mediaId)) {
            throw new BizException("请输入关键词或选择筛选条件");
        }
        long p = Math.max(1, page);
        long s = Math.max(1, Math.min(100, size));

        LambdaQueryWrapper<MediaLink> w = Wrappers.<MediaLink>lambdaQuery();
        if (mediaId != null) {
            w.eq(MediaLink::getMediaId, mediaId);
        }
        if (StringUtils.hasText(panType)) {
            w.eq(MediaLink::getPanType, panType.trim());
        }
        if (StringUtils.hasText(source)) {
            w.eq(MediaLink::getSource, source.trim());
        }
        if (invalid != null) {
            w.eq(MediaLink::getInvalid, invalid);
        }
        if (StringUtils.hasText(keyword)) {
            String q = keyword.trim();
            String like = "%" + q + "%";
            w.and(qw -> qw.like(MediaLink::getNote, q)
                    .or().like(MediaLink::getUrl, q)
                    .or().apply("media_id IN (SELECT id FROM media WHERE deleted = 0"
                            + " AND (title LIKE {0} OR original_title LIKE {0}))", like));
        }
        w.orderByDesc(MediaLink::getUpdatedAt).orderByDesc(MediaLink::getId);

        IPage<MediaLink> result = mediaLinkMapper.selectPage(new Page<>(p, s), w);
        Map<Long, String> titles = loadMediaTitles(result.getRecords());
        List<MediaLinkAdminVO> records = result.getRecords().stream()
                .map(row -> MediaLinkAdminVO.from(row,
                        titles.get(row.getMediaId()),
                        panLabel(row.getPanType())))
                .toList();
        return PageResult.of(result.getTotal(), p, s, records);
    }

    private static boolean hasSearchCriteria(String keyword, String panType, String source,
                                             Integer invalid, Long mediaId) {
        return mediaId != null
                || invalid != null
                || StringUtils.hasText(keyword)
                || StringUtils.hasText(panType)
                || StringUtils.hasText(source);
    }

    private Map<Long, String> loadMediaTitles(List<MediaLink> rows) {
        List<Long> ids = rows.stream()
                .map(MediaLink::getMediaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mediaMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Media::getId, Media::getTitle, (a, b) -> a));
    }

    static String panLabel(String panType) {
        if (!StringUtils.hasText(panType)) {
            return "其他";
        }
        // 未知盘型统一归「其他」，避免把 worker 不认识的 code 透传到前端导致错分/错转
        return PAN_LABELS.getOrDefault(panType.toLowerCase(), "其他");
    }
}
