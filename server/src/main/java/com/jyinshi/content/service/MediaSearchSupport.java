package com.jyinshi.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.content.entity.Media;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内搜索：拆字 + 多字段召回 + 相关度排序（对齐观影 mode=1 思路）。
 * 不搜 overview，避免大字段拖慢查询。
 */
final class MediaSearchSupport {

    private static final int MAX_CHARS = 8;
    private static final String SEARCHABLE =
            "CONCAT(IFNULL(title,''), IFNULL(original_title,''), IFNULL(actors,''), IFNULL(directors,''))";

    private MediaSearchSupport() {
    }

    static List<String> splitTokens(String keyword) {
        String k = keyword.trim();
        if (k.isEmpty()) {
            return List.of();
        }
        boolean hasHan = k.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        if (!hasHan) {
            return List.of(k);
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < k.length() && out.size() < MAX_CHARS; ) {
            int cp = k.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                String ch = new String(Character.toChars(cp));
                if (Character.isLetterOrDigit(cp)
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                    out.add(ch);
                }
            }
            i += Character.charCount(cp);
        }
        return new ArrayList<>(out);
    }

    static void applyKeyword(LambdaQueryWrapper<Media> w, String keyword) {
        List<String> tokens = splitTokens(keyword);
        if (tokens.isEmpty()) {
            return;
        }
        if (tokens.size() == 1 && tokens.get(0).equals(keyword.trim())) {
            String t = tokens.get(0);
            w.and(q -> q.like(Media::getTitle, t)
                    .or().like(Media::getOriginalTitle, t)
                    .or().like(Media::getActors, t)
                    .or().like(Media::getDirectors, t));
            return;
        }
        for (String ch : tokens) {
            w.and(q -> q.apply(SEARCHABLE + " LIKE {0}", "%" + ch + "%"));
        }
    }

    /** 有关键词时：相关度优先，再叠用户选的 sort。 */
    static void applySearchOrder(LambdaQueryWrapper<Media> w, String keyword, String sort) {
        String kw = keyword.trim();
        w.last("ORDER BY (" + relevanceExpr(kw) + ") DESC, " + secondaryOrder(sort));
    }

    private static String relevanceExpr(String keyword) {
        String esc = escapeSql(keyword);
        StringBuilder sb = new StringBuilder();
        sb.append("CASE ");
        sb.append("WHEN title = '").append(esc).append("' THEN 1000 ");
        sb.append("WHEN title LIKE '").append(esc).append("%' THEN 800 ");
        sb.append("WHEN title LIKE '%").append(esc).append("%' THEN 600 ");
        sb.append("WHEN original_title LIKE '%").append(esc).append("%' THEN 400 ");
        sb.append("WHEN actors LIKE '%").append(esc).append("%' THEN 200 ");
        sb.append("WHEN directors LIKE '%").append(esc).append("%' THEN 150 ");
        sb.append("ELSE 0 END");
        for (String ch : splitTokens(keyword)) {
            if (ch.equals(keyword)) {
                continue;
            }
            String c = escapeSql(ch);
            sb.append(" + CASE WHEN title LIKE '%").append(c).append("%' THEN 80 ELSE 0 END");
            sb.append(" + CASE WHEN actors LIKE '%").append(c).append("%' THEN 20 ELSE 0 END");
        }
        return sb.toString();
    }

    private static String secondaryOrder(String sort) {
        return switch (sort == null ? "" : sort) {
            case "new" -> "release_date DESC, id DESC";
            case "release_asc" -> "release_date ASC, id ASC";
            case "rating" -> "rating DESC, id DESC";
            case "rating_asc" -> "rating ASC, id ASC";
            case "hot_asc" -> "hot ASC, id ASC";
            default -> "hot DESC, id DESC";
        };
    }

    private static String escapeSql(String s) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "''");
    }
}
