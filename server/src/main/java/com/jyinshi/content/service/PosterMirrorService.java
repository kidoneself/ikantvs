package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.config.R2Properties;
import com.jyinshi.common.storage.R2StorageService;
import com.jyinshi.common.storage.StoragePaths;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaSeason;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.content.mapper.MediaSeasonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 海报/背景图：从 TMDB 等远程 URL 下载 → 上传 R2（全尺寸 + 列表缩略图）→ 回写 media。
 *
 * <p>已有 {@code poster} 不变；新增 {@code poster_thumb}（JPEG ~256px）。旧数据 thumb 为空时前台降级用 poster。
 */
@Slf4j
@Service
public class PosterMirrorService {

    private static final String UA = "Mozilla/5.0 (compatible; jyinshi-next/1.0)";
    private static final String DOUBAN_REFERER = "https://movie.douban.com/";

    private final R2Properties r2Props;
    private final R2StorageService r2;
    private final MediaMapper mediaMapper;
    private final MediaSeasonMapper mediaSeasonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PosterMirrorService(R2Properties r2Props, R2StorageService r2, MediaMapper mediaMapper,
                               MediaSeasonMapper mediaSeasonMapper) {
        this.r2Props = r2Props;
        this.r2 = r2;
        this.mediaMapper = mediaMapper;
        this.mediaSeasonMapper = mediaSeasonMapper;
    }

    public boolean isEnabled() {
        return r2Props.isReady();
    }

    public boolean isOwnUrl(String url) {
        return r2Props.isOwnUrl(url);
    }

    /** 镜像 poster + backdrop；全图已在本站时仍会补缩略图。 */
    public void mirrorMediaImages(Media media) {
        if (!isEnabled() || media == null || media.getId() == null) {
            return;
        }
        boolean changed = false;
        if (StringUtils.hasText(media.getPoster())) {
            if (!r2Props.isOwnUrl(media.getPoster())) {
                MirrorResult r = mirrorPosterFull(media.getId(), media.getPoster(), media.getMetaSource());
                if (r.posterUrl() != null) {
                    media.setPoster(r.posterUrl());
                    changed = true;
                }
                if (r.thumbUrl() != null) {
                    media.setPosterThumb(r.thumbUrl());
                    changed = true;
                }
            } else if (!StringUtils.hasText(media.getPosterThumb())
                    || !r2Props.isOwnUrl(media.getPosterThumb())) {
                String thumb = ensureThumbFromUrl(media.getId(), media.getPoster(), media.getMetaSource());
                if (thumb != null) {
                    media.setPosterThumb(thumb);
                    changed = true;
                }
            }
        }
        if (StringUtils.hasText(media.getBackdrop()) && !r2Props.isOwnUrl(media.getBackdrop())) {
            String url = mirrorBackdropOnly(media.getId(), media.getBackdrop(), media.getMetaSource());
            if (url != null) {
                media.setBackdrop(url);
                changed = true;
            }
        }
        if (changed) {
            mediaMapper.updateById(media);
        }
    }

