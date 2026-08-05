package com.jyinshi.transfer.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.transfer.dto.NasFileEntry;
import com.jyinshi.transfer.entity.NasJob;
import com.jyinshi.transfer.mapper.NasJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * nas_job 入队（千云 SQL claim）；可选 POST 千云 /api/wake。
 *
 * <p>入队策略：按「当前差集」为准。已有 pending 会先取消再换成最新差集
 * （迅雷换源/追更补齐后，自动丢掉过时的整部首灌，不必人工判断）。</p>
 */
@Slf4j
@Service
public class NasJobService {

    public static final String CFG_ENABLED = SysConfigService.TRANSFER_NAS_ENABLED;
    public static final String CFG_WAKE_URL = SysConfigService.TRANSFER_NAS_WAKE_URL;

    private final NasJobMapper nasJobMapper;
    private final SysConfigService config;
    private final ObjectMapper objectMapper;

    public NasJobService(NasJobMapper nasJobMapper, SysConfigService config, ObjectMapper objectMapper) {
        this.nasJobMapper = nasJobMapper;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return config.getBool(CFG_ENABLED, false);
    }

    /**
     * 取消该百度链尚未执行的 pending 灌盘任务。
     *
     * @return 取消条数
     */
    public int cancelPending(Long mediaLinkId, String reason) {
        if (mediaLinkId == null) {
            return 0;
        }
        String msg = StringUtils.hasText(reason) ? reason : "差集已变化，自动取消";
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        int n = nasJobMapper.cancelPendingByMediaLink(mediaLinkId, msg);
        if (n > 0) {
            log.info("[NAS] 自动取消 pending {} 条 mediaLinkId={} reason={}", n, mediaLinkId, msg);
        }
        return n;
    }

    /**
     * 按最新差集入队：先取消同链 pending，再写入新任务。
     * 若仍有 running（千云正在传），则跳过，等下次重算。
     *
     * @return 新任务 id；跳过或失败返回 null
     */
    public Long enqueueOrReplace(Long mediaLinkId, String title, String xunleiFolderId,
                                 Long baiduAccountId, List<NasFileEntry> files) {
        if (!isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(xunleiFolderId) || files == null || files.isEmpty()) {
            return null;
        }
        cancelPending(mediaLinkId, "差集已更新，替换为 " + files.size() + " 个文件");
        // running 中的不打断；下次迅雷/百度回报再重算
        if (mediaLinkId != null && nasJobMapper.countActiveByMediaLink(mediaLinkId) > 0) {
            log.info("[NAS] 跳过入队：仍有 running 任务 mediaLinkId={}（待其结束后再按差集补）",
                    mediaLinkId);
            return null;
        }
        return insertJob(mediaLinkId, title, xunleiFolderId, baiduAccountId, files);
    }

    /** @deprecated 用 {@link #enqueueOrReplace}；保留兼容。 */
    public Long enqueue(Long mediaLinkId, String title, String xunleiFolderId,
                        Long baiduAccountId, List<NasFileEntry> files) {
        return enqueueOrReplace(mediaLinkId, title, xunleiFolderId, baiduAccountId, files);
    }

    private Long insertJob(Long mediaLinkId, String title, String xunleiFolderId,
                           Long baiduAccountId, List<NasFileEntry> files) {
        try {
            NasJob job = new NasJob();
            job.setBaiduAccountId(baiduAccountId != null ? baiduAccountId : 0L);
            job.setXunleiFolderId(xunleiFolderId);
            job.setFilesJson(objectMapper.writeValueAsString(files.stream().map(f -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("fs_id", f.getFsId());
                m.put("name", f.getName());
                m.put("size", f.getSize());
                m.put("rel_dir", f.getRelDir() == null ? "" : f.getRelDir());
                return m;
            }).toList()));
            job.setTitle(title);
            job.setMediaLinkId(mediaLinkId);
            job.setStatus("pending");
            job.setPriority(5);
            job.setAttempts(0);
            job.setMaxAttempts(3);
            job.setAvailableAt(LocalDateTime.now());
            job.setTotalFiles(files.size());
            job.setDoneFiles(0);
            job.setFailedFiles(0);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            nasJobMapper.insert(job);
            log.info("[NAS] 入队 jobId={} mediaLinkId={} files={} folder={}",
                    job.getId(), mediaLinkId, files.size(), xunleiFolderId);
            wakeIfConfigured();
            return job.getId();
        } catch (Exception e) {
            log.warn("[NAS] 入队失败 mediaLinkId={}: {}", mediaLinkId, e.getMessage());
            return null;
        }
    }

    /** 入队后戳千云立刻 claim；URL 空则跳过。 */
    public void wakeIfConfigured() {
        String url = config.getOrDefault(CFG_WAKE_URL, "");
        if (!StringUtils.hasText(url)) {
            return;
        }
        String wake = url.endsWith("/") ? url + "api/wake" : url.replaceAll("/+$", "") + "/api/wake";
        try {
            HttpResponse resp = HttpRequest.post(wake).timeout(5000).execute();
            log.info("[NAS] wake {} → {}", wake, resp.getStatus());
        } catch (Exception e) {
            log.warn("[NAS] wake 失败 {}: {}", wake, e.getMessage());
        }
    }

    public NasJob findById(Long id) {
        return id == null ? null : nasJobMapper.selectById(id);
    }

    public List<NasJob> listRecent(int limit) {
        return nasJobMapper.selectList(new LambdaQueryWrapper<NasJob>()
                .orderByDesc(NasJob::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
