package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.transfer.config.TransferProperties;
import com.jyinshi.transfer.entity.NasLanding;
import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.mapper.NasLandingMapper;
import com.jyinshi.transfer.mapper.TransferMonitorMapper;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.account.AccountPool;
import com.jyinshi.transfer.event.NasXunleiShareReadyEvent;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.xunlei.XunleiDriver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 剧级迅雷落地夹：与迅雷监控共用同一夹（禁止再建空夹让千云整部重传）。
 * 基线默认空，由 NasFillService 在落地夹为空时首灌、有货后只追新。
 */
@Slf4j
@Service
public class NasLandingService {

    private final NasLandingMapper landingMapper;
    private final TransferAccountService accountService;
    private final AccountPool accountPool;
    private final XunleiDriver xunleiDriver;
    private final TransferProperties props;
    private final ObjectMapper objectMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final TransferMonitorMapper monitorMapper;
    private final ApplicationEventPublisher events;

    public NasLandingService(NasLandingMapper landingMapper,
                             TransferAccountService accountService,
                             AccountPool accountPool,
                             XunleiDriver xunleiDriver,
                             TransferProperties props,
                             ObjectMapper objectMapper,
                             MediaLinkMapper mediaLinkMapper,
                             TransferMonitorMapper monitorMapper,
                             ApplicationEventPublisher events) {
        this.landingMapper = landingMapper;
        this.accountService = accountService;
        this.accountPool = accountPool;
        this.xunleiDriver = xunleiDriver;
        this.props = props;
        this.objectMapper = objectMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.monitorMapper = monitorMapper;
        this.events = events;
    }

    public NasLanding findByMediaId(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        return landingMapper.selectOne(new LambdaQueryWrapper<NasLanding>()
                .eq(NasLanding::getMediaId, mediaId)
                .last("LIMIT 1"));
    }

    /**
     * 确保该剧有迅雷落地夹。
     * <ul>
     *   <li>已有迅雷监控固定夹 → 复用（与监控转存同一夹，避免千云对着空夹整部上传）</li>
     *   <li>已有 nas_landing 且无监控夹 → 沿用</li>
     *   <li>都没有 → 建夹+分享，基线写空</li>
     * </ul>
     *
     * @param baiduKeys 当前百度固定夹文件名（保留参数供调用方；新建时不再写入基线）
     */
    public NasLanding ensure(Long mediaId, Long sourceMediaLinkId, String title,
                             Collection<String> baiduKeys) {
        if (mediaId == null) {
            return null;
        }
        NasLanding existing = findByMediaId(mediaId);
        TransferMonitor xlMon = findXunleiMonitor(mediaId);

        // 优先绑迅雷监控夹：上游已追到 N 集时，NAS 差集对着它算，不会误首灌
        if (xlMon != null && StringUtils.hasText(xlMon.getTargetFolderId())) {
            return bindOrKeep(existing, mediaId, sourceMediaLinkId,
                    xlMon.getTargetFolderId(), xlMon.getMyShareUrl(), baiduKeys, "迅雷监控夹");
        }

        if (existing != null && StringUtils.hasText(existing.getXunleiFolderId())) {
            if (!StringUtils.hasText(existing.getBaselineJson())) {
                saveBaseline(existing, List.of());
                log.info("[NAS] 补齐空基线 mediaId={} (baiduKeys={})", mediaId,
                        baiduKeys == null ? 0 : baiduKeys.size());
            }
            publishShareReady(mediaId, existing.getXunleiShareUrl());
            return existing;
        }

        String folderName = StringUtils.hasText(title) ? title.trim() : ("media-" + mediaId);
        folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (folderName.length() > 80) {
            folderName = folderName.substring(0, 80);
        }

        TransferAccount xlAcc = accountService.findMonitorAccount("xunlei");
        if (xlAcc == null) {
            log.warn("[NAS] 无迅雷监控号，无法建落地夹 mediaId={}", mediaId);
            return null;
        }
        Account account = accountPool.pickByName(PanType.XUNLEI, xlAcc.getAccountName());
        if (account == null) {
            log.warn("[NAS] 迅雷监控号未装入内存池: {}", xlAcc.getAccountName());
            return null;
        }
        String landingDir = props.getMonitor().getLandingDir();
        String[] created = xunleiDriver.ensureLandingShare(account, landingDir, folderName);
        if (created == null) {
            return null;
        }
        return bindOrKeep(existing, mediaId, sourceMediaLinkId, created[0], created[1], baiduKeys, "新建");
    }