    /**
     * 批量补缩略图（并行下载/缩放/上传）。不影响 poster 字段。
     *
     * @param limit 本批最多处理条数；≤0 时用配置的 batch-size
     * @return 成功写入 thumb 的条数
     */
    public int backfillThumbs(int limit) {
        if (!isEnabled()) {
            return 0;
        }
        int batch = limit > 0 ? limit : r2Props.getBackfillBatchSize();
        batch = Math.min(batch, 1000);
        List<Media> list = mediaMapper.selectList(
                Wrappers.<Media>lambdaQuery()
                        .isNotNull(Media::getPoster)
                        .isNull(Media::getPosterThumb)
                        .last("LIMIT " + batch));
        if (list.isEmpty()) {
            return 0;
        }
        int threads = Math.max(1, Math.min(r2Props.getBackfillConcurrency(), 32));
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = list.stream()
                    .map(m -> pool.submit(() -> {
                        String thumb = ensureThumbFromUrl(m.getId(), m.getPoster(), m.getMetaSource());
                        if (thumb != null) {
                            Media patch = new Media();
                            patch.setId(m.getId());
                            patch.setPosterThumb(thumb);
                            mediaMapper.updateById(patch);
                            ok.incrementAndGet();
                        }
                    }))
                    .toList();
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("回填缩略图任务异常: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
        return ok.get();
    }

    /** import/refresh 后：把某部剧所有季海报镜像到 R2。 */
    public void mirrorSeasonPostersForMedia(Long mediaId, String metaSource) {
        if (!isEnabled() || mediaId == null) {
            return;
        }
        List<MediaSeason> seasons = mediaSeasonMapper.selectList(
                Wrappers.<MediaSeason>lambdaQuery().eq(MediaSeason::getMediaId, mediaId));
        for (MediaSeason s : seasons) {
            mirrorOneSeasonPoster(s, metaSource);
        }
    }

    /**
     * 批量补季海报镜像（仍为外链的条目）。
     *
     * @return 成功写入 R2 的条数
     */
    public int backfillSeasonPosters(int limit) {
        if (!isEnabled()) {
            return 0;
        }
        int batch = limit > 0 ? limit : r2Props.getBackfillBatchSize();
        batch = Math.min(batch, 1000);
        List<MediaSeason> list = mediaSeasonMapper.selectList(
                Wrappers.<MediaSeason>lambdaQuery()
                        .isNotNull(MediaSeason::getPoster)
                        .like(MediaSeason::getPoster, "%tmdb.org%")
                        .last("LIMIT " + batch));
        AtomicInteger ok = new AtomicInteger();
        int threads = Math.max(1, Math.min(r2Props.getBackfillConcurrency(), 32));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = list.stream()
                    .filter(s -> !r2Props.isOwnUrl(s.getPoster()))
                    .map(s -> pool.submit(() -> {
                        if (mirrorOneSeasonPoster(s, "tmdb")) {
                            ok.incrementAndGet();
                        }
                    }))
                    .toList();
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("回填季海报任务异常: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
        return ok.get();
    }

    private boolean mirrorOneSeasonPoster(MediaSeason season, String metaSource) {
        if (season == null || season.getId() == null || !StringUtils.hasText(season.getPoster())) {
            return false;
        }
        if (r2Props.isOwnUrl(season.getPoster())) {
            return false;
        }
        Optional<DownloadedImage> img = download(season.getPoster(), metaSource);
        if (img.isEmpty()) {
            return false;
        }
        DownloadedImage d = img.get();
        String key = StoragePaths.seasonPoster(season.getMediaId(), season.getSeasonNumber(), d.ext());
        String url = r2.upload(key, d.data(), d.contentType());
        if (url == null) {
            return false;
        }
        MediaSeason patch = new MediaSeason();
        patch.setId(season.getId());
        patch.setPoster(url);
        mediaSeasonMapper.updateById(patch);
        return true;
    }

    private MirrorResult mirrorPosterFull(Long mediaId, String remoteUrl, String metaSource) {
        Optional<DownloadedImage> img = download(remoteUrl, metaSource);
        if (img.isEmpty()) {
            return MirrorResult.empty();
        }
        DownloadedImage d = img.get();
        String posterKey = StoragePaths.keyed(StoragePaths.POSTERS, mediaId, d.ext());
        String posterUrl = r2.upload(posterKey, d.data(), d.contentType());
        String thumbUrl = uploadThumb(mediaId, d.data());
        return new MirrorResult(posterUrl, thumbUrl);
    }

    private String mirrorBackdropOnly(Long mediaId, String remoteUrl, String metaSource) {
        Optional<DownloadedImage> img = download(remoteUrl, metaSource);
        if (img.isEmpty()) {
            return null;
        }
        DownloadedImage d = img.get();
        String key = StoragePaths.keyed(StoragePaths.BACKDROPS, mediaId, d.ext());
        return r2.upload(key, d.data(), d.contentType());
    }

    private String ensureThumbFromUrl(Long mediaId, String imageUrl, String metaSource) {
        Optional<DownloadedImage> img = download(imageUrl, metaSource);
        if (img.isEmpty()) {
            return null;
        }
        return uploadThumb(mediaId, img.get().data());
    }

    private String uploadThumb(Long mediaId, byte[] sourceBytes) {
        Optional<byte[]> jpeg = PosterThumbGenerator.toJpeg(sourceBytes, r2Props.getThumbWidth());
        if (jpeg.isEmpty()) {
            return null;
        }
        String key = StoragePaths.keyed(StoragePaths.POSTER_THUMBS, mediaId, "jpg");
        return r2.upload(key, jpeg.get(), "image/jpeg");
    }

    private Optional<DownloadedImage> download(String url, String metaSource) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .timeout(Duration.ofMillis(r2Props.getDownloadTimeoutMs()))
                    .GET();
            if (url.contains("doubanio.com") || "douban".equals(metaSource)) {
                b.header("Referer", DOUBAN_REFERER);
            }
            HttpResponse<byte[]> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                log.warn("海报下载失败 {} status={}", url, resp.statusCode());
                return Optional.empty();
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                return Optional.empty();
            }
            String contentType = resp.headers().firstValue("Content-Type").orElse(guessContentType(url));
            return Optional.of(new DownloadedImage(body, contentType));
        } catch (Exception e) {
            log.warn("海报下载异常 {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private static String guessContentType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private record DownloadedImage(byte[] data, String contentType) {
        String ext() {
            if (contentType.contains("webp")) {
                return "webp";
            }
            if (contentType.contains("png")) {
                return "png";
            }
            return "jpg";
        }
    }

    private record MirrorResult(String posterUrl, String thumbUrl) {
        static MirrorResult empty() {
            return new MirrorResult(null, null);
        }
    }
}
