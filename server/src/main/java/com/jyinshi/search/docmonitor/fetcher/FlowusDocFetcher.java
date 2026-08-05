package com.jyinshi.search.docmonitor.fetcher;

import cn.hutool.crypto.digest.MD5;
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
 * FlowUs 分享页抓取：GET /api/docs/{id} → 块列表 → 可配置聚合。
 */
@Slf4j
@Component
public class FlowusDocFetcher implements DocFetcher {

    private static final String API_BASE = "https://flowus.cn/api/docs/";
    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private final DramaAggregator aggregator;

    public FlowusDocFetcher(DramaAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String source() {
        return "flowus";
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
        String jsonData = fetchPage(shareUrl);
        if (jsonData == null) {
            return FetchResult.error("获取 FlowUs 数据失败，请检查链接是否有效");
        }
        String fingerprint = MD5.create().digestHex(jsonData);
        if (allowShortCircuit && fingerprint.equals(task.getContentHash())) {
            return FetchResult.unchanged(fingerprint);
        }
        return FetchResult.ok(fingerprint, parse(jsonData, rules));
    }

    private ParsedContent parse(String jsonData, ParseRules rules) {
        ParsedContent content = new ParsedContent();
        try {
            JSONObject json = JSONUtil.parseObj(jsonData);
            String title = json.getStr("title");
            if (!StringUtils.hasText(title) && json.getJSONObject("data") != null) {
                title = json.getJSONObject("data").getStr("title");
            }
            content.setTitle(StringUtils.hasText(title) ? title : "未找到标题");

            List<ContentLine> lines = extractLines(json);
            StringBuilder textBuf = new StringBuilder();
            Map<String, ParsedLink> unique = new LinkedHashMap<>();
            for (ContentLine line : lines) {
                if (StringUtils.hasText(line.getText())) {
                    textBuf.append(line.getText()).append('\n');
                }
                for (String url : line.getUrls()) {
                    unique.putIfAbsent(url, linkOf(url, line.getText()));
                }
            }
            for (String url : PanLinkExtractor.extractUrls(jsonData)) {
                unique.putIfAbsent(url, linkOf(url, ""));
            }

            content.setTextLength(textBuf.length());
            content.setAllLinks(new ArrayList<>(unique.values()));
            content.setLinksCount(unique.size());
            content.setDramaEntries(aggregator.aggregate(lines, rules, source()));
            log.info("[FlowusDocFetcher] 解析完成 title={} links={} dramas={}",
                    content.getTitle(), content.getLinksCount(),
                    content.getDramaEntries() == null ? 0 : content.getDramaEntries().size());
        } catch (Exception e) {
            log.error("[FlowusDocFetcher] 解析失败", e);
        }
        return content;
    }

    private List<ContentLine> extractLines(JSONObject root) {
        List<ContentLine> lines = new ArrayList<>();
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return lines;
        }
        JSONObject blocks = data.getJSONObject("blocks");
        if (blocks == null) {
            return lines;
        }
        JSONObject pageBlock = null;
        for (String key : blocks.keySet()) {
            JSONObject b = blocks.getJSONObject(key);
            if (b != null && Integer.valueOf(0).equals(b.getInt("type"))) {
                pageBlock = b;
                break;
            }
        }
        if (pageBlock == null) {
            return lines;
        }
        JSONArray subNodes = pageBlock.getJSONArray("subNodes");
        if (subNodes == null) {
            return lines;
        }
        for (int i = 0; i < subNodes.size(); i++) {
            String nodeId = subNodes.getStr(i);
            JSONObject block = blocks.getJSONObject(nodeId);
            if (block == null) {
                continue;
            }
            String title = block.getStr("title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            ContentLine line = new ContentLine();
            line.setText(title.trim());
            String fromSeg = extractUrlFromBlock(block);
            if (StringUtils.hasText(fromSeg)) {
                line.getUrls().add(fromSeg);
            }
            for (String u : PanLinkExtractor.extractUrls(title)) {
                if (!line.getUrls().contains(u)) {
                    line.getUrls().add(u);
                }
            }
            lines.add(line);
        }
        return lines;
    }

    private String extractUrlFromBlock(JSONObject block) {
        try {
            JSONObject bd = block.getJSONObject("data");
            if (bd == null) {
                return null;
            }
            JSONArray segments = bd.getJSONArray("segments");
            if (segments == null) {
                return null;
            }
            for (int i = 0; i < segments.size(); i++) {
                JSONObject seg = segments.getJSONObject(i);
                if (seg != null && Integer.valueOf(3).equals(seg.getInt("type"))) {
                    String url = seg.getStr("url");
                    if (StringUtils.hasText(url)) {
                        return url;
                    }
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    private ParsedLink linkOf(String url, String text) {
        ParsedLink pl = new ParsedLink();
        pl.setUrl(url);
        pl.setText(text);
        pl.setType(PanLinkExtractor.getPanType(url));
        return pl;
    }

    private String extractDocId(String shareUrl) {
        if (shareUrl == null || !shareUrl.contains("/share/")) {
            return null;
        }
        String after = shareUrl.substring(shareUrl.indexOf("/share/") + 7);
        int q = after.indexOf('?');
        if (q > 0) {
            after = after.substring(0, q);
        }
        return after.trim();
    }

    private String fetchPage(String shareUrl) {
        String docId = extractDocId(shareUrl);
        if (docId == null) {
            log.error("[FlowusDocFetcher] 无效分享链接: {}", shareUrl);
            return null;
        }
        try {
            HttpResponse response = HttpRequest.get(API_BASE + docId)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("x-platform", "web-cookie")
                    .header("x-app-origin", "web")
                    .header("x-product", "flowus")
                    .header("app_version_name", "1.146.0")
                    .header("Referer", shareUrl)
                    .timeout(30000)
                    .execute();
            if (response.getStatus() == 200) {
                return response.body();
            }
            log.error("[FlowusDocFetcher] API status={}", response.getStatus());
            return null;
        } catch (Exception e) {
            log.error("[FlowusDocFetcher] 请求异常: {}", e.getMessage());
            return null;
        }
    }
}
