package com.jyinshi.search.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.jyinshi.search.docmonitor.DramaEntry;
import com.jyinshi.search.entity.DocMonitorTask;
import com.jyinshi.search.mapper.DocMonitorTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档监控搜索缓存：优先读任务表 {@code entries_json}，避免每次搜索打外站。
 */
@Slf4j
@Component
public class DocMonitorSearchCache {

    private final DocMonitorTaskMapper taskMapper;

    private volatile List<DramaEntry> cache = Collections.emptyList();
    private volatile long cacheTime;
    private static final long TTL_MS = 10 * 60 * 1000L;

    public DocMonitorSearchCache(DocMonitorTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public synchronized void invalidate() {
        cache = Collections.emptyList();
        cacheTime = 0;
    }

    public List<DramaEntry> allEntries() {
        long now = System.currentTimeMillis();
        if (now - cacheTime < TTL_MS && !cache.isEmpty()) {
            return cache;
        }
        return reload();
    }

    private synchronized List<DramaEntry> reload() {
        if (System.currentTimeMillis() - cacheTime < TTL_MS && !cache.isEmpty()) {
            return cache;
        }
        List<DocMonitorTask> tasks = taskMapper.selectEnabled();
        List<DramaEntry> all = new ArrayList<>();
        for (DocMonitorTask t : tasks) {
            if (!StringUtils.hasText(t.getEntriesJson())) {
                continue;
            }
            try {
                JSONArray arr = JSONUtil.parseArray(t.getEntriesJson());
                for (int i = 0; i < arr.size(); i++) {
                    DramaEntry e = arr.get(i, DramaEntry.class);
                    if (e != null) {
                        if (!StringUtils.hasText(e.getSource())) {
                            e.setSource(t.getSource());
                        }
                        all.add(e);
                    }
                }
            } catch (Exception ex) {
                log.warn("[DocMonitorSearchCache] 读 entries 失败 taskId={}: {}", t.getId(), ex.getMessage());
            }
        }
        cache = all;
        cacheTime = System.currentTimeMillis();
        log.info("[DocMonitorSearchCache] 已加载 {} 条剧目（{} 个任务）", all.size(), tasks.size());
        return cache;
    }
}
