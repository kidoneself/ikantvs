package com.jyinshi.content.service;

import com.jyinshi.content.entity.MediaLink;
import org.springframework.util.StringUtils;

/**
 * 详情页展示前过滤：note 含明显引流/广告词的不展示。
 * 无法识别盘内文件，仅依据备注标题。
 */
public final class MediaLinkAdFilter {

    private static final String[] AD_KEYWORDS = {
            "公众号", "加群", "资源群", "交流群", "qq群", "QQ群", "电报群", "TG群", "tg频道", "TG频道",
            "扫码", "防失联", "看简介", "免费领取", "免费获取", "关注获取", "关注公众号",
            "微信获取", "推广", "代理", "兼职", "赚佣金", "广告", "引流", "电报", "cgsousou",
    };

    /** note 里 @ 多为 TG/引流账号 */
    private static final java.util.regex.Pattern AT_HANDLE =
            java.util.regex.Pattern.compile("@[\\w\\u4e00-\\u9fa5]{3,}");

    private MediaLinkAdFilter() {
    }

    public static boolean isLikelyAd(MediaLink link) {
        if (link == null) {
            return false;
        }
        if ("manual".equalsIgnoreCase(link.getSource())
                || "self".equalsIgnoreCase(link.getSource())
                || "pool".equalsIgnoreCase(link.getSource())) {
            return false;
        }
        return matchesAdNote(link.getNote());
    }

    public static boolean matchesAdNote(String note) {
        if (!StringUtils.hasText(note)) {
            return false;
        }
        String n = note.toLowerCase();
        for (String kw : AD_KEYWORDS) {
            if (n.contains(kw.toLowerCase())) {
                return true;
            }
        }
        if (AT_HANDLE.matcher(note).find()) {
            return true;
        }
        // 超长 + 多 # 标签：PanSou 广告模板常见
        if (note.length() > 220 && countChar(note, '#') >= 3) {
            return true;
        }
        return false;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
