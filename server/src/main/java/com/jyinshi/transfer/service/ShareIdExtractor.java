package com.jyinshi.transfer.service;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从分享链接提取去重用的 share_id（夸克/百度/迅雷都是 /s/{id} 结构）。
 * 提取失败时回退到规范化后的整条 url，保证仍能作为唯一键。
 */
final class ShareIdExtractor {

    /** 匹配 .../s/{id}，id 取到分隔符（/ ? # | 空白）之前。 */
    private static final Pattern S_SLUG = Pattern.compile("/s/([^/?#|\\s]+)");

    private ShareIdExtractor() {
    }

    static String extract(String shareUrl) {
        if (!StringUtils.hasText(shareUrl)) {
            return "";
        }
        String first = shareUrl.trim().split("\\s")[0];
        Matcher m = S_SLUG.matcher(first);
        if (m.find()) {
            return m.group(1);
        }
        // 回退：去掉 query/fragment 的整条链接（截断到 1024）
        String cut = first.split("[?#]")[0];
        return cut.length() <= 191 ? cut : cut.substring(0, 191);
    }
}
