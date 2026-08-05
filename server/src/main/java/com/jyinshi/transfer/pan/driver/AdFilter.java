package com.jyinshi.transfer.pan.driver;

import java.util.Locale;
import java.util.Set;

/**
 * 广告/垃圾文件识别（首转与追更增量共用一份，避免规则漂移）。
 *
 * <p>本服务只做「影视资源」转存，真正内容就是视频文件。因此策略：<br>
 * 1) 命中广告关键词 → 广告；<br>
 * 2) {@code .url/.lnk} 快捷方式 → 广告；<br>
 * 3) 非视频的小体积图片/文本/网页类文件（.jpg/.png/.txt/.html…）→ 广告
 *    （影视分享里这类基本是"扫码进群/失效说明/封面"，靠关键词枚举追不全，按类型+体积兜底）。</p>
 *
 * <p>宁可多删几个说明图，也不放广告进用户网盘；正片是视频，永远不会误删。</p>
 */
public final class AdFilter {

    private AdFilter() {
    }

    /** 广告关键词（命中即广告，文件/文件夹都算）。 */
    private static final String[] KEYWORDS = {
            "广告", "推广", "资源获取", "更多资源", "热门资源", "更多影视", "福利", "加群", "进群",
            "入群", "微信", "公众号", "二维码", "防失联", "失联", "失效", "最新地址", "永久地址",
            "备用地址", "请转存", "资源合集", "点我", "关注", "扫码", "客服", "投稿", "求片",
            "打赏", "官网", "网址", "域名", "频道", "telegram", "观影", "追剧", "看更多"
    };

    /** 视频扩展名（白名单：这些永远不当广告）。 */
    private static final Set<String> VIDEO_EXT = Set.of(
            ".mp4", ".mkv", ".ts", ".avi", ".rmvb", ".rm", ".wmv", ".mov", ".flv",
            ".m2ts", ".mpg", ".mpeg", ".m4v", ".webm", ".iso", ".vob", ".3gp");

    /** 字幕扩展名（保留：属于正片配套）。 */
    private static final Set<String> SUBTITLE_EXT = Set.of(".srt", ".ass", ".ssa", ".sub", ".vtt", ".smi");

    /** 非视频的可疑扩展名（图片/文本/网页/快捷方式）。 */
    private static final Set<String> JUNK_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".txt", ".html", ".htm",
            ".url", ".lnk", ".doc", ".docx", ".mht");

    /** 小体积阈值：超过则不按"垃圾类型"删（避免误伤大文件）。 */
    private static final long SMALL = 50L * 1024 * 1024;

    public static boolean isAd(String name) {
        return isAd(name, 0L, false);
    }

    public static boolean isAd(String name, long size, boolean isDir) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);

        for (String k : KEYWORDS) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        if (isDir) {
            return false; // 文件夹只看关键词，靠递归再逐层过滤
        }

        String ext = extOf(lower);
        if (VIDEO_EXT.contains(ext) || SUBTITLE_EXT.contains(ext)) {
            return false; // 正片/字幕，保留
        }
        // 非视频的图片/文本/网页/快捷方式：小体积（或未知体积）一律视为广告说明
        if (JUNK_EXT.contains(ext) && (size <= 0 || size < SMALL)) {
            return true;
        }
        return false;
    }

    private static String extOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot >= 0 ? lowerName.substring(dot) : "";
    }
}
