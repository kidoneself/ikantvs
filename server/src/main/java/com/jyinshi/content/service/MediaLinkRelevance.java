package com.jyinshi.content.service;

import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import org.springframework.util.StringUtils;

/**
 * 详情页链接排序：note 与片名越相关越靠前，不相关仍保留但沉底。
 */
public final class MediaLinkRelevance {

    private MediaLinkRelevance() {
    }

    public static int score(Media media, MediaLink link) {
        if (media == null || link == null) {
            return 0;
        }
        int score = scoreText(link.getNote(), media.getTitle());
        if (score < 80 && StringUtils.hasText(media.getOriginalTitle())) {
            score = Math.max(score, scoreText(link.getNote(), media.getOriginalTitle()));
        }
        if ("manual".equalsIgnoreCase(link.getSource())) {
            score += 30;
        }
        if (!StringUtils.hasText(link.getNote()) || link.getNote().trim().length() < 6) {
            score = Math.max(0, score - 20);
        }
        return score;
    }

    /** 两段标题的互相关度（供入库：详情页标题 ↔ 片库片名）。 */
    public static int scoreTitlePair(String a, String b) {
        return scoreText(a, b);
    }

    /** note 与参考标题的相关度 0～100 */
    static int scoreText(String note, String reference) {
        if (!StringUtils.hasText(note) || !StringUtils.hasText(reference)) {
            return 0;
        }
        String n = normalize(note);
        String ref = normalize(reference);
        if (n.isEmpty() || ref.isEmpty()) {
            return 0;
        }
        if (n.contains(ref) || ref.contains(n)) {
            return 100;
        }
        // 最长公共连续子串（≥2 字）
        int best = 0;
        int maxLen = Math.min(ref.length(), 12);
        for (int len = maxLen; len >= 2; len--) {
            for (int i = 0; i <= ref.length() - len; i++) {
                String sub = ref.substring(i, i + len);
                if (n.contains(sub)) {
                    best = Math.max(best, 40 + len * 4);
                }
            }
        }
        return Math.min(best, 95);
    }

    private static String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[\\s\\p{Punct}·•【】\\[\\]()（）「」『』《》<>]", "")
                .trim();
    }
}
