package com.jyinshi.content.pool;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从分享 URL 判定网盘类型。认不出返回 null（调用方跳过该 URL）。
 */
public final class PanShareDetector {

    private static final Map<String, String> HOST_PAN = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Pattern PWD_QUERY =
            Pattern.compile("[?&]pwd=([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    static {
        HOST_PAN.put("pan.quark.cn", "quark");
        HOST_PAN.put("pan.baidu.com", "baidu");
        HOST_PAN.put("pan.xunlei.com", "xunlei");
        HOST_PAN.put("drive.uc.cn", "uc");
        HOST_PAN.put("alipan.com", "aliyun");
        HOST_PAN.put("aliyundrive.com", "aliyun");
        HOST_PAN.put("115.com", "115");
        HOST_PAN.put("115cdn.com", "115");
        HOST_PAN.put("anxia.com", "115");
        HOST_PAN.put("123pan.com", "123");
        HOST_PAN.put("123pan.cn", "123");
        HOST_PAN.put("123684.com", "123");
        HOST_PAN.put("123685.com", "123");
        HOST_PAN.put("123912.com", "123");
        HOST_PAN.put("123592.com", "123");
        HOST_PAN.put("cloud.189.cn", "tianyi");
        HOST_PAN.put("caiyun.139.com", "mobile");

        LABELS.put("quark", "夸克");
        LABELS.put("baidu", "百度");
        LABELS.put("xunlei", "迅雷");
        LABELS.put("uc", "UC");
        LABELS.put("aliyun", "阿里");
        LABELS.put("115", "115");
        LABELS.put("123", "123");
        LABELS.put("tianyi", "天翼");
        LABELS.put("mobile", "移动");
        LABELS.put("magnet", "磁力");
        LABELS.put("ed2k", "电驴");
        LABELS.put("pikpak", "PikPak");
    }

    private PanShareDetector() {
    }

    public static String detect(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("magnet:")) {
            return "magnet";
        }
        if (lower.startsWith("ed2k://")) {
            return "ed2k";
        }
        for (Map.Entry<String, String> e : HOST_PAN.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String label(String panType) {
        if (!StringUtils.hasText(panType)) {
            return "未知";
        }
        return LABELS.getOrDefault(panType.toLowerCase(Locale.ROOT), panType);
    }

    public static String extractPwd(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher m = PWD_QUERY.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
