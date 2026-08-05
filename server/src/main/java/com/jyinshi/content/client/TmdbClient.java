package com.jyinshi.content.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.ops.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TMDB 客户端。主采集源。
 *
 * <p>v3 API：详情 /movie/{id} /tv/{id}（append credits 拿演职员）；搜索 /search/multi。
 * 接口地址/Key/图片基址等全部走 {@link SysConfigService}，后台可改；国内部署把接口地址指向反代即可。
 */
@Slf4j
@Component
public class TmdbClient {

    private final SysConfigService config;
    private final ObjectMapper mapper;

    public TmdbClient(SysConfigService config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    private String apiKey() {
        return config.getOrDefault(SysConfigService.META_TMDB_API_KEY, "");
    }

    private String baseUrl() {
        return config.getOrDefault(SysConfigService.META_TMDB_BASE_URL, "https://api.themoviedb.org/3");
    }

    private String language() {
        return config.getOrDefault(SysConfigService.META_TMDB_LANGUAGE, "zh-CN");
    }

    private int timeoutMs() {
        return config.getInt(SysConfigService.META_TMDB_TIMEOUT_MS, 8000);
    }

    private String imageBase() {
        return config.getOrDefault(SysConfigService.META_TMDB_IMAGE_BASE, "https://image.tmdb.org/t/p/w500");
    }

    private String backdropBase() {
        return config.getOrDefault(SysConfigService.META_TMDB_BACKDROP_BASE, "https://image.tmdb.org/t/p/w780");
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey());
    }

    /** 从 TMDB 详情页 URL 解析 [type, id]：themoviedb.org/movie/278-xxx 或 /tv/1396。 */
    public static String[] parseRef(String url) {
        if (!StringUtils.hasText(url) || !url.contains("themoviedb.org")) {
            return null;
        }
        Matcher mt = Pattern.compile("themoviedb\\.org/(movie|tv)/(\\d+)").matcher(url);
        if (mt.find()) {
            return new String[]{mt.group(1), mt.group(2)};
        }
        return null;
    }

    /**
     * 按 tmdbId 抓详情。type 决定 movie/tv 端点；anime/variety 视作 tv。
     * type 为空时先试 movie 再试 tv。
     */
    public FetchedMetadata fetchById(int tmdbId, String type) {
        if (!isConfigured()) {
            return null;
        }
        if (type == null || type.isBlank()) {
            FetchedMetadata m = fetchDetail(tmdbId, true, "movie");
            return m != null ? m : fetchDetail(tmdbId, false, "tv");
        }
        boolean isMovie = "movie".equalsIgnoreCase(type);
        return fetchDetail(tmdbId, isMovie, type);
    }

    /** 从 TMDB 详情取 IMDb id（tt…），用于录入时反查豆瓣。 */
    public String fetchImdbId(int tmdbId, String type) {
        if (!isConfigured()) {
            return null;
        }
        if (isTvLike(type)) {
            String imdb = readImdbFromExternalIds(tmdbId);
            if (StringUtils.hasText(imdb)) {
                return imdb;
            }
        }
        if (type == null || type.isBlank() || "movie".equalsIgnoreCase(type)) {
            String imdb = readImdbFromMovieDetail(tmdbId);
            if (StringUtils.hasText(imdb)) {
                return imdb;
            }
        }
        if (type == null || type.isBlank()) {
            return readImdbFromExternalIds(tmdbId);
        }
        return null;
    }

    /** 发现结果引用：类型 + tmdbId，供定时拉新逐条采集。 */
    public record Ref(String type, int tmdbId) {
    }

    /** 本周/当日全站趋势（电影 + 剧集），media_type 决定类型。 */
    public List<Ref> trending(String window, int pages) {
        String w = ("day".equalsIgnoreCase(window)) ? "day" : "week";
        return collectRefs("/trending/all/" + w, null, pages, null);
    }

    /** 正在播出的剧集（热播剧的主要来源）。 */
    public List<Ref> onTheAirTv(int pages) {
        return collectRefs("/tv/on_the_air", "tv", pages, null);
    }

    /** 正在上映的电影；region 可选（如 CN/US），空则全球。 */
    public List<Ref> nowPlayingMovies(String region, int pages) {
        String extra = StringUtils.hasText(region) ? "&region=" + region.trim() : "";
        return collectRefs("/movie/now_playing", "movie", pages, extra);
    }

