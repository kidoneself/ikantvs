package com.jyinshi.content.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.transfer.event.NasXunleiShareReadyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * NAS 迅雷落地分享就绪 → upsert 自营迅雷链，让「只加百度」也能在前台露出迅雷我方链。
 *
 * <p>不建 transfer_monitor（没有上游迅雷源）；展示链直接写 {@code media_link.url}。
 * 若该剧已有自营迅雷锚点（运营加了迅雷上游），优先回填其 url，避免重复建链。</p>
 */
@Slf4j
@Component
public class NasXunleiShareSyncListener {

    private static final String SELF = "self";
    private static final String PAN = "xunlei";

    private final MediaLinkMapper mediaLinkMapper;
    private final MediaMapper mediaMapper;

    public NasXunleiShareSyncListener(MediaLinkMapper mediaLinkMapper, MediaMapper mediaMapper) {
        this.mediaLinkMapper = mediaLinkMapper;
        this.mediaMapper = mediaMapper;
    }

    @EventListener
    public void onNasXunleiShareReady(NasXunleiShareReadyEvent evt) {
        if (evt.mediaId() == null || !StringUtils.hasText(evt.shareUrl())) {
            return;
        }
        Long mediaId = evt.mediaId();
        String shareUrl = evt.shareUrl().trim();
        String shareId = ShareIdExtractor.extract(shareUrl, PAN);

        MediaLink link = mediaLinkMapper.selectOne(Wrappers.<MediaLink>lambdaQuery()
                .eq(MediaLink::getMediaId, mediaId)
                .eq(MediaLink::getPanType, PAN)
                .eq(MediaLink::getShareId, shareId)
                .last("LIMIT 1"));

        if (link == null) {
            List<MediaLink> selves = mediaLinkMapper.selectList(Wrappers.<MediaLink>lambdaQuery()
                    .eq(MediaLink::getMediaId, mediaId)
                    .eq(MediaLink::getPanType, PAN)
                    .eq(MediaLink::getSource, SELF)
                    .orderByAsc(MediaLink::getId));
            if (selves != null && !selves.isEmpty()) {
                link = selves.stream()
                        .filter(l -> shareUrl.equals(l.getUrl()) || shareId.equals(l.getShareId()))
                        .findFirst()
                        .orElseGet(() -> selves.stream()
                                .filter(l -> !StringUtils.hasText(l.getUrl()))
                                .findFirst()
                                .orElse(null));
                // 已有非空且不同的自营迅雷链：运营另有上游，不另插 NAS 链
                if (link == null) {
                    log.debug("[每日更新] NAS 迅雷分享跳过：已有自营迅雷链 mediaId={}", mediaId);
                    return;
                }
            }
        }

        String note = titleOf(mediaId);
        LocalDateTime now = LocalDateTime.now();
        if (link == null) {
            link = new MediaLink();
            link.setMediaId(mediaId);
            link.setPanType(PAN);
            link.setShareId(shareId);
            link.setUrl(shareUrl);
            link.setSource(SELF);
            link.setStatus("approved");
            link.setInvalid(0);
            link.setNote(note);
            link.setLastSeenAt(now);
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            mediaLinkMapper.insert(link);
            log.info("[每日更新] NAS 迅雷自营链新建 mediaId={} mediaLinkId={} url={}",
                    mediaId, link.getId(), shareUrl);
            return;
        }

        boolean dirty = false;
        if (!SELF.equals(link.getSource())) {
            link.setSource(SELF);
            dirty = true;
        }
        if (!shareUrl.equals(link.getUrl())) {
            link.setUrl(shareUrl);
            dirty = true;
        }
        if (!shareId.equals(link.getShareId())) {
            // 空链锚点收编为落地分享 id，避免 uk 撞车时再插一条
            MediaLink clash = mediaLinkMapper.selectOne(Wrappers.<MediaLink>lambdaQuery()
                    .eq(MediaLink::getMediaId, mediaId)
                    .eq(MediaLink::getPanType, PAN)
                    .eq(MediaLink::getShareId, shareId)
                    .ne(MediaLink::getId, link.getId())
                    .last("LIMIT 1"));
            if (clash == null) {
                link.setShareId(shareId);
                dirty = true;
            }
        }
        if (link.getInvalid() == null || link.getInvalid() != 0) {
            link.setInvalid(0);
            dirty = true;
        }
        if (!"approved".equals(link.getStatus())) {
            link.setStatus("approved");
            dirty = true;
        }
        if (dirty) {
            link.setUpdatedAt(now);
            link.setLastSeenAt(now);
            mediaLinkMapper.updateById(link);
            log.info("[每日更新] NAS 迅雷自营链回写 mediaId={} mediaLinkId={} url={}",
                    mediaId, link.getId(), shareUrl);
        }
    }

    private String titleOf(Long mediaId) {
        Media m = mediaMapper.selectById(mediaId);
        return m != null && StringUtils.hasText(m.getTitle()) ? m.getTitle() : ("media-" + mediaId);
    }
}
