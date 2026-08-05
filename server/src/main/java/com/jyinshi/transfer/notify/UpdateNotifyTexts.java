package com.jyinshi.transfer.notify;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 追更更新播报文案（对齐老站 MonitorScheduleTask / 「今日」口令回复）。
 */
public final class UpdateNotifyTexts {

    public static final String SITE_URL = "https://naspt.vip/";

    private UpdateNotifyTexts() {
    }

    /**
     * @param items 已按「最新在上」排好
     */
    public static String broadcast(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item p : items) {
            sb.append("✔ ").append(p.title());
            if (StringUtils.hasText(p.episode())) {
                sb.append(" → ").append(p.episode().trim());
            }
            sb.append("\n");
        }
        sb.append("\n🎬点击链接获取\n")
                .append("👇👇\n")
                .append(SITE_URL).append("\n")
                .append("（打不开复制到浏览器）\n")
                .append("可以邀请朋友进群噢！");
        return sb.toString();
    }

    public record Item(String title, String episode) {
    }
}
