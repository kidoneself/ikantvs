package com.jyinshi.search.service;

import com.jyinshi.content.ingest.source.LinkSource;
import com.jyinshi.content.ingest.source.RawLink;
import com.jyinshi.search.config.DocMonitorProperties;
import com.jyinshi.search.docmonitor.DramaEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文档监控 → 流式搜索插件：按关键词匹配已解析剧目，产出夸克/百度/迅雷链。
 * 关闭 {@code jyinshi.doc-monitor.enabled} 时不参与搜索。
 */
@Slf4j
@Component
public class DocMonitorLinkSource implements LinkSource {

    private final DocMonitorSearchCache cache;
    private final DocMonitorProperties properties;

    public DocMonitorLinkSource(DocMonitorSearchCache cache, DocMonitorProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public String sourceName() {
        return "docmonitor";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<RawLink> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        List<RawLink> out = new ArrayList<>();
        try {
            for (DramaEntry e : cache.allEntries()) {
                if (!match(e, kw)) {
                    continue;
                }
                String note = StringUtils.hasText(e.getFullTitle()) ? e.getFullTitle() : e.getName();
                String src = StringUtils.hasText(e.getSource()) ? e.getSource() : "docmonitor";
                if (StringUtils.hasText(e.getQuarkUrl())) {
                    out.add(RawLink.of("quark", e.getQuarkUrl().trim(), null, note, src));
                }
                if (StringUtils.hasText(e.getBaiduUrl())) {
                    out.add(toPanRaw("baidu", e.getBaiduUrl(), note, src));
                }
                if (StringUtils.hasText(e.getXunleiUrl())) {
                    out.add(toPanRaw("xunlei", e.getXunleiUrl(), note, src));
                }
            }
            if (!out.isEmpty()) {
                log.info("[DocMonitorLinkSource] kw={} 命中 {} 条链", keyword, out.size());
            }
        } catch (Exception ex) {
            log.warn("[DocMonitorLinkSource] 搜索异常: {}", ex.getMessage());
        }
        return out;
    }

    private static RawLink toPanRaw(String panType, String rawUrl, String note, String src) {
        String url = rawUrl.trim();
        String pwd = null;
        int idx = url.indexOf("?pwd=");
        if (idx < 0) {
            idx = url.indexOf("&pwd=");
        }
        if (idx > 0) {
            pwd = url.substring(idx + 5).split("[&#]")[0];
            url = url.substring(0, idx);
        }
        return RawLink.of(panType, url, pwd, note, src);
    }

    private static boolean match(DramaEntry e, String kw) {
        if (e.getName() != null && e.getName().toLowerCase(Locale.ROOT).contains(kw)) {
            return true;
        }
        return e.getFullTitle() != null && e.getFullTitle().toLowerCase(Locale.ROOT).contains(kw);
    }
}
