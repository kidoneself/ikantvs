package com.jyinshi.search.docmonitor.fetcher;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jyinshi.search.docmonitor.ContentLine;
import com.jyinshi.search.docmonitor.DocFetcher;
import com.jyinshi.search.docmonitor.DramaAggregator;
import com.jyinshi.search.docmonitor.FetchResult;
import com.jyinshi.search.docmonitor.PanLinkExtractor;
import com.jyinshi.search.docmonitor.ParseRules;
import com.jyinshi.search.docmonitor.ParsedContent;
import com.jyinshi.search.docmonitor.ParsedLink;
import com.jyinshi.search.entity.DocMonitorTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金山文档分享页抓取：meta(fver) + open/otl → 段落 → 可配置聚合。
 */
@Slf4j
@Component
public class KdocsDocFetcher implements DocFetcher {

    private static final String META_API = "https://www.kdocs.cn/3rd/drive/api/v5/links/%s";
    private static final String OTL_API = "https://www.kdocs.cn/api/v3/office/file/%s/open/otl";
    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private final DramaAggregator aggregator;

    public KdocsDocFetcher(DramaAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String source() {
        return "kdocs";
    }

    @Override
    public FetchResult fetch(DocMonitorTask task, ParseRules rules) {
        return fetchInternal(task, rules, true);
    }

    @Override
    public FetchResult fetchFull(DocMonitorTask task, ParseRules rules) {
        return fetchInternal(task, rules, false);
    }

    private FetchResult fetchInternal(DocMonitorTask task, ParseRules rules, boolean allowShortCircuit) {
        String shareUrl = task.getShareUrl();
        String linkId = extractLinkId(shareUrl);
        if (linkId == null) {
            return FetchResult.error("无效的 kdocs 分享链接（应为 https://www.kdocs.cn/l/xxxx）");
        }
        KdocsMeta meta = fetchMeta(linkId, shareUrl);
        if (meta == null) {
            return FetchResult.error("kdocs 元信息获取失败，链接可能已失效");
        }
        String fingerprint = String.valueOf(meta.fver);
        if (allowShortCircuit && fingerprint.equals(task.getContentHash())) {
            log.info("[KdocsDocFetcher] fver 未变({})，跳过下载 task={}", fingerprint, task.getTaskName());
            return FetchResult.unchanged(fingerprint);
        }
        String otlJson = fetchOtl(linkId, shareUrl);
        if (otlJson == null) {
            return FetchResult.error("kdocs 文档内容下载失败");
        }
        return FetchResult.ok(fingerprint, parse(otlJson, meta, rules));
    }

    private ParsedContent parse(String otlJson, KdocsMeta meta, ParseRules rules) {
        ParsedContent content = new ParsedContent();
        try {
            JSONObject root = JSONUtil.parseObj(otlJson);
            List<ContentLine> paragraphs = new ArrayList<>();
            walkContent(root.get("content"), paragraphs);

            Map<String, ParsedLink> unique = new LinkedHashMap<>();
            for (String url : PanLinkExtractor.extractUrls(otlJson)) {
                unique.putIfAbsent(url, linkOf(url, ""));
            }
            for (ContentLine p : paragraphs) {
                for (String url : p.getUrls()) {
                    unique.putIfAbsent(url, linkOf(url, p.getText()));
                }
            }

            int textLen = paragraphs.stream().mapToInt(p -> p.getText() == null ? 0 : p.getText().length()).sum();
            content.setTitle(meta != null && StringUtils.hasText(meta.fname) ? meta.fname : "kdocs 文档");
            content.setTextLength(textLen);
            content.setAllLinks(new ArrayList<>(unique.values()));
            content.setLinksCount(unique.size());
            content.setDramaEntries(aggregator.aggregate(paragraphs, rules, source()));
            log.info("[KdocsDocFetcher] 解析完成 title={} links={} dramas={}",
                    content.getTitle(), content.getLinksCount(),
                    content.getDramaEntries() == null ? 0 : content.getDramaEntries().size());
        } catch (Exception e) {
            log.error("[KdocsDocFetcher] 解析 OTL 失败", e);
        }
        return content;
    }

    private void walkContent(Object node, List<ContentLine> out) {
        if (node instanceof JSONObject obj) {
            if ("paragraph".equals(obj.getStr("type"))) {
                ContentLine p = new ContentLine();
                collectParagraph(obj, p);
                for (String url : PanLinkExtractor.extractUrls(p.getText())) {
                    if (!p.getUrls().contains(url)) {
                        p.getUrls().add(url);
                    }
                }
                if (StringUtils.hasText(p.getText()) || !p.getUrls().isEmpty()) {
                    out.add(p);
                }
                return;
            }
            for (String k : obj.keySet()) {
                walkContent(obj.get(k), out);
            }
        } else if (node instanceof JSONArray arr) {
            for (Object item : arr) {
                walkContent(item, out);
            }
        }
    }

    private void collectParagraph(Object node, ContentLine p) {
        if (node instanceof JSONObject obj) {
            String type = obj.getStr("type");
            if ("text".equals(type)) {
                String t = obj.getStr("text");
                if (t != null) {
                    p.setText(p.getText() + t);
                }
                JSONArray marks = obj.getJSONArray("marks");
                if (marks != null) {
                    for (int i = 0; i < marks.size(); i++) {
                        JSONObject mk = marks.getJSONObject(i);
                        if (mk != null && "link".equals(mk.getStr("type"))) {
                            JSONObject attrs = mk.getJSONObject("attrs");
                            if (attrs != null && StringUtils.hasText(attrs.getStr("href"))) {
                                p.getUrls().add(attrs.getStr("href"));
                            }
                        }
                    }
                }
            }
            if ("link".equals(type)) {
                JSONObject attrs = obj.getJSONObject("attrs");
                if (attrs != null && StringUtils.hasText(attrs.getStr("href"))) {
                    p.getUrls().add(attrs.getStr("href"));
                }
            }
            for (String k : obj.keySet()) {
                collectParagraph(obj.get(k), p);
            }
        } else if (node instanceof JSONArray arr) {
            for (Object it : arr) {
                collectParagraph(it, p);
            }
        }
    }

    private ParsedLink linkOf(String url, String text) {
        ParsedLink pl = new ParsedLink();
        pl.setUrl(url);
        pl.setText(text);
        pl.setType(PanLinkExtractor.getPanType(url));
        return pl;
    }

    private String extractLinkId(String shareUrl) {
        if (shareUrl == null || !shareUrl.contains("/l/")) {
            return null;
        }
        String after = shareUrl.substring(shareUrl.indexOf("/l/") + 3);
        int q = after.indexOf('?');
        if (q > 0) {
            after = after.substring(0, q);
        }
        int slash = after.indexOf('/');
        if (slash > 0) {
            after = after.substring(0, slash);
        }
        return after.trim();
    }

    private KdocsMeta fetchMeta(String linkId, String shareUrl) {
        try {
            HttpResponse resp = HttpRequest.get(String.format(META_API, linkId))
                    .header("User-Agent", UA)
                    .header("Referer", shareUrl)
                    .header("Accept", "application/json, text/plain, */*")
                    .timeout(15000)
                    .execute();
            if (resp.getStatus() != 200) {
                log.warn("[KdocsDocFetcher] meta status={} body={}", resp.getStatus(), resp.body());
                return null;
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            JSONObject fi = json.getJSONObject("fileinfo");
            if (fi == null) {
                return null;
            }
            KdocsMeta m = new KdocsMeta();
            m.fname = fi.getStr("fname");
            m.fver = fi.getLong("fver", 0L);
            return m;
        } catch (Exception e) {
            log.warn("[KdocsDocFetcher] meta 异常: {}", e.getMessage());
            return null;
        }
    }

    private String fetchOtl(String linkId, String shareUrl) {
        try {
            HttpResponse resp = HttpRequest.post(String.format(OTL_API, linkId))
                    .header("User-Agent", UA)
                    .header("Referer", shareUrl)
                    .header("Origin", "https://www.kdocs.cn")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .body("{}")
                    .timeout(30000)
                    .execute();
            if (resp.getStatus() != 200) {
                log.warn("[KdocsDocFetcher] otl status={}", resp.getStatus());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.warn("[KdocsDocFetcher] otl 异常: {}", e.getMessage());
            return null;
        }
    }

    private static class KdocsMeta {
        String fname;
        long fver;
    }
}
