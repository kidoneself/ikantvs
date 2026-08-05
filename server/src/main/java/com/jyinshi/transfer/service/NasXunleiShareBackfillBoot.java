package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.ingest.ShareIdExtractor;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.transfer.entity.NasLanding;
import com.jyinshi.transfer.event.NasXunleiShareReadyEvent;
import com.jyinshi.transfer.mapper.NasLandingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 启动后检查：有 NAS 迅雷分享但尚无对应自营链的，补发就绪事件（幂等 upsert）。
 */
@Slf4j
@Component
public class NasXunleiShareBackfillBoot {

    private static final long DELAY_MS = 12_000L;

    private final NasLandingMapper landingMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final ApplicationEventPublisher events;
    private final TaskScheduler taskScheduler;

    public NasXunleiShareBackfillBoot(NasLandingMapper landingMapper,
                                     MediaLinkMapper mediaLinkMapper,
                                     ApplicationEventPublisher events,
                                     TaskScheduler taskScheduler) {
        this.landingMapper = landingMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.events = events;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1100)
    public void onReady() {
        taskScheduler.schedule(this::run, Instant.now().plusMillis(DELAY_MS));
    }

    private void run() {
        List<NasLanding> rows = landingMapper.selectList(new LambdaQueryWrapper<NasLanding>()
                .isNotNull(NasLanding::getXunleiShareUrl)
                .ne(NasLanding::getXunleiShareUrl, ""));
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int sent = 0;
        for (NasLanding row : rows) {
            if (row.getMediaId() == null || !StringUtils.hasText(row.getXunleiShareUrl())) {
                continue;
            }
            String shareUrl = row.getXunleiShareUrl();
            String shareId = ShareIdExtractor.extract(shareUrl, "xunlei");
            Long cnt = mediaLinkMapper.selectCount(new LambdaQueryWrapper<MediaLink>()
                    .eq(MediaLink::getMediaId, row.getMediaId())
                    .eq(MediaLink::getPanType, "xunlei")
                    .eq(MediaLink::getSource, "self")
                    .and(w -> w.eq(MediaLink::getShareId, shareId).or().eq(MediaLink::getUrl, shareUrl)));
            if (cnt != null && cnt > 0) {
                continue;
            }
            events.publishEvent(new NasXunleiShareReadyEvent(row.getMediaId(), shareUrl));
            sent++;
        }
        if (sent > 0) {
            log.info("[NAS] 迅雷自营链补发就绪事件 missing={}", sent);
        }
    }
}
