package com.jyinshi.transfer.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 从分享 URL 的域名判定网盘类型（与迁移脚本 HOST_PAN_MAP 口径一致）。
 *
 * <p>用于转存前校验「链接归属」与「所选网盘」是否一致，避免把 123/UC/阿里 等链接
 * 当成夸克/百度去转，产生必然失败的任务。</p>
 */
final class PanUrlDetector {

    /** host 片段 → 规范类型。有序：先匹配更具体的。 */
    private static final Map<String, String> HOST_PAN = new LinkedHashMap<>();
    /** 规范类型 → 中文展示名。 */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

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
    }

    private PanUrlDetector() {
    }

    /** 从 URL 判定网盘类型；判不出返回 null（不阻断，交由 panType 判断）。 */
    static String detect(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("magnet:")) {
            return "magnet";
        }
        for (Map.Entry<String, String> e : HOST_PAN.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 类型的中文展示名；未知回退原值。 */
    static String label(String panType) {
        if (!StringUtils.hasText(panType)) {
            return "未知";
        }
        return LABELS.getOrDefault(panType.toLowerCase(Locale.ROOT), panType);
    }
}
