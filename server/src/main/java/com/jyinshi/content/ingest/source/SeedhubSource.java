package com.jyinshi.content.ingest.source;

import com.jyinshi.content.ingest.IngestPanFilter;
import com.jyinshi.content.ingest.IngestProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SeedHub 来源插件：按需搜 {@code /s/{kw}/ → /movies/{id}/ → link_start → 网盘链接}。
 *
 * <p>只做「找链接」，产出 {@link RawLink} 交给 {@code IngestService}。归属识别/去重/入库/检测都不管。
 * link_start 的 pan_id→URL 跳转结果走 Redis 缓存，减少对站点的重复请求。
 */
@Slf4j
@Component
public class SeedhubSource implements LinkSource {

    private static final String SOURCE = "seedhub";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern COVER_BLOCK =
            Pattern.compile("<div class=\"cover\">(.*?)</div>\\s*\\n\\s*</div>", Pattern.DOTALL);
    private static final Pattern MOVIE_ID = Pattern.compile("/movies/(\\d+)/");
    private static final Pattern PAN_TAG = Pattern.compile(
            "<a([^>]*redirect_to=pan_id_(\\d+)[^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_LINK = Pattern.compile("data-link=\"([^\"]+)\"");
    private static final Pattern TITLE_ATTR = Pattern.compile("title=\"([^\"]+)\"");
    private static final Pattern H2_TITLE = Pattern.compile("<h2[^>]*>.*?</a>\\s*(.*?)</h2>", Pattern.DOTALL);

    private static final String TYPE_MAGNET = "magnet";

    private static final Map<String, String> HOST_TO_TYPE = Map.ofEntries(
            Map.entry("pan.quark.cn", "quark"),
            Map.entry("pan.baidu.com", "baidu"),
            Map.entry("pan.xunlei.com", "xunlei"),
            Map.entry("drive.uc.cn", "uc"),
            Map.entry("alipan.com", "aliyun"),
            Map.entry("aliyundrive.com", "aliyun"),
            Map.entry("cloud.189.cn", "tianyi"),
            Map.entry("115.com", "115"),
            Map.entry("123pan.com", "123"),
            Map.entry("caiyun.139.com", "mobile")
    );

