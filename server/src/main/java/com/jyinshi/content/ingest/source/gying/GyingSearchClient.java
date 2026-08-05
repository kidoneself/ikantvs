package com.jyinshi.content.ingest.source.gying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gying（观影）单账号搜索客户端。
 *
 * <p>对齐 pansou plugin/gying：登录 + cookie 持久化 + PoW 机器人验证解题 + 详情页拉网盘/磁力链接。
 * 只负责「找链接」，产出 {@link SearchResult}（含 {@link PanLink}）交给上层的 {@code GyingSource}
 * 转成规范化 {@code RawLink}。一个实例绑定一个账号；多账号轮询由 {@code GyingAccountPool} 负责。
 */
@Slf4j
public class GyingSearchClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_CONCURRENT_DETAILS = 8;
    private static final long POW_MIN_SUBMIT_MS = 3000;

    private static final Pattern CHALLENGE_JSON_PATTERN =
            Pattern.compile("(?s)const\\s+json\\s*=\\s*(\\{.*?\\})\\s*;\\s*const\\s+jss\\s*=");
    private static final Pattern SEARCH_DATA_PATTERN =
            Pattern.compile("(?s)_obj\\s*\\.\\s*search\\s*=\\s*(\\{.*?\\})\\s*;");
    private static final Pattern MAGNET_HASH_PATTERN =
            Pattern.compile("(?i)^[a-f0-9]{40}$");

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String cookieCacheDir;
    private final int detailConcurrency;
    private final LinkedHashMap<String, String> cookieJar = new LinkedHashMap<>();

    private String cookie;
    private String username;
    private LocalDateTime cookieExpireTime;

    public GyingSearchClient(String baseUrl, String cookieCacheDir, String httpProxy, int detailConcurrency) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.cookieCacheDir = cookieCacheDir != null && !cookieCacheDir.isBlank()
                ? cookieCacheDir : "cache/gying_cookies";
        this.detailConcurrency = detailConcurrency > 0 ? detailConcurrency : MAX_CONCURRENT_DETAILS;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (httpProxy != null && !httpProxy.isBlank()) {
            try {
                URI p = URI.create(httpProxy.trim());
                if (p.getHost() != null && p.getPort() > 0) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(), p.getPort())));
                    log.info("[gying] 使用代理 {}:{}", p.getHost(), p.getPort());
                }
            } catch (Exception e) {
                log.warn("[gying] 代理配置无效: {}", httpProxy);
            }
        }
        this.httpClient = builder.build();
    }

    static String normalizeBaseUrl(String raw) {
        String s = raw.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public boolean login(String username, String password) throws Exception {
        this.username = username;
        if (loadCookieFromCache(username)) {
            log.info("[gying] 从缓存恢复 Cookie user={}", maskUser(username));
            return true;
        }

        synchronized (cookieJar) {
            cookieJar.clear();
        }

        requestWithChallengeRetry("GET", baseUrl + "/", null, null);

        String postData = String.format(
                "code=&siteid=1&dosubmit=1&cookietime=10506240&username=%s&password=%s",
                URLEncoder.encode(username, StandardCharsets.UTF_8),
                URLEncoder.encode(password, StandardCharsets.UTF_8));
        HttpResult loginResp = requestWithChallengeRetry(
                "POST", baseUrl + "/user/login", postData, "application/x-www-form-urlencoded");

        LoginResponse loginResult = objectMapper.readValue(loginResp.body, LoginResponse.class);
        if (loginResult.code != 200) {
            log.warn("[gying] 登录失败: {}", loginResp.body);
            return false;
        }

        requestWithChallengeRetry("GET", baseUrl + "/mv/wkMn", null, null);

        syncCookieString();
        this.cookieExpireTime = LocalDateTime.now().plusDays(121);
        saveCookieToCache(username, this.cookie, this.cookieExpireTime);
        log.info("[gying] 登录成功 user={}", maskUser(username));
        return true;
    }

    public List<SearchResult> search(String keyword) throws Exception {
        if (cookie == null || cookie.isEmpty()) {
            throw new IllegalStateException("请先登录");
        }

        String searchUrl = baseUrl + "/search?q="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&type=0&mode=2";
        HttpResult searchResp = requestWithChallengeRetry("GET", searchUrl, null, null);

        if (searchResp.status == 403) {
            deleteCookieCache();
            throw new RuntimeException("403 Forbidden - Cookie 已过期，请重新登录");
        }
        if (searchResp.status != 200) {
            throw new RuntimeException("搜索失败，状态码: " + searchResp.status);
        }

        String html = searchResp.body;
        if (isLoginShell(html)) {
            deleteCookieCache();
            throw new RuntimeException("HTTP 403 Forbidden - 需要重新登录");
        }

        Matcher matcher = SEARCH_DATA_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("未找到搜索结果数据");
        }

        SearchData searchData = objectMapper.readValue(matcher.group(1), SearchData.class);
        requestWithChallengeRetry("GET", baseUrl + "/mv/wkMn", null, null);
        return fetchAllDetails(searchData, keyword);
    }

    public boolean isCookieValid() {
        return cookie != null && !cookie.isEmpty()
                && cookieExpireTime != null
                && LocalDateTime.now().isBefore(cookieExpireTime);
    }

    // ==================== HTTP + Challenge ====================

    private record HttpResult(int status, String body) {
    }

    private HttpResult requestWithChallengeRetry(String method, String url, String body, String contentType)
            throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpResult resp = sendRaw(method, url, body, contentType);
            if (isBotChallengePage(resp.body)) {
                if (attempt == 1) {
                    throw new RuntimeException("重试后仍然进入机器人验证页");
                }
                log.info("[gying] 触发安全验证，开始解题 url={}", url);
                solveBotChallenge(url, resp.body);
                continue;
            }
            return resp;
        }
        throw new RuntimeException("请求重试次数已耗尽");
    }

    private HttpResult sendRaw(String method, String url, String body, String contentType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cookie", getCookieHeader())
                .timeout(Duration.ofSeconds(60));
        if ("POST".equals(method)) {
            b.header("Content-Type", contentType != null ? contentType : "application/x-www-form-urlencoded");
            b.header("Referer", baseUrl + "/");
            b.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        } else {
            b.GET();
        }
        HttpResponse<String> response = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(response);
        return new HttpResult(response.statusCode(), response.body());
    }

    private void mergeSetCookies(HttpResponse<?> response) {
        synchronized (cookieJar) {
            for (String h : response.headers().allValues("Set-Cookie")) {
                String[] parts = h.split(";", 2);
                if (parts.length == 0) {
                    continue;
                }
                String[] kv = parts[0].split("=", 2);
                if (kv.length == 2) {
                    cookieJar.put(kv[0].trim(), kv[1].trim());
                }
            }
            syncCookieString();
        }
    }

    private String getCookieHeader() {
        synchronized (cookieJar) {
            return cookie != null ? cookie : "";
        }
    }

    private void syncCookieString() {
        cookie = cookieJar.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static boolean isBotChallengePage(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        boolean hasVerify = body.contains("正在确认你是不是机器人")
                || body.contains("浏览器安全验证")
                || body.contains("安全验证")
                || body.contains("正在进行浏览器计算验证");
        if (!hasVerify) {
            return false;
        }
        return CHALLENGE_JSON_PATTERN.matcher(body).find()
                || body.contains("powSolve-")
                || body.contains("pow.worker-")
                || body.contains("const jss=")
                || body.contains("/res/pow");
    }

    private static boolean isLoginShell(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.contains("_BT.PC.HTML('login')")
                || body.contains("_BT.PC.HTML(\"login\")")
                || body.contains("_BT.PC.HTML('nologin')")
                || body.contains("_BT.PC.HTML(\"nologin\")")
                || body.contains("未登录，访问受限");
    }

    private void solveBotChallenge(String requestUrl, String html) throws Exception {
        Matcher m = CHALLENGE_JSON_PATTERN.matcher(html);
        if (!m.find()) {
            solveRemotePowChallenge(requestUrl);
            return;
        }
        JsonNode node = objectMapper.readTree(m.group(1));
        if (node.hasNonNull("N") && node.hasNonNull("x") && node.path("t").asInt(0) > 0) {
            solveInlinePowChallenge(requestUrl, node);
            return;
        }
        solveLegacyHashChallenge(requestUrl, node);
    }

    private void solveRemotePowChallenge(String requestUrl) throws Exception {
        String powUrl = buildPowUrl(requestUrl);
        HttpResult get = sendRaw("GET", powUrl, null, null);
        if (get.status != 200) {
            throw new RuntimeException("获取PoW验证数据失败: HTTP " + get.status);
        }
        JsonNode ch = objectMapper.readTree(get.body);
        String y = computePowResult(ch.path("N").asText(), ch.path("x").asText(), ch.path("t").asInt(0));
        submitChallengeVerification(powUrl, "y=" + URLEncoder.encode(y, StandardCharsets.UTF_8));
    }

    private void solveInlinePowChallenge(String requestUrl, JsonNode challenge) throws Exception {
        String y = computePowResult(
                challenge.path("N").asText(),
                challenge.path("x").asText(),
                challenge.path("t").asInt(0));
        String form = "action=verify&id="
                + URLEncoder.encode(challenge.path("id").asText(), StandardCharsets.UTF_8)
                + "&y=" + URLEncoder.encode(y, StandardCharsets.UTF_8);
        submitChallengeVerification(requestUrl, form);
    }

    private void solveLegacyHashChallenge(String requestUrl, JsonNode challenge) throws Exception {
        String id = challenge.path("id").asText();
        int diff = challenge.path("diff").asInt(0);
        String salt = challenge.path("salt").asText();
        JsonNode challengeArr = challenge.get("challenge");
        if (id.isEmpty() || salt.isEmpty() || diff <= 0 || challengeArr == null || challengeArr.isEmpty()) {
            throw new RuntimeException("验证数据无效");
        }

        int challengeCount = challengeArr.size();
        byte[][] targets = new byte[challengeCount][];
        for (int i = 0; i < challengeCount; i++) {
            targets[i] = hexToBytes(challengeArr.get(i).asText());
        }

        int[] nonces = new int[challengeCount];
        boolean[] found = new boolean[challengeCount];
        int foundCount = 0;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        log.info("[gying] 旧版 PoW: {} 个挑战, diff={}", challengeCount, diff);

        for (int nonce = 0; nonce <= diff && foundCount < challengeCount; nonce++) {
            byte[] hash = md.digest((nonce + salt).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < challengeCount; i++) {
                if (!found[i] && Arrays.equals(hash, targets[i])) {
                    nonces[i] = nonce;
                    found[i] = true;
                    foundCount++;
                }
            }
        }
        if (foundCount < challengeCount) {
            throw new RuntimeException("无法完成机器人验证");
        }

        StringBuilder form = new StringBuilder();
        form.append("action=verify&id=").append(URLEncoder.encode(id, StandardCharsets.UTF_8));
        for (int n : nonces) {
            form.append("&nonce[]=").append(n);
        }
        submitChallengeVerification(requestUrl, form.toString());
    }

    private String computePowResult(String nHex, String xHex, int t) throws Exception {
        BigInteger modulus = new BigInteger(nHex, 16);
        BigInteger y = new BigInteger(xHex, 16);
        if (t <= 0) {
            throw new RuntimeException("PoW验证数据无效: t");
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < t; i++) {
            y = y.multiply(y).mod(modulus);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("[gying] PoW 计算完成 t={} cost={}ms", t, elapsed);
        if (elapsed < POW_MIN_SUBMIT_MS) {
            Thread.sleep(POW_MIN_SUBMIT_MS - elapsed);
        }
        return y.toString(16);
    }

    private void submitChallengeVerification(String url, String formBody) throws Exception {
        HttpResult resp = sendRaw("POST", url, formBody, "application/x-www-form-urlencoded");
        if (isBotChallengePage(resp.body)) {
            throw new RuntimeException("机器人验证出现循环");
        }
        JsonNode node = objectMapper.readTree(resp.body);
        if (!node.path("success").asBoolean(false)) {
            String msg = node.path("msg").asText("未知错误");
            throw new RuntimeException("机器人验证失败: " + msg);
        }
        log.info("[gying] 安全验证通过");
    }

    private String buildPowUrl(String requestUrl) throws Exception {
        URI uri = URI.create(requestUrl);
        return new URI(uri.getScheme(), uri.getAuthority(), "/res/pow", null, null).toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ==================== 详情 ====================

    private List<SearchResult> fetchAllDetails(SearchData searchData, String keyword) throws Exception {
        List<SearchResult> results = Collections.synchronizedList(new ArrayList<>());
        if (searchData == null || searchData.l == null || searchData.l.i == null || searchData.l.i.isEmpty()) {
            return results;
        }

        ExecutorService executor = Executors.newFixedThreadPool(detailConcurrency);
        List<Future<?>> futures = new ArrayList<>();
        String keywordLower = keyword.toLowerCase();

        for (int i = 0; i < searchData.l.i.size(); i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    if (index >= searchData.l.title.size()) {
                        return;
                    }
                    String title = searchData.l.title.get(index);
                    if (!title.toLowerCase().contains(keywordLower)) {
                        return;
                    }
                    DetailData detail = fetchDetail(searchData.l.i.get(index), searchData.l.d.get(index));
                    if (detail == null) {
                        return;
                    }
                    SearchResult result = buildResult(detail, searchData, index);
                    if (result != null && !result.links.isEmpty()) {
                        results.add(result);
                    }
                } catch (Exception ignored) {
                    // 单条详情失败忽略
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 超时/异常忽略
            }
        }
        executor.shutdownNow();
        return results;
    }

    private DetailData fetchDetail(String resourceId, String resourceType) throws Exception {
        String detailUrl = baseUrl + "/res/downurl/" + resourceType + "/" + resourceId;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                Thread.sleep(800L * attempt);
            }
            HttpResult resp = requestWithChallengeRetry("GET", detailUrl, null, null);
            if (resp.status != 200) {
                continue;
            }
            if (isLoginShell(resp.body)) {
                throw new RuntimeException("详情接口需要重新登录");
            }
            if (resp.body != null && resp.body.contains("正在确认你是不是机器人")) {
                continue;
            }
            DetailData detail = objectMapper.readValue(resp.body, DetailData.class);
            if (detail.code == 403 && resp.body != null && resp.body.contains("频繁")) {
                log.debug("[gying] 详情限流，重试 {}/3 {}", attempt + 1, detailUrl);
                continue;
            }
            if (detail.code == 403) {
                throw new RuntimeException("详情接口返回 403");
            }
            return detail;
        }
        return null;
    }

    private SearchResult buildResult(DetailData detail, SearchData searchData, int index) {
        if (index >= searchData.l.title.size()) {
            return null;
        }

        SearchResult result = new SearchResult();
        result.title = searchData.l.title.get(index);
        if (index < searchData.l.year.size() && searchData.l.year.get(index) > 0) {
            result.title = result.title + "（" + searchData.l.year.get(index) + "）";
        }
        result.links = extractLinks(detail);
        return result;
    }

    private List<PanLink> extractLinks(DetailData detail) {
        List<PanLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (detail == null) {
            return links;
        }
        extractPanLinks(detail, links, seen);
        extractMagnetLinks(detail, links, seen);
        return links;
    }

    private void extractPanLinks(DetailData detail, List<PanLink> links, Set<String> seen) {
        if (detail.panlist == null || detail.panlist.url == null) {
            return;
        }
        for (int i = 0; i < detail.panlist.url.size(); i++) {
            String url = detail.panlist.url.get(i).trim()
                    .replaceAll("（访问码：.*?）", "")
                    .replaceAll("\\(访问码：.*?\\)", "")
                    .trim();
            if (url.isEmpty()) {
                continue;
            }
            String type = determineLinkType(url);
            if ("others".equals(type)) {
                continue;
            }
            String seenKey = type + ":" + url.toLowerCase();
            if (seen.contains(seenKey)) {
                continue;
            }
            seen.add(seenKey);

            PanLink link = new PanLink();
            link.type = type;
            link.url = url;
            if (i < detail.panlist.p.size()) {
                link.password = detail.panlist.p.get(i);
            }
            String urlPwd = extractPasswordFromURL(url);
            if (urlPwd != null && !urlPwd.isEmpty()) {
                link.password = urlPwd;
            }
            if (i < detail.panlist.name.size()) {
                link.workTitle = cleanTitle(detail.panlist.name.get(i));
            }
            links.add(link);
        }
    }

    /** 磁力在 downlist.list.m（infohash），不在 panlist。对齐 pansou extractMagnetLinks。 */
    private void extractMagnetLinks(DetailData detail, List<PanLink> links, Set<String> seen) {
        if (detail.downlist == null || detail.downlist.list == null || detail.downlist.list.m == null) {
            return;
        }
        List<String> hashes = detail.downlist.list.m;
        for (int i = 0; i < hashes.size(); i++) {
            String infoHash = hashes.get(i) == null ? "" : hashes.get(i).trim().toLowerCase();
            if (!MAGNET_HASH_PATTERN.matcher(infoHash).matches()) {
                continue;
            }
            String seenKey = "magnet:" + infoHash;
            if (seen.contains(seenKey)) {
                continue;
            }
            seen.add(seenKey);

            String resourceName = safeListGet(detail.downlist.list.t, i);
            if (resourceName.isEmpty()) {
                resourceName = safeListGet(detail.downlist.list.s, i);
            }
            String magnetUrl = buildMagnetUrl(infoHash, resourceName);
            if (magnetUrl.isEmpty()) {
                continue;
            }

            PanLink link = new PanLink();
            link.type = "magnet";
            link.url = magnetUrl;
            if (!resourceName.isEmpty()) {
                link.workTitle = cleanTitle(resourceName);
            }
            links.add(link);
        }
    }

    private static String safeListGet(List<String> list, int index) {
        if (list == null || index < 0 || index >= list.size() || list.get(index) == null) {
            return "";
        }
        return list.get(index).trim();
    }

    private static String buildMagnetUrl(String infoHash, String resourceName) {
        if (!MAGNET_HASH_PATTERN.matcher(infoHash).matches()) {
            return "";
        }
        StringBuilder url = new StringBuilder("magnet:?xt=urn:btih:").append(infoHash);
        if (resourceName != null && !resourceName.isBlank()) {
            url.append("&dn=").append(URLEncoder.encode(resourceName.trim(), StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private String determineLinkType(String url) {
        String u = url.toLowerCase();
        if (u.startsWith("magnet:") || u.contains("magnet:?xt=")) {
            return "magnet";
        }
        if (u.startsWith("ed2k:")) {
            return "ed2k";
        }
        if (u.contains("pan.quark.cn")) {
            return "quark";
        }
        if (u.contains("drive.uc.cn")) {
            return "uc";
        }
        if (u.contains("pan.baidu.com")) {
            return "baidu";
        }
        if (u.contains("aliyundrive.com") || u.contains("alipan.com")) {
            return "aliyun";
        }
        if (u.contains("pan.xunlei.com")) {
            return "xunlei";
        }
        if (u.contains("cloud.189.cn")) {
            return "tianyi";
        }
        if (u.contains("115.com") || u.contains("anxia.com") || u.contains("115cdn.com")) {
            return "115";
        }
        if (u.contains("123pan.com") || u.contains("123pan.cn") || u.contains("123684.com") || u.contains("123865.com")) {
            return "123";
        }
        if (u.contains("caiyun.139.com") || u.contains("caiyun.feixin.10086.cn")) {
            return "mobile";
        }
        if (u.contains("mypikpak.com")) {
            return "pikpak";
        }
        return "others";
    }

    private String extractPasswordFromURL(String url) {
        Matcher m = Pattern.compile("\\?pwd=([a-zA-Z0-9]+)").matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("\\?password=([a-zA-Z0-9]+)").matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String cleanTitle(String title) {
        if (title == null || title.isEmpty()) {
            return title;
        }
        title = title.replaceAll("[\\p{So}\\p{Sk}\\p{Cn}]", "");
        title = title.replaceAll("【[^】]*[Pp】]】", "");
        title = title.replaceAll("【[^】]*字[幕】]】", "");
        title = title.replaceAll("【[^】]*语[^】]*】", "");
        title = title.replaceAll("【无水印】|【无广告】|【纯净[^】]*】|【收藏版】|【典藏版】|【高码[^】]*】|【[^】]*同步[^】]*】|【[^】]*更新[^】]*】|【类型：[^】]*】", "");
        title = title.replaceAll("^[━─═\\-—]+|[━─═\\-—]+$", "");
        title = title.replaceAll("\\s+", " ").trim();
        title = title.replaceAll("[━─═]{2,}", "");
        return title;
    }

    // ==================== Cookie 缓存 ====================

    private void saveCookieToCache(String username, String cookie, LocalDateTime expireTime) {
        try {
            Path cacheDir = Paths.get(cookieCacheDir);
            Files.createDirectories(cacheDir);
            Path filePath = cacheDir.resolve(generateHash(username) + ".json");

            CookieCache cache = new CookieCache();
            cache.username = username;
            cache.cookie = cookie;
            cache.baseUrl = baseUrl;
            cache.expireTime = expireTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            cache.savedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            Files.writeString(filePath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cache),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[gying] 保存 Cookie 缓存失败: {}", e.getMessage());
        }
    }

    private boolean loadCookieFromCache(String username) {
        try {
            Path filePath = Paths.get(cookieCacheDir, generateHash(username) + ".json");
            if (!Files.exists(filePath)) {
                return false;
            }

            CookieCache cache = objectMapper.readValue(Files.readString(filePath), CookieCache.class);
            if (cache.baseUrl != null && !cache.baseUrl.equals(baseUrl)) {
                Files.deleteIfExists(filePath);
                return false;
            }

            LocalDateTime expireTime = LocalDateTime.parse(cache.expireTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (LocalDateTime.now().isAfter(expireTime)) {
                Files.deleteIfExists(filePath);
                return false;
            }

            synchronized (cookieJar) {
                cookieJar.clear();
                if (cache.cookie != null) {
                    for (String part : cache.cookie.split(";\\s*")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            cookieJar.put(kv[0].trim(), kv[1].trim());
                        }
                    }
                }
                syncCookieString();
            }
            this.cookieExpireTime = expireTime;
            this.username = username;
            return cookie != null && !cookie.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteCookieCache() {
        if (username == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(cookieCacheDir, generateHash(username) + ".json"));
        } catch (Exception ignored) {
            // 删缓存失败无所谓
        }
        synchronized (cookieJar) {
            cookieJar.clear();
            cookie = "";
        }
    }

    private String generateHash(String username) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((username + "gying_salt_2026").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(username.hashCode());
        }
    }

    private static String maskUser(String username) {
        if (username == null || username.length() <= 3) {
            return username;
        }
        return username.substring(0, 2) + "***" + username.substring(username.length() - 2);
    }

    // ==================== 数据模型 ====================

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LoginResponse {
        public int code;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchData {
        public String q;
        public String n;
        public List<String> wd;
        public SearchList l;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchList {
        public List<String> title = new ArrayList<>();
        public List<Integer> year = new ArrayList<>();
        public List<String> d = new ArrayList<>();
        public List<String> i = new ArrayList<>();
        public List<String> info = new ArrayList<>();
        public List<String> daoyan = new ArrayList<>();
        public List<String> zhuyan = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DetailData {
        public int code;
        public boolean wp;
        public Downlist downlist;
        public Panlist panlist;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Downlist {
        public DownlistItems list;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DownlistItems {
        /** 磁力 infohash（40 位十六进制）。 */
        public List<String> m = new ArrayList<>();
        /** 资源名称。 */
        public List<String> t = new ArrayList<>();
        /** 文件大小描述（t 为空时兜底）。 */
        public List<String> s = new ArrayList<>();
        public List<String> n = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Panlist {
        public List<String> id = new ArrayList<>();
        public List<String> name = new ArrayList<>();
        public List<String> p = new ArrayList<>();
        public List<String> url = new ArrayList<>();
        public List<Integer> type = new ArrayList<>();
        public List<String> user = new ArrayList<>();
        public List<String> time = new ArrayList<>();
        public List<String> tname = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CookieCache {
        public String username;
        public String cookie;
        public String baseUrl;
        public String expireTime;
        public String savedAt;
    }

    public static class SearchResult {
        public String title;
        public List<PanLink> links = new ArrayList<>();
    }

    public static class PanLink {
        public String type;
        public String url;
        public String password;
        public String workTitle;
    }
}
