package com.jyinshi.search.docmonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 公共网盘 URL 扫描，各 Fetcher / Aggregator 共用。 */
public final class PanLinkExtractor {

    private PanLinkExtractor() {}

    private static final Pattern[] PATTERNS = new Pattern[]{
            Pattern.compile("https?://pan\\.quark\\.cn/s/[a-zA-Z0-9]+"),
            Pattern.compile("https?://pan\\.baidu\\.com/s/[a-zA-Z0-9_-]+(?:\\?pwd=[a-zA-Z0-9]+)?"),
            Pattern.compile("https?://(?:www\\.)?aliyundrive\\.com/s/[a-zA-Z0-9]+"),
            Pattern.compile("https?://(?:www\\.)?alipan\\.com/s/[a-zA-Z0-9]+"),
            Pattern.compile("https?://115\\.com/s/[a-zA-Z0-9]+"),
            Pattern.compile("https?://cloud\\.189\\.cn/[a-zA-Z0-9/]+"),
            Pattern.compile("https?://drive\\.uc\\.cn/s/[a-zA-Z0-9?=&]+"),
            Pattern.compile("https?://pan\\.xunlei\\.com/s/[a-zA-Z0-9_-]+(?:\\?pwd=[a-zA-Z0-9]+)?"),
    };

    public static List<String> extractUrls(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                out.add(m.group());
            }
        }
        return out;
    }

    public static boolean isPanLink(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("quark.cn")
                || u.contains("baidu.com/s")
                || u.contains("aliyundrive")
                || u.contains("alipan.com")
                || u.contains("115.com")
                || u.contains("189.cn")
                || u.contains("uc.cn")
                || u.contains("xunlei.com");
    }

    public static String getPanType(String url) {
        if (url == null) {
            return "unknown";
        }
        String u = url.toLowerCase();
        if (u.contains("quark.cn")) {
            return "quark";
        }
        if (u.contains("baidu.com")) {
            return "baidu";
        }
        if (u.contains("aliyundrive") || u.contains("alipan.com")) {
            return "aliyun";
        }
        if (u.contains("115.com")) {
            return "115";
        }
        if (u.contains("189.cn")) {
            return "tianyi";
        }
        if (u.contains("uc.cn")) {
            return "uc";
        }
        if (u.contains("xunlei.com")) {
            return "xunlei";
        }
        return "other";
    }

    public static boolean isQuark(String url) {
        return url != null && url.toLowerCase().contains("quark.cn");
    }

    public static boolean isBaidu(String url) {
        return url != null && url.toLowerCase().contains("pan.baidu.com");
    }

    public static boolean isXunlei(String url) {
        return url != null && url.toLowerCase().contains("pan.xunlei.com");
    }
}