    /** 同剧迅雷监控（有固定夹的优先）。 */
    private TransferMonitor findXunleiMonitor(Long mediaId) {
        List<MediaLink> links = mediaLinkMapper.selectList(new LambdaQueryWrapper<MediaLink>()
                .eq(MediaLink::getMediaId, mediaId)
                .eq(MediaLink::getPanType, "xunlei"));
        if (links == null || links.isEmpty()) {
            return null;
        }
        TransferMonitor best = null;
        for (MediaLink link : links) {
            TransferMonitor m = monitorMapper.selectOne(new LambdaQueryWrapper<TransferMonitor>()
                    .eq(TransferMonitor::getMediaLinkId, link.getId())
                    .last("LIMIT 1"));
            if (m == null || !StringUtils.hasText(m.getTargetFolderId())) {
                continue;
            }
            // 优先已有我方分享的（完整落地）
            if (best == null || StringUtils.hasText(m.getMyShareUrl())) {
                best = m;
            }
        }
        return best;
    }

    private NasLanding bindOrKeep(NasLanding existing, Long mediaId, Long sourceMediaLinkId,
                                  String folderId, String shareUrl,
                                  Collection<String> baiduKeys, String via) {
        boolean same = existing != null
                && folderId.equals(existing.getXunleiFolderId());
        NasLanding row = existing != null ? existing : new NasLanding();
        row.setMediaId(mediaId);
        if (sourceMediaLinkId != null) {
            row.setSourceMediaLinkId(sourceMediaLinkId);
        }
        row.setXunleiFolderId(folderId);
        if (StringUtils.hasText(shareUrl)) {
            row.setXunleiShareUrl(shareUrl);
        }
        if (!StringUtils.hasText(row.getBaselineJson())) {
            row.setBaselineJson("[]");
        }
        row.setUpdatedAt(LocalDateTime.now());
        if (row.getId() == null) {
            row.setCreatedAt(LocalDateTime.now());
            landingMapper.insert(row);
            log.info("[NAS] 落地夹就绪 mediaId={} folder={} via={} baseline=empty (baiduKeys={})",
                    mediaId, folderId, via, baiduKeys == null ? 0 : baiduKeys.size());
        } else if (!same) {
            landingMapper.updateById(row);
            log.info("[NAS] 落地夹改绑 mediaId={} folder={} via={} (原夹换掉，避免对着空夹千云重传)",
                    mediaId, folderId, via);
        } else {
            landingMapper.updateById(row);
        }
        publishShareReady(mediaId, row.getXunleiShareUrl());
        return row;
    }

    private void publishShareReady(Long mediaId, String shareUrl) {
        if (mediaId == null || !StringUtils.hasText(shareUrl)) {
            return;
        }
        events.publishEvent(new NasXunleiShareReadyEvent(mediaId, shareUrl));
    }

    public Set<String> parseBaseline(NasLanding landing) {
        if (landing == null || !StringUtils.hasText(landing.getBaselineJson())) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(landing.getBaselineJson(),
                    new TypeReference<List<String>>() {});
            if (list == null || list.isEmpty()) {
                return Set.of();
            }
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            log.warn("[NAS] 解析基线失败 mediaId={}: {}", landing.getMediaId(), e.getMessage());
            return Set.of();
        }
    }

    private void saveBaseline(NasLanding row, Collection<String> baiduKeys) {
        row.setBaselineJson(toBaselineJson(baiduKeys));
        row.setUpdatedAt(LocalDateTime.now());
        landingMapper.updateById(row);
    }

    private String toBaselineJson(Collection<String> baiduKeys) {
        try {
            Collection<String> src = baiduKeys == null ? Collections.emptyList() : baiduKeys;
            return objectMapper.writeValueAsString(new LinkedHashSet<>(src));
        } catch (Exception e) {
            log.warn("[NAS] 序列化基线失败: {}", e.getMessage());
            return "[]";
        }
    }
}
