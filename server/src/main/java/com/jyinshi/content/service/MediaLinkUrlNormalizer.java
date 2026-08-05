package com.jyinshi.content.service;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网盘分享 URL 规范化。百度 {@code url|提取码} 需转为 {@code ?pwd=} 才能自动填充。
 */
public final class MediaLinkUrlNormalizer {

    /** https://pan.baidu.com/s/xxx|efc4 */
    private static final Pattern BAIDU_PIPE =
            Pattern.compile("^(https?://pan\\.baidu\\.com/s/[^\\s|?#]+)\\|([^\\s|?#]+)$", Pattern.CASE_INSENSITIVE);

    /** 第二行：提取码：efc4 */
    private static final Pattern EXTRACT_CODE_LINE =
            Pattern.compile("^[\\s]*提取码[:：]\\s*([^\\s\\r\\n]+)", Pattern.CASE_INSENSITIVE);

    private MediaLinkUrlNormalizer() {
    }

    public static String normalize(String raw, String panType) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String[] lines = raw.split("\\r?\\n");
        String shareUrl = lines[0].trim();
        String extraPwd = extractCodeFromLines(lines);

        Parsed parsed = parsePipeSuffix(shareUrl, panType);
        shareUrl = parsed.url();
        String pwd = StringUtils.hasText(parsed.password()) ? parsed.password() : extraPwd;

        if (!StringUtils.hasText(pwd) || hasPwdInUrl(shareUrl)) {
            return truncate(shareUrl);
        }
        if (supportsPwdQuery(panType)) {
            return truncate(appendPwdQuery(shareUrl, pwd.trim()));
        }
        return truncate(shareUrl + "\n提取码：" + pwd.trim());
    }

    private static Parsed parsePipeSuffix(String url, String panType) {
        if (!"baidu".equalsIgnoreCase(panType)) {
            return new Parsed(url, null);
        }
        Matcher m = BAIDU_PIPE.matcher(url.trim());
        if (!m.matches()) {
            return new Parsed(url, null);
        }
        return new Parsed(m.group(1).trim(), m.group(2).trim());
    }

    private static String extractCodeFromLines(String[] lines) {
        if (lines.length < 2) {
            return null;
        }
        for (int i = 1; i < lines.length; i++) {
            Matcher m = EXTRACT_CODE_LINE.matcher(lines[i].trim());
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private static boolean supportsPwdQuery(String panType) {
        if (!StringUtils.hasText(panType)) {
            return false;
        }
        return switch (panType.toLowerCase(Locale.ROOT)) {
            case "baidu", "xunlei", "quark", "123" -> true;
            default -> false;
        };
    }

    private static boolean hasPwdInUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("pwd=") || lower.contains("password=");
    }

    private static String appendPwdQuery(String url, String pwd) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            String param = "pwd=" + pwd;
            String newQuery = query == null || query.isBlank() ? param : query + "&" + param;
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment())
                    .toString();
        } catch (Exception ignored) {
            return url.contains("?") ? url + "&pwd=" + pwd : url + "?pwd=" + pwd;
        }
    }

    private static String truncate(String url) {
        return url.length() <= 1024 ? url : url.substring(0, 1024);
    }

    private record Parsed(String url, String password) {
    }
}
