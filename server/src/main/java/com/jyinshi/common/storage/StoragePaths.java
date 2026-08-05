package com.jyinshi.common.storage;

/**
 * R2 对象 key 前缀约定（单 bucket、多目录）。
 *
 * <p>所有对外 URL 形如 {@code {publicBase}/{prefix}/{id}.{ext}}，由 {@link R2StorageService} 统一上传。
 * 新业务加前缀即可，勿另起 bucket / 另写 S3 客户端。
 */
public final class StoragePaths {

    /** 影视海报（content 域，PosterMirrorService，约 w500） */
    public static final String POSTERS = "posters";

    /** 列表缩略图（JPEG，约 256px 宽） */
    public static final String POSTER_THUMBS = "poster-thumbs";

    /** 季海报（content 域 media_season） */
    public static final String SEASON_POSTERS = "season-posters";

    /** 影视背景图（content 域） */
    public static final String BACKDROPS = "backdrops";

    /** 用户头像（identity 域，未实现） */
    public static final String AVATARS = "avatars";

    /** 运营附件（历史 R2 key；公告图已改走本机 {@code /api/uploads}，勿再上传 R2）。 */
    public static final String UPLOADS = "uploads";

    private StoragePaths() {
    }

    public static String keyed(String prefix, long id, String ext) {
        return prefix + "/" + id + "." + ext;
    }

    public static String seasonPoster(long mediaId, int seasonNumber, String ext) {
        return SEASON_POSTERS + "/" + mediaId + "-" + seasonNumber + "." + ext;
    }
}
