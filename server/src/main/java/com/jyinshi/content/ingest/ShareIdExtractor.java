package com.jyinshi.content.ingest;

import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从分享链接提取去重用的 share_id（去重键 media_id + pan_type + share_id 的一部分）。
 *
 * <p><b>必须与迁移 V023 的 SQL 回填规则保持一致</b>，否则新入库的 share_id 会和存量对不上、
 * 导致同一分享被判成两条。规则：
 * <ul>
 *   <li>magnet：取 btih 后的哈希，转小写</li>
 *   <li>baidu 且含 {@code surl=}：取 surl 参数</li>
 *   <li>其它：取路径最后一段（去掉 query/fragment/换行、去尾部 /）</li>
 *   <li>baidu 的 {@code /s/1xxxx}：去掉前导 1</li>
 *   <li>提取值须通过 ascii 合法校验 {@code ^[A-Za-z0-9._~-]{1,64}$}，否则退回 md5(url)</li>
 * </ul>
 * share_id 列是 {@code VARCHAR(64) CHARACTER SET ascii NOT NULL}，故必须保证非空且为 ascii。
 */
public final class ShareIdExtractor {

    private static final Pattern BTIH = Pattern.compile("btih:([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SURL = Pattern.compile("surl=([^&#\\r\\n]+)");
    private static final Pattern ASCII_OK = Pattern.compile("^[A-Za-z0-9._~-]{1,64}$");

    private ShareIdExtractor() {
    }

    /**
     * @param url     分享链接（不含提取码更佳；提取码在 query 里也会被剥掉）
     * @param panType 网盘类型（magnet/baidu/... 用于分支）
     * @return 非空 ascii 的 share_id
     */
    public static String extract(String url, String panType) {
        if (!StringUtils.hasText(url)) {
            return md5(url);
        }
        String pan = panType == null ? "" : panType.trim().toLowerCase();
        String first = url.trim().split("[\\r\\n]", 2)[0].trim();

        String candidate = rawCandidate(first, pan);
        if (candidate != null && "baidu".equals(pan) && !first.contains("surl=")
                && candidate.length() > 1 && candidate.startsWith("1")
                && !candidate.matches("^[a-f0-9]{32}$")) {
            // 百度 /s/1xxxx 去前导 1（与 V023 对齐）
            candidate = candidate.substring(1);
        }
        if (candidate != null && ASCII_OK.matcher(candidate).matches()) {
            return candidate;
        }
        return md5(first);
    }

    private static String rawCandidate(String url, String pan) {
        if ("magnet".equals(pan)) {
            Matcher m = BTIH.matcher(url);
            return m.find() ? m.group(1).toLowerCase() : null;
        }
        if ("baidu".equals(pan)) {
            Matcher m = SURL.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        }
        // 其它：去 query/fragment → 去尾部 / → 取最后一段
        String cut = url.split("[?#]", 2)[0];
        while (cut.endsWith("/")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        int slash = cut.lastIndexOf('/');
        String seg = slash >= 0 ? cut.substring(slash + 1) : cut;
        return seg.isEmpty() ? null : seg;
    }

    private static String md5(String s) {
        String base = s == null ? "" : s;
        return DigestUtils.md5DigestAsHex(base.getBytes(StandardCharsets.UTF_8));
    }
}
