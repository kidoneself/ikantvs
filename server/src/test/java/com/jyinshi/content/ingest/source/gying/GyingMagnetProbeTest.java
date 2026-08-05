package com.jyinshi.content.ingest.source.gying;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手动探测 Gying downlist 磁力字段（需出网 + 账号）。本地跑：
 * {@code mvn -Dtest=GyingMagnetProbeTest test}
 */
@Disabled("手动探测，默认不跑 CI")
class GyingMagnetProbeTest {

    private static final String BASE = "https://www.xn--wcv59z.com";
    private static final String USER = "8768611@qq.com";
    private static final String PASS = "Lzq951201@";
    private static final Pattern SEARCH_DATA =
            Pattern.compile("(?s)_obj\\s*\\.\\s*search\\s*=\\s*(\\{.*?\\})\\s*;");

    @Test
    void probeZuoyeJiangZhi() throws Exception {
        GyingSearchClient client = new GyingSearchClient(BASE, "cache/gying_probe_cookies", null, 4);
        if (!client.login(USER, PASS)) {
            throw new IllegalStateException("login failed");
        }

        String kw = "昨夜将至";
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String searchUrl = BASE + "/search?q="
                + java.net.URLEncoder.encode(kw, StandardCharsets.UTF_8) + "&type=0&mode=2";
        // reuse client search instead
        List<GyingSearchClient.SearchResult> results = client.search(kw);
        int magnets = 0;
        for (GyingSearchClient.SearchResult r : results) {
            for (GyingSearchClient.PanLink link : r.links) {
                if ("magnet".equals(link.type)) {
                    magnets++;
                    System.out.println("RESULT magnet: " + link.workTitle);
                    System.out.println("  url: " + link.url);
                }
            }
        }
        System.out.println("=== search magnets from client: " + magnets + " in " + results.size() + " results ===");

        // raw detail JSON for titles matching keyword
        String html = httpGet(http, searchUrl, cookieFrom(client));
        Matcher m = SEARCH_DATA.matcher(html);
        if (!m.find()) {
            System.out.println("no search json in html len=" + html.length());
            return;
        }
        JsonNode search = new ObjectMapper().readTree(m.group(1));
        JsonNode titles = search.path("l").path("title");
        JsonNode ids = search.path("l").path("i");
        JsonNode types = search.path("l").path("d");
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i).asText("");
            if (!title.contains("昨夜") && !title.toLowerCase().contains("inseparable")) {
                continue;
            }
            String type = types.get(i).asText("");
            String id = ids.get(i).asText("");
            String detailUrl = BASE + "/res/downurl/" + type + "/" + id;
            String body = httpGet(http, detailUrl, cookieFrom(client));
            JsonNode detail = new ObjectMapper().readTree(body);
            JsonNode list = detail.path("downlist").path("list");
            JsonNode hashes = list.path("m");
            JsonNode names = list.path("t");
            System.out.println("\n=== RAW " + title + " (" + type + "/" + id + ") ===");
            System.out.println("downlist.list.m size=" + hashes.size());
            for (int j = 0; j < hashes.size(); j++) {
                String hash = hashes.get(j).asText("");
                String name = j < names.size() ? names.get(j).asText("") : "";
                System.out.printf("  [%d] len=%d hash=%s name=%s%n", j, hash.length(), hash, name);
            }
            System.out.println("full list keys: " + list.fieldNames());
            System.out.println("panlist.url size=" + detail.path("panlist").path("url").size());
        }
    }

    private static String cookieFrom(GyingSearchClient client) throws Exception {
        var f = GyingSearchClient.class.getDeclaredField("cookie");
        f.setAccessible(true);
        return (String) f.get(client);
    }

    private static String httpGet(HttpClient http, String url, String cookie) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", cookie)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