    private static final Map<String, Pattern> URL_PATTERNS = Map.ofEntries(
            Map.entry("quark", Pattern.compile("https?://pan\\.quark\\.cn/s/[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("baidu", Pattern.compile("https?://pan\\.baidu\\.com/s/[A-Za-z0-9_-]+(?:\\?pwd=[A-Za-z0-9]+)?", Pattern.CASE_INSENSITIVE)),
            Map.entry("xunlei", Pattern.compile("https?://pan\\.xunlei\\.com/s/[A-Za-z0-9_-]+(?:\\?pwd=[A-Za-z0-9]+)?", Pattern.CASE_INSENSITIVE)),
            Map.entry("uc", Pattern.compile("https?://drive\\.uc\\.cn/s/[^\\s\"']+", Pattern.CASE_INSENSITIVE)),
            Map.entry("aliyun", Pattern.compile("https?://(?:www\\.)?(?:alipan|aliyundrive)\\.com/s/[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("tianyi", Pattern.compile("https?://cloud\\.189\\.cn/(?:t/|web/share\\?)[^\\s\"'<]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("115", Pattern.compile("https?://(?:115|anxia)\\.com/s/[^\\s\"'<]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("123", Pattern.compile("https?://(?:www\\.)?123pan\\.(?:com|cn)/s/[^\\s\"'<]+", Pattern.CASE_INSENSITIVE)),
            Map.entry("mobile", Pattern.compile("https?://caiyun\\.139\\.com/[^\\s\"'<]+", Pattern.CASE_INSENSITIVE))
    );

    private static final Pattern MAGNET_PATTERN =
            Pattern.compile("magnet:\\?xt=urn:btih:[A-Za-z0-9]+[^\\s\"'<]*", Pattern.CASE_INSENSITIVE);

    private final IngestProperties props;
    private final IngestPanFilter panFilter;
    private final StringRedisTemplate redis;

    public SeedhubSource(IngestProperties props, IngestPanFilter panFilter, StringRedisTemplate redis) {
        this.props = props;
        this.panFilter = panFilter;
        this.redis = redis;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && props.getSeedhub().isEnabled();
    }

    @Override
    public List<RawLink> search(String keyword) {
        IngestProperties.Seedhub cfg = props.getSeedhub();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        if (cfg.isSkipNonChinese() && !containsChinese(kw)) {
            return List.of();
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, cfg.getTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        Set<String> wantTypes = resolveWantTypes(cfg.getPanTypes());
        if (wantTypes.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, cfg.getLinkStartConcurrency()));
        long t0 = System.currentTimeMillis();

        try {
            String searchPath = "/s/" + URLEncoder.encode(kw, StandardCharsets.UTF_8) + "/";
            String searchHtml = get(http, cfg, searchPath, cfg.getBaseUrl() + "/");
            List<MovieBrief> candidates = parseListPage(searchHtml);
            if (candidates.isEmpty()) {
                return List.of();
            }
            List<MovieBrief> picks = pickTitles(cfg, kw, candidates);
            if (picks.isEmpty()) {
                return List.of();
            }

            Map<String, RawLink> uniqueByUrl = new LinkedHashMap<>();
            for (MovieBrief pick : picks) {
                String detailPath = "/movies/" + pick.movieId + "/";
                String detailHtml = get(http, cfg, detailPath, cfg.getBaseUrl() + searchPath);

                // 磁力：详情页直接给出，无需走 link_start 跳转
                if (wantTypes.contains(TYPE_MAGNET)) {
                    Matcher mag = MAGNET_PATTERN.matcher(detailHtml);
                    while (mag.find()) {
                        String magnet = mag.group(0);
                        uniqueByUrl.putIfAbsent(magnet,
                                RawLink.of(TYPE_MAGNET, magnet, null, pick.title, SOURCE));
                    }
                }

                List<PanCandidate> panCands = parsePanCandidates(detailHtml, wantTypes);
                if (panCands.isEmpty()) {
                    continue;
                }
                String referer = cfg.getBaseUrl() + detailPath;
                List<CompletableFuture<RawLink>> futures = new ArrayList<>();
                for (PanCandidate cand : panCands) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            String url = resolvePanUrl(http, cfg, cand, referer);
                            if (!StringUtils.hasText(url)) {
                                return null;
                            }
                            String note = pick.title;
                            if (StringUtils.hasText(cand.title)) {
                                note = note + " · " + cand.title;
                            }
                            return RawLink.of(determineLinkType(url), url, extractPassword(url), note, SOURCE);
                        } catch (Exception e) {
                            log.debug("[ingest] seedhub link_start 失败 panId={}: {}", cand.panId, e.getMessage());
                            return null;
                        }
                    }, pool));
                }
                for (CompletableFuture<RawLink> f : futures) {
                    try {
                        RawLink r = f.get(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
                        if (r != null && StringUtils.hasText(r.getPanType())
                                && wantTypes.contains(r.getPanType())) {
                            uniqueByUrl.putIfAbsent(r.getUrl(), r);
                        }
                    } catch (Exception ignored) {
                        // 单条超时/失败忽略
                    }
                }
            }

            if (!uniqueByUrl.isEmpty()) {
                log.info("[ingest] seedhub 命中 kw={} 结果={} 耗时={}ms",
                        kw, uniqueByUrl.size(), System.currentTimeMillis() - t0);
            }
            return new ArrayList<>(uniqueByUrl.values());
        } catch (Exception e) {
            log.warn("[ingest] seedhub 搜索失败 kw={}: {}", kw, e.getMessage());
            return List.of();
        } finally {
            pool.shutdownNow();
        }
    }

