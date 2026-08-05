package com.jyinshi.content.ingest.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.content.ingest.IngestPanFilter;
import com.jyinshi.content.ingest.IngestProperties;
import com.jyinshi.ops.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * pansou 来源插件：调 {@code {baseUrl}/api/search} 按剧名搜网盘资源。
 *
 * <p>只做「找链接」，产出 {@link RawLink} 交给 {@code IngestService}。归属识别/去重/入库/检测都不管。
 * 兼容两种响应壳：顶层直接 {@code {merged_by_type}} 或包一层 {@code {code,data:{merged_by_type}}}。
 */
@Slf4j
@Component
public class PansouSource implements LinkSource {

    private static final String SOURCE = "pansou";

    private final IngestProperties props;
    private final IngestPanFilter panFilter;
    private final ObjectMapper mapper;
    private final SysConfigService config;
    private final HttpClient http;

    public PansouSource(IngestProperties props, IngestPanFilter panFilter, ObjectMapper mapper,
                        SysConfigService config) {
        this.props = props;
        this.panFilter = panFilter;
        this.mapper = mapper;
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, props.getPansou().getTimeoutMs())))
                .build();
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    /** pansou 服务地址：优先后台 sys_config（国内部署指反代），回退 yml/env 默认。 */
    private String baseUrl() {
        return config.getOrDefault(SysConfigService.INGEST_PANSOU_BASE_URL, props.getPansou().getBaseUrl());
    }

    @Override
    public boolean isEnabled() {
        // 总开关(ingest.enabled)仍走 yml；pansou 单源开关后台可改
        return props.isEnabled()
                && config.getBool(SysConfigService.INGEST_PANSOU_ENABLED, props.getPansou().isEnabled());
    }

    @Override
    public List<RawLink> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        IngestProperties.Pansou cfg = props.getPansou();
        Set<String> allowed = panFilter.resolve(cfg.getCloudTypes());
        if (allowed != null && allowed.isEmpty()) {
            return List.of();
        }
        try {
            String body = buildBody(keyword, panFilter.toCsv(cfg.getCloudTypes()));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/search"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[ingest] pansou 非 200：{} kw={}", resp.statusCode(), keyword);
                return List.of();
            }
            return parse(mapper.readTree(resp.body()));
        } catch (Exception e) {
            log.warn("[ingest] pansou 搜索失败 kw={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private String buildBody(String keyword, String cloudTypes) throws Exception {
        var root = mapper.createObjectNode();
        root.put("kw", keyword);
        root.put("res", "merge");
        if (StringUtils.hasText(cloudTypes)) {
            var arr = root.putArray("cloud_types");
            for (String t : cloudTypes.split(",")) {
                if (StringUtils.hasText(t)) {
                    arr.add(t.trim());
                }
            }
        }
        return mapper.writeValueAsString(root);
    }

    /** 从响应里取 merged_by_type（兼容顶层/包 data 两种），逐条转 RawLink。 */
    private List<RawLink> parse(JsonNode root) {
        JsonNode merged = root.path("merged_by_type");
        if (merged.isMissingNode() || !merged.isObject()) {
            merged = root.path("data").path("merged_by_type");
        }
        if (merged.isMissingNode() || !merged.isObject()) {
            return List.of();
        }
        List<RawLink> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> types = merged.fields();
        while (types.hasNext()) {
            Map.Entry<String, JsonNode> entry = types.next();
            String panType = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            if (!entry.getValue().isArray()) {
                continue;
            }
            for (JsonNode item : entry.getValue()) {
                String url = text(item, "url");
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                RawLink link = RawLink.of(panType, url.trim(), text(item, "password"),
                        text(item, "note"), SOURCE);
                link.setPublishedAt(parseTime(text(item, "datetime")));
                out.add(link);
            }
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** pansou datetime 形如 2023-06-10T14:23:45Z；解析失败或占位(0001-)返回 null。 */
    private static LocalDateTime parseTime(String s) {
        if (!StringUtils.hasText(s) || s.startsWith("0001")) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception ignore) {
            return null;
        }
    }
}
