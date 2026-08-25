package com.jyinshi.content.pool;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 粘贴解析：标题 + 网盘链接。两种录入共用。
 *
 * <p>规则：第一条有效文字当标题；标题后碎语忽略直到下一条 URL；连续多条 URL 共用当前标题。
 * 出现新的非 URL 文本（且当前标题已挂过 URL）则开启新标题。</p>
 */
public final class PoolPasteParser {

    private static final Pattern URL = Pattern.compile(
            "(https?://[^\\s<>\"'，。；、【】（）]+)"
                    + "|(magnet:\\?xt=urn:btih:[A-Za-z0-9][^\\s<>\"']*)"
                    + "|(ed2k://[^\\s<>\"']+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_PREFIX = Pattern.compile(
            "^(?:链接|下载地址|地址|分享|网盘)\\s*[:：]?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PWD_LINE = Pattern.compile(
            "^(?:提取码|密码|访问码)\\s*[:：]\\s*([A-Za-z0-9]+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRAP_QUOTES = Pattern.compile("^[「『\"“‘'](.+)[」』\"”’']$");

    private PoolPasteParser() {
    }

    public record Item(String title, String url, String password, String panType) {
    }

    public static List<Item> parse(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String[] lines = text.split("\\r?\\n");
        String title = null;
        boolean seenUrlForTitle = false;
        List<Item> items = new ArrayList<>();

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher pwd = PWD_LINE.matcher(line);
            if (pwd.matches()) {
                attachPassword(items, pwd.group(1));
                continue;
            }

            List<FoundUrl> urls = findUrls(line);
            if (!urls.isEmpty()) {
                String leftover = stripPrefixes(stripUrls(line, urls)).trim();
                leftover = unwrapTitle(leftover);
                if (StringUtils.hasText(leftover) && isTitleText(leftover)
                        && (title == null || seenUrlForTitle)) {
                    title = leftover;
                    seenUrlForTitle = false;
                }
                if (!StringUtils.hasText(title)) {
                    title = "未命名资源";
                }
                for (FoundUrl fu : urls) {
                    String pan = PanShareDetector.detect(fu.url);
                    if (pan == null) {
                        continue;
                    }
                    String password = StringUtils.hasText(fu.pwd) ? fu.pwd : PanShareDetector.extractPwd(fu.url);
                    items.add(new Item(truncate(title, 255), fu.url, password, pan));
                    if (items.size() >= PoolConstants.MAX_ITEMS) {
                        return items;
                    }
                }
                seenUrlForTitle = true;
                continue;
            }

            String candidate = unwrapTitle(stripPrefixes(line).trim());
            if (!isTitleText(candidate)) {
                continue;
            }
            if (title != null && !seenUrlForTitle) {
                // 标题后的碎语，忽略直到下一条 URL
                continue;
            }
            title = candidate;
            seenUrlForTitle = false;
        }
        return items;
    }

    private static void attachPassword(List<Item> items, String password) {
        if (items.isEmpty() || !StringUtils.hasText(password)) {
            return;
        }
        int i = items.size() - 1;
        Item last = items.get(i);
        if (StringUtils.hasText(last.password())) {
            return;
        }
        items.set(i, new Item(last.title(), last.url(), password, last.panType()));
    }

    private record FoundUrl(String url, String pwd) {
    }

    private static List<FoundUrl> findUrls(String line) {
        List<FoundUrl> out = new ArrayList<>();
        Matcher m = URL.matcher(line);
        while (m.find()) {
            String url = trimTrailingPunct(m.group());
            if (!StringUtils.hasText(url)) {
                continue;
            }
            out.add(new FoundUrl(url, PanShareDetector.extractPwd(url)));
        }
        return out;
    }

    private static String stripUrls(String line, List<FoundUrl> urls) {
        String s = line;
        for (FoundUrl fu : urls) {
            s = s.replace(fu.url, " ");
        }
        return s;
    }

    private static String stripPrefixes(String s) {
        return LINE_PREFIX.matcher(s).replaceFirst("");
    }

    private static String unwrapTitle(String s) {
        if (!StringUtils.hasText(s)) {
            return s;
        }
        Matcher m = WRAP_QUOTES.matcher(s.trim());
        return m.matches() ? m.group(1).trim() : s.trim();
    }

    private static boolean isTitleText(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        String t = s.trim();
        if (t.startsWith("http://") || t.startsWith("https://")
                || t.toLowerCase().startsWith("magnet:") || t.toLowerCase().startsWith("ed2k://")) {
            return false;
        }
        return t.codePoints().anyMatch(cp -> Character.isLetterOrDigit(cp)
                || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private static String trimTrailingPunct(String url) {
        int end = url.length();
        while (end > 0) {
            char c = url.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == '。' || c == '，'
                    || c == ')' || c == '）' || c == ']' || c == '」' || c == '》') {
                end--;
            } else {
                break;
            }
        }
        return url.substring(0, end);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
