package com.jyinshi.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.content.config.QuarkRankingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 夸克影视热榜客户端（公开排行接口，无搜索）。
 *
 * <p>接口：{@code getYingshiRanking}。非官方，可能变更或风控。</p>
 */
@Slf4j
@Component
public class QuarkRankingClient {

    private final QuarkRankingProperties props;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public QuarkRankingClient(QuarkRankingProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @param channel  电影 / 电视剧 / 综艺 / 动漫 / 短剧
     * @param rankType 最热 / 新片榜 / 好评榜（接口也接受「热搜榜」等）
     */
    public List<QuarkRankingItem> fetch(String channel, String rankType, int start, int hit) {
        if (!props.isEnabled()) {
            return Collections.emptyList();
        }
        int safeHit = Math.max(1, Math.min(50, hit));
        int safeStart = Math.max(0, start);
        try {
            String url = props.getApiUrl()
                    + "?channel=" + enc(channel)
                    + "&rank_type=" + enc(rankType)
                    + "&area=" + enc("全部")
                    + "&year=" + enc("全部")
                    + "&cate=" + enc("全部")
                    + "&from=hot_page"
                    + "&start=" + safeStart
                    + "&hit=" + safeHit;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(3000, props.getTimeoutMs())))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[quark-ranking] HTTP {} channel={} rank={}", resp.statusCode(), channel, rankType);
                return Collections.emptyList();
            }
            return parse(resp.body());
        } catch (Exception e) {
            log.warn("[quark-ranking] 请求失败 channel={} rank={}: {}", channel, rankType, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<QuarkRankingItem> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        if (root.path("status").asInt(-1) != 0) {
            log.warn("[quark-ranking] 业务失败: {}", root.path("msg").asText());
            return Collections.emptyList();
        }
        JsonNode items = root.path("data").path("hits").path("hit").path("item");
        if (!items.isArray()) {
            return Collections.emptyList();
        }
        List<QuarkRankingItem> list = new ArrayList<>(items.size());
        for (JsonNode n : items) {
            String title = text(n, "title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            list.add(new QuarkRankingItem(
                    title.trim(),
                    text(n, "year"),
                    text(n, "area"),
                    text(n, "category"),
                    text(n, "channel"),
                    text(n, "score_avg"),
                    text(n, "hot_score"),
                    text(n, "src"),
                    text(n, "actors"),
                    text(n, "desc"),
                    text(n, "video_id"),
                    text(n, "ranking")
            ));
        }
        return list;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
