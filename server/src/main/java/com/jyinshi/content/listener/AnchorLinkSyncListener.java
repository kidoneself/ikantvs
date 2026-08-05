package com.jyinshi.content.listener;

import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.service.DailyUpdateService;
import com.jyinshi.transfer.event.AnchorLinkReadyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * content 域监听 transfer 的「我方链就绪」事件，把自营锚点链的展示 url 回写成我方稳定分享链。
 *
 * <p>追更录入时 {@code media_link.url} 先留空；首转成功后回写我方稳定分享。
 * 前台/转存只认我方链，绝不回退上游。跨域只通过领域事件反向通知，transfer 不直接写 content 的表。</p>
 *
 * <p>电影额外：首转成功后停该盘巡检；各盘都齐了则剧级自动标完结（无需运营手点）。</p>
 */
@Slf4j
@Component
public class AnchorLinkSyncListener {

    private final MediaLinkMapper mediaLinkMapper;
    private final DailyUpdateService dailyUpdateService;

    public AnchorLinkSyncListener(MediaLinkMapper mediaLinkMapper, DailyUpdateService dailyUpdateService) {
        this.mediaLinkMapper = mediaLinkMapper;
        this.dailyUpdateService = dailyUpdateService;
    }

    @EventListener
    public void onAnchorLinkReady(AnchorLinkReadyEvent evt) {
        if (evt.mediaLinkId() == null || !StringUtils.hasText(evt.myShareUrl())) {
            return;
        }
        MediaLink link = mediaLinkMapper.selectById(evt.mediaLinkId());
        // 只回写自营锚点链；非自营链不动
        if (link == null || !"self".equals(link.getSource())) {
            return;
        }
        if (!evt.myShareUrl().equals(link.getUrl())) {
            link.setUrl(evt.myShareUrl());
            link.setInvalid(0);
            link.setUpdatedAt(LocalDateTime.now());
            mediaLinkMapper.updateById(link);
            log.info("[每日更新] 回写我方链: mediaLinkId={}, myShareUrl={}", evt.mediaLinkId(), evt.myShareUrl());
        }
        // 电影：首转成功 → 停巡检；齐套则自动完结
        dailyUpdateService.onMovieFirstSaveReady(evt.mediaLinkId());
    }
}