    /**
     * 拉取列表型端点的多页结果，抽出 [type, tmdbId]。
     * forcedType 为空时读每条的 media_type（trending 用），否则强制该类型（now_playing/on_the_air）。
     */
    private List<Ref> collectRefs(String path, String forcedType, int pages, String extraQuery) {
        List<Ref> out = new ArrayList<>();
        if (!isConfigured()) {
            return out;
        }
        int total = Math.max(1, pages);
        for (int page = 1; page <= total; page++) {
            String url = baseUrl() + path + "?api_key=" + apiKey()
                    + "&language=" + language()
                    + "&include_adult=false&page=" + page
                    + (extraQuery == null ? "" : extraQuery);
            JsonNode root = getJson(url);
            if (root == null) {
                break;
            }
            for (JsonNode n : root.path("results")) {
                String mt = forcedType != null ? forcedType : n.path("media_type").asText("");
                if (!"movie".equals(mt) && !"tv".equals(mt)) {
                    continue;
                }
                int id = n.path("id").asInt(0);
                if (id > 0) {
                    out.add(new Ref(mt, id));
                }
            }
        }
        return out;
    }

    /** 多类型搜索，返回轻量候选（标题/年份/海报/类型/tmdbId），供补录匹配用。 */
    public List<FetchedMetadata> searchMulti(String query) {
        List<FetchedMetadata> out = new ArrayList<>();
        if (!isConfigured() || !StringUtils.hasText(query)) {
            return out;
        }
        String url = baseUrl() + "/search/multi?api_key=" + apiKey()
                + "&language=" + language()
                + "&include_adult=false&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonNode root = getJson(url);
        if (root == null) {
            return out;
        }
        for (JsonNode n : root.path("results")) {
            String mediaType = n.path("media_type").asText("");
            if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) {
                continue;
            }
            boolean isMovie = "movie".equals(mediaType);
            FetchedMetadata m = new FetchedMetadata();
            m.setSource("tmdb");
            m.setTmdbId(n.path("id").asInt());
            m.setType(mediaType);
            m.setTitle(text(n, isMovie ? "title" : "name"));
            m.setOriginalTitle(text(n, isMovie ? "original_title" : "original_name"));
            String date = text(n, isMovie ? "release_date" : "first_air_date");
            m.setReleaseDate(date);
            m.setYear(parseYear(date));
            m.setPoster(posterUrl(text(n, "poster_path")));
            m.setRating(decimal(n, "vote_average"));
            m.setOverview(text(n, "overview"));
            out.add(m);
        }
        return out;
    }

    // ---------------- 内部 ----------------

    private static boolean isTvLike(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String t = type.toLowerCase();
        return "tv".equals(t) || "anime".equals(t) || "variety".equals(t);
    }

    private String readImdbFromMovieDetail(int tmdbId) {
        String url = baseUrl() + "/movie/" + tmdbId + "?api_key=" + apiKey();
        JsonNode n = getJson(url);
        if (n == null || n.has("status_code")) {
            return null;
        }
        return normalizeImdbId(text(n, "imdb_id"));
    }

    private String readImdbFromExternalIds(int tmdbId) {
        String url = baseUrl() + "/tv/" + tmdbId + "/external_ids?api_key=" + apiKey();
        JsonNode n = getJson(url);
        if (n == null || n.has("status_code")) {
            return null;
        }
        return normalizeImdbId(text(n, "imdb_id"));
    }

    private static String normalizeImdbId(String imdb) {
        if (!StringUtils.hasText(imdb) || "null".equalsIgnoreCase(imdb)) {
            return null;
        }
        return imdb.trim();
    }

    private FetchedMetadata fetchDetail(int tmdbId, boolean isMovie, String keepType) {
        String path = isMovie ? "/movie/" : "/tv/";
        String url = baseUrl() + path + tmdbId + "?api_key=" + apiKey()
                + "&language=" + language() + "&append_to_response=credits";
        JsonNode n = getJson(url);
        if (n == null || n.has("status_code")) {
            return null;
        }
        FetchedMetadata m = new FetchedMetadata();
        m.setSource("tmdb");
        m.setTmdbId(tmdbId);
        m.setType(keepType);
        m.setTitle(text(n, isMovie ? "title" : "name"));
        m.setOriginalTitle(text(n, isMovie ? "original_title" : "original_name"));
        String date = text(n, isMovie ? "release_date" : "first_air_date");
        m.setReleaseDate(date);
        m.setYear(parseYear(date));
        m.setPoster(posterUrl(text(n, "poster_path")));
        m.setBackdrop(backdropUrl(text(n, "backdrop_path")));
        m.setRating(decimal(n, "vote_average"));
        m.setPopularity(popularity(n));
        m.setOverview(text(n, "overview"));
        m.setGenres(joinNames(n.path("genres"), "name"));
        m.setCountry(joinCountry(n));
        if (!isMovie) {
            int ep = n.path("number_of_episodes").asInt(0);
            m.setEpisodeCount(ep > 0 ? ep : null);
            applyTvSeriesFields(m, n);
        }
        JsonNode credits = n.path("credits");
        m.setActors(joinCast(credits.path("cast"), 8));
        m.setDirectors(joinDirectors(credits.path("crew")));
        return m;
    }

    /** 解析 TV 季列表与连载字段（跳过 season_number=0 的 Specials）。 */
    private void applyTvSeriesFields(FetchedMetadata m, JsonNode n) {
        List<FetchedSeason> seasons = new ArrayList<>();
        FetchedSeason specialsOnly = null;
        for (JsonNode s : n.path("seasons")) {
            int sn = s.path("season_number").asInt(-1);
            if (sn <= 0) {
                int ec = s.path("episode_count").asInt(0);
                if (ec > 0 && specialsOnly == null) {
                    specialsOnly = toFetchedSeason(s, 1);
                }
                continue;
            }
            seasons.add(toFetchedSeason(s, sn));
        }
        // TMDB 仅有 Specials（季 0）时，按「第 1 季」展示，避免 season_count 永远为空。
        if (seasons.isEmpty() && specialsOnly != null) {
            seasons.add(specialsOnly);
        }
        m.setSeasons(seasons);
        // 0 = TMDB 无季拆分（占位条目），与 NULL（未同步）区分。
        m.setSeasonCount(seasons.isEmpty() ? 0 : seasons.size());
        m.setSeriesStatus(text(n, "status"));
        m.setInProduction(n.path("in_production").asBoolean(false));

        JsonNode lastEp = n.path("last_episode_to_air");
        if (lastEp != null && !lastEp.isNull() && !lastEp.isMissingNode()) {
            m.setLastAirDate(text(lastEp, "air_date"));
            int lsn = lastEp.path("season_number").asInt(0);
            int len = lastEp.path("episode_number").asInt(0);
            m.setLastSeasonNumber(lsn > 0 ? lsn : (specialsOnly != null ? 1 : null));
            m.setLastEpisodeNumber(len > 0 ? len : null);
        }
    }

    private FetchedSeason toFetchedSeason(JsonNode s, int seasonNumber) {
        FetchedSeason fs = new FetchedSeason();
        fs.setSeasonNumber(seasonNumber);
        int tmdbSeasonId = s.path("id").asInt(0);
        fs.setTmdbSeasonId(tmdbSeasonId > 0 ? tmdbSeasonId : null);
        fs.setName(text(s, "name"));
        int ec = s.path("episode_count").asInt(0);
        fs.setEpisodeCount(ec > 0 ? ec : null);
        fs.setAirDate(text(s, "air_date"));
        fs.setPoster(posterUrl(text(s, "poster_path")));
        fs.setOverview(text(s, "overview"));
        return fs;
    }

    private JsonNode getJson(String url) {
        try (HttpResponse resp = HttpRequest.get(url)
                .timeout(timeoutMs())
                .execute()) {
            if (!resp.isOk()) {
                log.warn("TMDB 请求非 200: {} {}", resp.getStatus(), url.replaceAll("api_key=[^&]+", "api_key=***"));
                return null;
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("TMDB 请求失败: {}", e.getMessage());
            return null;
        }
    }

    private String posterUrl(String path) {
        return StringUtils.hasText(path) ? imageBase() + path : null;
    }

    private String backdropUrl(String path) {
        return StringUtils.hasText(path) ? backdropBase() + path : null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asDouble() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(Math.round(v.asDouble() * 10) / 10.0);
    }

    /** TMDB popularity 是浮点人气值，取整存入 hot；缺失/异常返回 null。 */
    private static Integer popularity(JsonNode n) {
        JsonNode v = n.get("popularity");
        if (v == null || v.isNull() || v.asDouble() <= 0) {
            return null;
        }
        return (int) Math.round(v.asDouble());
    }

    private static Integer parseYear(String date) {
        if (date != null && date.length() >= 4) {
            try {
                return Integer.parseInt(date.substring(0, 4));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static String joinNames(JsonNode arr, String field) {
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode n : arr) {
            String v = text(n, field);
            if (StringUtils.hasText(v)) {
                names.add(v);
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private static String joinCountry(JsonNode n) {
        // movie: production_countries[].iso_3166_1; tv: origin_country[]
        JsonNode pc = n.path("production_countries");
        if (pc.isArray() && pc.size() > 0) {
            return joinNames(pc, "iso_3166_1");
        }
        JsonNode oc = n.path("origin_country");
        if (oc.isArray() && oc.size() > 0) {
            List<String> c = new ArrayList<>();
            oc.forEach(x -> c.add(x.asText()));
            return String.join(",", c);
        }
        return null;
    }

    private static String joinCast(JsonNode cast, int limit) {
        if (cast == null || !cast.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode c : cast) {
            String v = text(c, "name");
            if (StringUtils.hasText(v)) {
                names.add(v);
            }
            if (names.size() >= limit) {
                break;
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private static String joinDirectors(JsonNode crew) {
        if (crew == null || !crew.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode c : crew) {
            if ("Director".equals(text(c, "job"))) {
                String v = text(c, "name");
                if (StringUtils.hasText(v)) {
                    names.add(v);
                }
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }
}
