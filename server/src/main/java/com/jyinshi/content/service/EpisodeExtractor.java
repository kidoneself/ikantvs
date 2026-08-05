package com.jyinshi.content.service;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从「最新文件名」智能提取展示值（集数 &gt; 日期 &gt; 文件名截部分）。
 *
 * <p>对齐老站 {@code MonitorService}：只认文件名。
 * 集数：开头数字 + 分隔（{@code 01-} / {@code 01.} / {@code 01_}）或中文后缀（{@code 01国语} / {@code 06粤语}），
 * 或 {@code S01E12}；再日期；再文件名截断。</p>
 */
public final class EpisodeExtractor {

    /**
     * 开头 1~3 位集数，后接：- . _ 空白、或中文（国语/粤语/集…）。
     * 不用裸 {@code ^\d{1,3}}，避免把 {@code 20260117} 误当成集数 202。
     */
    private static final Pattern EP_HEAD =
            Pattern.compile("^(\\d{1,3})(?:[-._\\s]|[\\u4e00-\\u9fff])");
    private static final Pattern EP_SXXEXX = Pattern.compile("S\\d+E(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_YMD = Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])");
    private static final Pattern DATE_SEP =
            Pattern.compile("(20\\d{2})[-./](0[1-9]|1[0-2])[-./](0[1-9]|[12]\\d|3[01])");

    private EpisodeExtractor() {
    }

    /** 智能提取显示值：集数 &gt; 日期 &gt; 文件名（去扩展名，最长 20 字）。提取不出返回 null。 */
    public static String extractDisplay(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        Integer ep = extractEpisode(fileName);
        if (ep != null) {
            return String.valueOf(ep);
        }
        String date = extractDate(fileName);
        if (date != null) {
            return date;
        }
        String name = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            name = fileName.substring(0, dot);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > 20 ? name.substring(0, 20) + "..." : name;
    }

    /**
     * 从文件名提取集数。
     * 1. 开头数字 + 分隔/中文：{@code 01-}、{@code 01.}、{@code 01国语}、{@code 06粤语}
     * 2. {@code S01E12} 取 E 后集数（不能取季数）
     */
    public static Integer extractEpisode(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        Matcher m1 = EP_HEAD.matcher(fileName);
        if (m1.find()) {
            int ep = Integer.parseInt(m1.group(1));
            if (ep > 0 && ep <= 500) {
                return ep;
            }
        }
        Matcher m2 = EP_SXXEXX.matcher(fileName);
        if (m2.find()) {
            int ep = Integer.parseInt(m2.group(1));
            if (ep > 0 && ep <= 500) {
                return ep;
            }
        }
        return null;
    }

    /** YYYYMMDD 或 YYYY-MM-DD/YYYY.MM.DD → "M.D"（综艺按日期更新用）。 */
    private static String extractDate(String fileName) {
        Matcher m1 = DATE_YMD.matcher(fileName);
        if (m1.find()) {
            return Integer.parseInt(m1.group(2)) + "." + Integer.parseInt(m1.group(3));
        }
        Matcher m2 = DATE_SEP.matcher(fileName);
        if (m2.find()) {
            return Integer.parseInt(m2.group(2)) + "." + Integer.parseInt(m2.group(3));
        }
        return null;
    }

    /**
     * 多文件里挑「最新」文件名（监控转存回填用）。
     * 按老站规则抽集数/日期比大小，禁止用名字里「第一段数字」（会把 S01E02 的季数 01 当集数）。
     */
    public static String pickLatestFileName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        String best = null;
        String bestDisplay = null;
        for (String n : names) {
            if (!StringUtils.hasText(n)) {
                continue;
            }
            String d = extractDisplay(n);
            if (!StringUtils.hasText(d)) {
                if (best == null) {
                    best = n;
                }
                continue;
            }
            if (!StringUtils.hasText(bestDisplay)) {
                best = n;
                bestDisplay = d;
                continue;
            }
            String picked = pickLatest(bestDisplay, d);
            if (d.equals(picked) && !d.equals(bestDisplay)) {
                best = n;
                bestDisplay = d;
            }
        }
        return best != null ? best : names.get(names.size() - 1);
    }

    /**
     * 取「更靠后」的显示值：纯数字比大小；日期(M.D)比月/日；否则保留 current。
     * 对齐老站 {@code shouldUpdateEpisode}。
     */
    public static String pickLatest(String current, String candidate) {
        if (!StringUtils.hasText(current)) {
            return candidate;
        }
        if (!StringUtils.hasText(candidate)) {
            return current;
        }
        try {
            return Integer.parseInt(candidate) > Integer.parseInt(current) ? candidate : current;
        } catch (NumberFormatException ignore) {
            // 非纯数字，尝试日期
        }
        if (current.contains(".") && candidate.contains(".")) {
            try {
                String[] c = current.split("\\.");
                String[] n = candidate.split("\\.");
                if (c.length == 2 && n.length == 2) {
                    int cm = Integer.parseInt(c[0]);
                    int cd = Integer.parseInt(c[1]);
                    int nm = Integer.parseInt(n[0]);
                    int nd = Integer.parseInt(n[1]);
                    if (nm > cm || (nm == cm && nd > cd)) {
                        return candidate;
                    }
                }
            } catch (NumberFormatException ignore) {
                // 无法比较，保留原值
            }
        }
        return current;
    }

    /** 新文件名是否应覆盖当前（只增不减，对齐老站）。 */
    public static boolean shouldAdvanceFile(String currentFileName, String newFileName) {
        if (!StringUtils.hasText(newFileName)) {
            return false;
        }
        if (!StringUtils.hasText(currentFileName)) {
            return true;
        }
        if (currentFileName.equals(newFileName)) {
            return false;
        }
        String cur = extractDisplay(currentFileName);
        String neu = extractDisplay(newFileName);
        if (!StringUtils.hasText(neu)) {
            return false;
        }
        if (!StringUtils.hasText(cur)) {
            return true;
        }
        return neu.equals(pickLatest(cur, neu)) && !neu.equals(cur);
    }
}
