package com.jyinshi.search.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.search.config.DocMonitorProperties;
import com.jyinshi.search.docmonitor.DocFetcher;
import com.jyinshi.search.docmonitor.DocFetcherRegistry;
import com.jyinshi.search.docmonitor.DramaEntry;
import com.jyinshi.search.docmonitor.FetchResult;
import com.jyinshi.search.docmonitor.ParseRuleTemplates;
import com.jyinshi.search.docmonitor.ParseRules;
import com.jyinshi.search.docmonitor.ParsedContent;
import com.jyinshi.search.docmonitor.ParsedLink;
import com.jyinshi.search.dto.DocMonitorCheckResultVO;
import com.jyinshi.search.dto.DocMonitorHistoryVO;
import com.jyinshi.search.dto.DocMonitorPreviewRequest;
import com.jyinshi.search.dto.DocMonitorPreviewVO;
import com.jyinshi.search.dto.DocMonitorSaveRequest;
import com.jyinshi.search.dto.DocMonitorTaskVO;
import com.jyinshi.search.entity.DocMonitorHistory;
import com.jyinshi.search.entity.DocMonitorTask;
import com.jyinshi.search.mapper.DocMonitorHistoryMapper;
import com.jyinshi.search.mapper.DocMonitorTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocMonitorService {

    private final DocMonitorTaskMapper taskMapper;
    private final DocMonitorHistoryMapper historyMapper;
    private final DocFetcherRegistry fetcherRegistry;
    private final DocMonitorProperties properties;
    private final DocMonitorSearchCache searchCache;

    public DocMonitorService(DocMonitorTaskMapper taskMapper,
                             DocMonitorHistoryMapper historyMapper,
                             DocFetcherRegistry fetcherRegistry,
                             DocMonitorProperties properties,
                             DocMonitorSearchCache searchCache) {
        this.taskMapper = taskMapper;
        this.historyMapper = historyMapper;
        this.fetcherRegistry = fetcherRegistry;
        this.properties = properties;
        this.searchCache = searchCache;
    }

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sources", fetcherRegistry.sources());
        Map<String, ParseRules> templates = ParseRuleTemplates.all();
        m.put("templates", templates);
        return m;
    }

    public PageResult<DocMonitorTaskVO> page(long page, long size, String keyword, String source) {
        Page<DocMonitorTask> p = new Page<>(page, size);
        LambdaQueryWrapper<DocMonitorTask> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(source)) {
            q.eq(DocMonitorTask::getSource, source.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(DocMonitorTask::getTaskName, kw)
                    .or().like(DocMonitorTask::getCategory, kw)
                    .or().like(DocMonitorTask::getShareUrl, kw));
        }
        q.orderByDesc(DocMonitorTask::getLastUpdateTime).orderByDesc(DocMonitorTask::getId);
        Page<DocMonitorTask> result = taskMapper.selectPage(p, q);
        List<DocMonitorTaskVO> records = result.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), page, size, records);
    }

    public DocMonitorTaskVO get(Long id) {
        DocMonitorTask task = requireTask(id);
        return toVo(task);
    }

    @Transactional
    public DocMonitorTaskVO create(DocMonitorSaveRequest req) {
        DocMonitorTask task = new DocMonitorTask();
        applySave(task, req, true);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("[DocMonitor] 创建任务 id={} source={} name={}", task.getId(), task.getSource(), task.getTaskName());
        try {
            checkTask(task.getId(), "manual");
        } catch (Exception e) {
            log.warn("[DocMonitor] 创建后首次检查失败 id={}: {}", task.getId(), e.getMessage());
        }
        searchCache.invalidate();
        return toVo(taskMapper.selectById(task.getId()));
    }

    @Transactional
    public DocMonitorTaskVO update(Long id, DocMonitorSaveRequest req) {
        DocMonitorTask task = requireTask(id);
        applySave(task, req, false);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        searchCache.invalidate();
        return toVo(taskMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireTask(id);
        historyMapper.deleteByTaskId(id);
        taskMapper.deleteById(id);
        searchCache.invalidate();
    }

    @Transactional
    public void updateStatus(Long id, boolean enabled) {
        DocMonitorTask task = requireTask(id);
        task.setStatus(enabled ? 1 : 0);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        searchCache.invalidate();
    }

    public DocMonitorPreviewVO preview(DocMonitorPreviewRequest req) {
        String source = resolveSource(req.getSource(), req.getShareUrl());
        ParseRules rules = resolveRules(source, req.getTemplate(), req.getParseRules());
        DocMonitorTask fake = new DocMonitorTask();
        fake.setSource(source);
        fake.setShareUrl(req.getShareUrl().trim());
        fake.setAccessCode(req.getAccessCode());
        DocFetcher fetcher = fetcherRegistry.require(source);
        FetchResult fr = fetcher.fetchFull(fake, rules);
        if (fr.isError()) {
            throw new BizException(fr.getMessage());
        }
        ParsedContent parsed = fr.getParsed();
        DocMonitorPreviewVO vo = new DocMonitorPreviewVO();
        vo.setSource(source);
        vo.setFingerprint(fr.getFingerprint());
        vo.setAppliedRules(rules);
        if (parsed != null) {
            vo.setTitle(parsed.getTitle());
            vo.setLinksCount(parsed.getLinksCount());
            vo.setTextLength(parsed.getTextLength());
            List<DramaEntry> dramas = parsed.getDramaEntries() == null ? List.of() : parsed.getDramaEntries();
            vo.setDramaCount(dramas.size());
            vo.setDramas(dramas);
            List<DocMonitorPreviewVO.SampleLink> samples = new ArrayList<>();
            if (parsed.getAllLinks() != null) {
                for (ParsedLink pl : parsed.getAllLinks().stream().limit(8).toList()) {
                    DocMonitorPreviewVO.SampleLink s = new DocMonitorPreviewVO.SampleLink();
                    s.setUrl(pl.getUrl());
                    s.setType(pl.getType());
                    s.setText(pl.getText());
                    samples.add(s);
                }
            }
            vo.setSampleLinks(samples);
        }
        return vo;
    }

    @Transactional
    public DocMonitorCheckResultVO checkTask(Long id, String checkType) {
        DocMonitorTask task = requireTask(id);
        ParseRules rules = readRules(task);
        DocFetcher fetcher = fetcherRegistry.resolve(task);
        FetchResult fr = fetcher.fetch(task, rules);

        DocMonitorCheckResultVO vo = new DocMonitorCheckResultVO();
        vo.setTaskId(id);
        vo.setTaskName(task.getTaskName());
        vo.setSource(task.getSource());

        if (fr.isError()) {
            vo.setSuccess(false);
            vo.setMessage(fr.getMessage());
            task.setLastCheckTime(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return vo;
        }

        if (fr.isUnchanged()) {
            vo.setSuccess(true);
            vo.setUnchanged(true);
            vo.setMessage("内容未变化");
            vo.setLinksCount(task.getLinksCount());
            vo.setDramaCount(task.getDramaCount());
            task.setLastCheckTime(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return vo;
        }

        ParsedContent parsed = fr.getParsed();
        int oldLinks = task.getLinksCount() == null ? 0 : task.getLinksCount();
        int oldText = task.getTextLength() == null ? 0 : task.getTextLength();
        int newLinks = parsed == null ? 0 : parsed.getLinksCount();
        int newText = parsed == null ? 0 : parsed.getTextLength();
        List<DramaEntry> dramas = parsed == null || parsed.getDramaEntries() == null
                ? List.of() : parsed.getDramaEntries();

        boolean updated = !fr.getFingerprint().equals(task.getContentHash())
                || oldLinks != newLinks
                || oldText != newText;

        if (parsed != null && StringUtils.hasText(parsed.getTitle())
                && !StringUtils.hasText(task.getTaskName())) {
            task.setTaskName(parsed.getTitle());
        }

        task.setContentHash(fr.getFingerprint());
        task.setLinksCount(newLinks);
        task.setTextLength(newText);
        task.setDramaCount(dramas.size());
        task.setEntriesJson(JSONUtil.toJsonStr(dramas));
        task.setLastCheckTime(LocalDateTime.now());
        if (updated) {
            task.setLastUpdateTime(LocalDateTime.now());
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        DocMonitorHistory h = new DocMonitorHistory();
        h.setTaskId(task.getId());
        h.setSource(task.getSource());
        h.setTaskName(task.getTaskName());
        h.setOldLinksCount(oldLinks);
        h.setNewLinksCount(newLinks);
        h.setLinksCountDiff(newLinks - oldLinks);
        h.setOldTextLength(oldText);
        h.setNewTextLength(newText);
        h.setTextLengthDiff(newText - oldText);
        h.setContentHash(fr.getFingerprint());
        h.setCheckType(checkType == null ? "manual" : checkType);
        h.setHasUpdate(updated ? 1 : 0);
        h.setChangeDescription(updated
                ? String.format("链接 %d→%d，剧目 %d 条", oldLinks, newLinks, dramas.size())
                : "无实质变化");
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);

        searchCache.invalidate();

        vo.setSuccess(true);
        vo.setUpdated(updated);
        vo.setMessage(h.getChangeDescription());
        vo.setLinksCount(newLinks);
        vo.setDramaCount(dramas.size());
        return vo;
    }

    public List<DocMonitorCheckResultVO> checkAll(String checkType) {
        List<DocMonitorTask> tasks = taskMapper.selectEnabled();
        List<DocMonitorCheckResultVO> out = new ArrayList<>();
        for (DocMonitorTask t : tasks) {
            try {
                out.add(checkTask(t.getId(), checkType == null ? "auto" : checkType));
            } catch (Exception e) {
                log.warn("[DocMonitor] 检查失败 id={}: {}", t.getId(), e.getMessage());
                DocMonitorCheckResultVO vo = new DocMonitorCheckResultVO();
                vo.setTaskId(t.getId());
                vo.setTaskName(t.getTaskName());
                vo.setSource(t.getSource());
                vo.setSuccess(false);
                vo.setMessage(e.getMessage());
                out.add(vo);
            }
        }
        return out;
    }

    public List<DocMonitorHistoryVO> history(Long taskId, int limit) {
        requireTask(taskId);
        int lim = Math.min(Math.max(limit, 1), 100);
        List<DocMonitorHistory> list = historyMapper.selectList(
                new LambdaQueryWrapper<DocMonitorHistory>()
                        .eq(DocMonitorHistory::getTaskId, taskId)
                        .orderByDesc(DocMonitorHistory::getId)
                        .last("LIMIT " + lim));
        return list.stream().map(h -> {
            DocMonitorHistoryVO vo = new DocMonitorHistoryVO();
            BeanUtils.copyProperties(h, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    public void runScheduledCheck() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("[DocMonitor] 定时检查开始，启用任务数={}", taskMapper.selectEnabled().size());
        checkAll("auto");
    }

    public Collection<String> sources() {
        return fetcherRegistry.sources();
    }

    // ---------- helpers ----------

    private void applySave(DocMonitorTask task, DocMonitorSaveRequest req, boolean creating) {
        String source = resolveSource(req.getSource(), req.getShareUrl());
        if ("flowus".equals(source) && !StringUtils.hasText(req.getTaskName()) && creating) {
            // FlowUs 仍建议填名；允许空，检查后用文档标题回填
        }
        task.setSource(source);
        task.setShareUrl(req.getShareUrl().trim());
        if (req.getTaskName() != null) {
            task.setTaskName(req.getTaskName().trim());
        } else if (creating) {
            task.setTaskName("");
        }
        task.setAccessCode(req.getAccessCode());
        task.setCategory(req.getCategory());
        task.setRemark(req.getRemark());
        if (req.getStatus() != null) {
            task.setStatus(req.getStatus());
        } else if (creating) {
            task.setStatus(1);
        }
        ParseRules rules = resolveRules(source, req.getTemplate(), req.getParseRules());
        task.setParseRules(JSONUtil.toJsonStr(rules));
        if (creating) {
            task.setLinksCount(0);
            task.setTextLength(0);
            task.setDramaCount(0);
        }
    }

    private ParseRules resolveRules(String source, String template, ParseRules incoming) {
        ParseRules base;
        if (StringUtils.hasText(template) && ParseRuleTemplates.all().containsKey(template)) {
            base = copyRules(ParseRuleTemplates.all().get(template));
        } else {
            base = ParseRuleTemplates.forSource(source);
        }
        if (incoming == null) {
            return base;
        }
        // 用传入字段覆盖（列表非 null 即覆盖，含空列表）
        if (incoming.getTemplate() != null) {
            base.setTemplate(incoming.getTemplate());
        } else if (StringUtils.hasText(template)) {
            base.setTemplate(template);
        } else {
            base.setTemplate("custom");
        }
        if (incoming.getQuarkPrefixes() != null) {
            base.setQuarkPrefixes(incoming.getQuarkPrefixes());
        }
        if (incoming.getBaiduPrefixes() != null) {
            base.setBaiduPrefixes(incoming.getBaiduPrefixes());
        }
        if (incoming.getXunleiPrefixes() != null) {
            base.setXunleiPrefixes(incoming.getXunleiPrefixes());
        }
        if (incoming.getNoisePrefixes() != null) {
            base.setNoisePrefixes(incoming.getNoisePrefixes());
        }
        if (incoming.getNoiseContains() != null) {
            base.setNoiseContains(incoming.getNoiseContains());
        }
        if (incoming.getNameExtractRegex() != null) {
            base.setNameExtractRegex(incoming.getNameExtractRegex());
        }
        if (incoming.getPwdRegex() != null) {
            base.setPwdRegex(incoming.getPwdRegex());
        }
        if (incoming.getMatchMode() != null) {
            base.setMatchMode(incoming.getMatchMode());
        }
        return base;
    }

    private ParseRules copyRules(ParseRules src) {
        return JSONUtil.toBean(JSONUtil.toJsonStr(src), ParseRules.class);
    }

    private ParseRules readRules(DocMonitorTask task) {
        if (StringUtils.hasText(task.getParseRules())) {
            try {
                ParseRules r = JSONUtil.toBean(task.getParseRules(), ParseRules.class);
                if (r != null) {
                    return r;
                }
            } catch (Exception e) {
                log.warn("[DocMonitor] 解析 parse_rules 失败 taskId={}: {}", task.getId(), e.getMessage());
            }
        }
        return ParseRuleTemplates.forSource(task.getSource());
    }

    private String resolveSource(String source, String url) {
        if (StringUtils.hasText(source)) {
            String s = source.trim().toLowerCase();
            fetcherRegistry.require(s);
            return s;
        }
        if (url != null && url.toLowerCase().contains("kdocs.cn")) {
            return "kdocs";
        }
        return "flowus";
    }

    private DocMonitorTask requireTask(Long id) {
        DocMonitorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        return task;
    }

    private DocMonitorTaskVO toVo(DocMonitorTask task) {
        DocMonitorTaskVO vo = new DocMonitorTaskVO();
        BeanUtils.copyProperties(task, vo);
        vo.setParseRules(readRules(task));
        return vo;
    }
}
