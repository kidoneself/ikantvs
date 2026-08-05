package com.jyinshi.content.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.ops.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 豆瓣采集客户端。走 frodo 移动端 API + HMAC-SHA1 签名（同 MoviePilot 思路），
 * 用豆瓣 App 公开 apikey/secret，<b>无需自备 key</b>。
 */
@Slf4j
@Component
public class DoubanClient {

    /** 模仿豆瓣 Android 客户端，必须像 App 才能过。 */
    private static final String UA =
            "api-client/1 com.douban.frodo/7.22.0(231) Android/29 product/Pixel "
            + "vendor/Google model/Pixel rom/android network/wifi platform/mobile";

    private final SysConfigService config;
    private final ObjectMapper mapper;

    public DoubanClient(SysConfigService config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return config.getBool(SysConfigService.META_DOUBAN_ENABLED, true);
    }

    private String apikey() {
        return config.getOrDefault(SysConfigService.META_DOUBAN_APIKEY, "");
    }

    private String secret() {
        return config.getOrDefault(SysConfigService.META_DOUBAN_SECRET, "");
    }

    private String frodoBase() {
        return config.getOrDefault(SysConfigService.META_DOUBAN_FRODO_BASE, "https://frodo.douban.com/api/v2");
    }

    private String legacyApikey() {
        return config.getOrDefault(SysConfigService.META_DOUBAN_LEGACY_APIKEY, "");
    }

    private String legacyBase() {
        return config.getOrDefault(SysConfigService.META_DOUBAN_LEGACY_BASE, "https://api.douban.com/v2");
    }

    private int timeoutMs() {
        return config.getInt(SysConfigService.META_DOUBAN_TIMEOUT_MS, 8000);
    }

    /**
     * 用 IMDb id 反查豆瓣 subject id（录入 TMDB 时自动关联豆瓣外链，不拉元数据）。
     */
    public String resolveDoubanIdByImdb(String imdbId) {
        if (!isEnabled() || !StringUtils.hasText(imdbId)) {
            return null;
        }
        String legacyApikey = legacyApikey();
        if (!StringUtils.hasText(legacyApikey)) {
            return null;
        }
        String imdb = imdbId.trim();
        if (!imdb.startsWith("tt")) {
            return null;
        }
        String url = legacyBase() + "/movie/imdb/" + imdb;
        String body = "apikey=" + legacyApikey;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("豆瓣 IMDb 反查非 200: {} imdb={}", resp.statusCode(), imdb);
                return null;
            }
            JsonNode n = mapper.readTree(resp.body());
            return parseSubjectId(n);
        } catch (Exception e) {
            log.debug("豆瓣 IMDb 反查失败 imdb={}: {}", imdb, e.getMessage());
            return null;
        }
    }

    /** 按豆瓣 subject id 抓详情。豆瓣 URL 不区分影/剧，先试 movie 再试 tv（同源消歧，非跨源兜底）。 */
    public FetchedMetadata fetchById(String doubanId, String type) {
        if (!isEnabled() || !StringUtils.hasText(doubanId)) {
            return null;
        }
        if ("tv".equalsIgnoreCase(type) || "anime".equalsIgnoreCase(type)
                || "variety".equalsIgnoreCase(type)) {
            return fetchDetail(doubanId, "tv", type);
        }
        if ("movie".equalsIgnoreCase(type)) {
            return fetchDetail(doubanId, "movie", "movie");
        }
        FetchedMetadata m = fetchDetail(doubanId, "movie", "movie");
        return m != null ? m : fetchDetail(doubanId, "tv", "tv");
    }

    private FetchedMetadata fetchDetail(String doubanId, String endpoint, String keepType) {
        String path = "/api/v2/" + endpoint + "/" + doubanId;
        JsonNode n = getSigned(path);
        if (n == null || n.has("code") || !n.has("title")) {
            return null;
        }
        FetchedMetadata m = new FetchedMetadata();
        m.setSource("douban");
        m.setDoubanId(doubanId);
        m.setType(keepType);
        m.setTitle(text(n, "title"));
        m.setOriginalTitle(text(n, "original_title"));
        m.setYear(parseInt(text(n, "year")));
        m.setOverview(text(n, "intro"));
        m.setPoster(poster(n));
        m.setRating(rating(n));
        m.setGenres(joinArray(n.path("genres")));
        m.setCountry(joinArray(n.path("countries")));
        m.setActors(joinObjName(n.path("actors"), 8));
        m.setDirectors(joinObjName(n.path("directors"), 5));
        int ep = n.path("episodes_count").asInt(0);
        m.setEpisodeCount(ep > 0 ? ep : null);
        return m;
    }

    /** 带 frodo 签名发请求。用 JDK HttpClient 原样发送预编码 URL，避免 query 被二次编码导致签名失配。 */
    private JsonNode getSigned(String path) {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String sig = sign(path, ts);
        if (sig == null) {
            return null;
        }
        String query = "apikey=" + apikey()
                + "&_ts=" + ts
                + "&_sig=" + URLEncoder.encode(sig, StandardCharsets.UTF_8)
                + "&os_rom=android";
        String url = frodoBase().replace("/api/v2", "") + path + "?" + query;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("豆瓣请求非 200: {} path={}", resp.statusCode(), path);
                return null;
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("豆瓣请求失败: {}", e.getMessage());
            return null;
        }
    }

    /** _sig = base64(hmac_sha1(secret, "GET&" + urlencode(path) + "&" + ts))。只签 path。 */
    private String sign(String path, String ts) {
        try {
            String raw = "GET&" + URLEncoder.encode(path, StandardCharsets.UTF_8) + "&" + ts;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("豆瓣签名失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 frodo/legacy 返回的 id 字段解析纯数字 subject id。 */
    static String parseSubjectId(JsonNode n) {
        if (n == null || n.has("code")) {
            return null;
        }
        JsonNode idNode = n.get("id");
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isNumber()) {
            return idNode.asText();
        }
        String s = idNode.asText("");
        Matcher m = Pattern.compile("(\\d{5,})").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** 从豆瓣详情页 URL 解析出 subject id：movie.douban.com/subject/{id}/ */
    public static String parseId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || !host.contains("douban.com")) {
                return null;
            }
            String[] parts = uri.getPath().split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("subject".equals(parts[i]) && i + 1 < parts.length && parts[i + 1].matches("\\d+")) {
                    return parts[i + 1];
                }
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static String poster(JsonNode n) {
        JsonNode pic = n.path("pic");
        String large = text(pic, "large");
        if (StringUtils.hasText(large)) {
            return large;
        }
        String normal = text(pic, "normal");
        if (StringUtils.hasText(normal)) {
            return normal;
        }
        return text(n, "cover_url");
    }

    private static BigDecimal rating(JsonNode n) {
        JsonNode r = n.path("rating");
        double v = r.path("value").asDouble(0);
        return v > 0 ? BigDecimal.valueOf(Math.round(v * 10) / 10.0) : null;
    }

    private static String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        arr.forEach(x -> {
            if (StringUtils.hasText(x.asText())) {
                out.add(x.asText());
            }
        });
        return out.isEmpty() ? null : String.join(",", out);
    }

    private static String joinObjName(JsonNode arr, int limit) {
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : arr) {
            String name = text(x, "name");
            if (StringUtils.hasText(name)) {
                out.add(name);
            }
            if (out.size() >= limit) {
                break;
            }
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer parseInt(String s) {
        if (StringUtils.hasText(s)) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