    private String resolvePanUrl(HttpClient http, IngestProperties.Seedhub cfg,
                                 PanCandidate cand, String referer) throws Exception {
        String cacheKey = "jyinshi:seedhub:pan:" + cand.panId;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        } catch (Exception ignore) {
            // Redis 不可用照常跳转
        }
        String q = "redirect_to=pan_id_" + cand.panId + "&movie_title=x";
        String page = get(http, cfg, "/link_start/?" + q, referer);
        String url = extractPanUrl(page, cand.panType);
        if (StringUtils.hasText(url)) {
            try {
                redis.opsForValue().set(cacheKey, url, Duration.ofHours(Math.max(1, cfg.getPanLinkTtlHours())));
            } catch (Exception ignore) {
                // 缓存失败无所谓
            }
        }
        return url;
    }

    private String get(HttpClient http, IngestProperties.Seedhub cfg, String path, String referer) throws Exception {
        String url = path.startsWith("http") ? path : cfg.getBaseUrl() + path;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", referer != null ? referer : cfg.getBaseUrl() + "/")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private static List<MovieBrief> parseListPage(String html) {
        Map<Integer, MovieBrief> byId = new LinkedHashMap<>();
        Matcher blockM = COVER_BLOCK.matcher(html);
        while (blockM.find()) {
            String block = blockM.group(1);
            Matcher idM = MOVIE_ID.matcher(block);
            if (!idM.find()) {
                continue;
            }
            int movieId = Integer.parseInt(idM.group(1));
            byId.put(movieId, new MovieBrief(movieId, extractTitleFromBlock(block, movieId)));
        }
        Matcher allIds = MOVIE_ID.matcher(html);
        while (allIds.find()) {
            int movieId = Integer.parseInt(allIds.group(1));
            byId.putIfAbsent(movieId, new MovieBrief(movieId, "id:" + movieId));
        }
        return new ArrayList<>(byId.values());
    }

    private static String extractTitleFromBlock(String block, int movieId) {
        Matcher m = H2_TITLE.matcher(block);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", " ").trim();
        }
        return "id:" + movieId;
    }

    private List<MovieBrief> pickTitles(IngestProperties.Seedhub cfg, String keyword, List<MovieBrief> items) {
        int limit = Math.min(cfg.getMaxListCandidates(), items.size());
        List<ScoredMovie> scored = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            MovieBrief m = items.get(i);
            scored.add(new ScoredMovie(m, scoreTitle(keyword, m.title)));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        if (scored.isEmpty()) {
            return List.of();
        }

        List<MovieBrief> picks = new ArrayList<>();
        picks.add(scored.get(0).movie);
        if (cfg.getMaxDetailFetch() >= 2 && scored.size() >= 2) {
            ScoredMovie top1 = scored.get(0);
            ScoredMovie top2 = scored.get(1);
            boolean ambiguous = (top1.score - top2.score) < cfg.getScoreGapForTop2()
                    || (top1.movie.title.contains("年番") && !keyword.contains("年番"))
                    || (top1.movie.title.matches(".*第\\d+.*") && !keyword.matches(".*第\\d+.*"));
            if (ambiguous && top2.movie.movieId != top1.movie.movieId) {
                picks.add(top2.movie);
            }
        }
        return picks.stream().limit(cfg.getMaxDetailFetch()).collect(Collectors.toList());
    }

    private static double scoreTitle(String keyword, String title) {
        if (keyword == null || title == null) {
            return 0;
        }
        String kw = keyword.trim().toLowerCase();
        String t = title.trim().toLowerCase();
        if (kw.isEmpty() || t.isEmpty()) {
            return 0;
        }
        if (kw.equals(t)) {
            return 100;
        }
        if (t.contains(kw)) {
            return 80 + Math.min(10, kw.length() * 10.0 / Math.max(t.length(), 1));
        }
        if (kw.contains(t)) {
            return 70;
        }
        int common = 0;
        for (char c : kw.toCharArray()) {
            if (t.indexOf(c) >= 0) {
                common++;
            }
        }
        return common * 60.0 / Math.max(kw.length(), 1);
    }

    private static List<PanCandidate> parsePanCandidates(String html, Set<String> wants) {
        Map<String, PanCandidate> picked = new LinkedHashMap<>();
        Matcher m = PAN_TAG.matcher(html);
        while (m.find()) {
            String tag = m.group(1);
            int panId = Integer.parseInt(m.group(2));
            Matcher dl = DATA_LINK.matcher(tag);
            if (!dl.find()) {
                continue;
            }
            String host = dl.group(1).trim().toLowerCase();
            String panType = null;
            for (Map.Entry<String, String> e : HOST_TO_TYPE.entrySet()) {
                if (host.contains(e.getKey())) {
                    panType = e.getValue();
                    break;
                }
            }
            if (panType == null || !wants.contains(panType) || picked.containsKey(panType)) {
                continue;
            }
            Matcher tm = TITLE_ATTR.matcher(tag);
            String title = tm.find() ? tm.group(1) : "";
            picked.put(panType, new PanCandidate(panType, panId, title));
        }
        List<PanCandidate> out = new ArrayList<>();
        for (String w : wants) {
            if (picked.containsKey(w)) {
                out.add(picked.get(w));
            }
        }
        return out;
    }

    private static String extractPanUrl(String html, String panType) {
        Pattern pat = URL_PATTERNS.get(panType);
        if (pat == null) {
            return null;
        }
        Matcher m = pat.matcher(html);
        return m.find() ? m.group(0) : null;
    }

    private static String extractPassword(String url) {
        int idx = url.indexOf("pwd=");
        if (idx < 0) {
            return null;
        }
        String pwd = url.substring(idx + 4).split("[&#]")[0];
        return StringUtils.hasText(pwd) ? pwd : null;
    }

    private static String determineLinkType(String url) {
        if (url == null) {
            return null;
        }
        String u = url.toLowerCase();
        if (u.startsWith("magnet:")) {
            return TYPE_MAGNET;
        }
        if (u.contains("pan.quark.cn")) {
            return "quark";
        }
        if (u.contains("pan.baidu.com")) {
            return "baidu";
        }
        if (u.contains("pan.xunlei.com")) {
            return "xunlei";
        }
        if (u.contains("drive.uc.cn")) {
            return "uc";
        }
        if (u.contains("alipan.com") || u.contains("aliyundrive.com")) {
            return "aliyun";
        }
        if (u.contains("cloud.189.cn")) {
            return "tianyi";
        }
        if (u.contains("115.com") || u.contains("anxia.com")) {
            return "115";
        }
        if (u.contains("123pan.com") || u.contains("123pan.cn")) {
            return "123";
        }
        if (u.contains("caiyun.139.com")) {
            return "mobile";
        }
        return null;
    }

    private static boolean containsChinese(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveWantTypes(String ymlPanTypes) {
        Set<String> allowed = panFilter.resolve(ymlPanTypes);
        if (allowed == null) {
            Set<String> all = new LinkedHashSet<>(HOST_TO_TYPE.values());
            all.add(TYPE_MAGNET);
            return all;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String t : allowed) {
            if (HOST_TO_TYPE.containsValue(t) || TYPE_MAGNET.equals(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static final class MovieBrief {
        final int movieId;
        final String title;

        MovieBrief(int movieId, String title) {
            this.movieId = movieId;
            this.title = title;
        }
    }

    private static final class PanCandidate {
        final String panType;
        final int panId;
        final String title;

        PanCandidate(String panType, int panId, String title) {
            this.panType = panType;
            this.panId = panId;
            this.title = title;
        }
    }

    private static final class ScoredMovie {
        final MovieBrief movie;
        final double score;

        ScoredMovie(MovieBrief movie, double score) {
            this.movie = movie;
            this.score = score;
        }
    }
}
