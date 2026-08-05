package com.jyinshi.transfer.service;

import com.jyinshi.content.service.EpisodeExtractor;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度 ↔ 迅雷跨盘 NAS 差集身份键。
 *
 * <p>同一集/同一期在不同源文件名常不一致（尾部 {@code -}、空格/点/横杠互换、
 * {@code 4K}、书名号等），exact 文件名比对会把已有当缺失。本类按内容类型抽稳定键：</p>
 * <ul>
 *   <li>剧集/动漫：集数 + 版本（国语/粤语等）；忽略画质后缀</li>
 *   <li>综艺：日期(YYYYMMDD) + 期题归一化（同一天多期/彩蛋不合并）</li>
 *   <li>抽不出时：整名归一化兜底</li>
 * </ul>
 *
 * <p>{@code relDir} 仍参与键（子目录结构不同时不误并）。</p>
 */
public final class NasFileMatcher {

    /** 片头日期：2026.05.20 / 2026-05-20 / 20260520 */
    private static final Pattern DATE_HEAD = Pattern.compile(
            "^(20\\d{2})[.\\-/]?(0[1-9]|1[0-2])[.\\-/]?(0[1-9]|[12]\\d|3[01])");
    /** 文中日期（半熟恋人.20260728.4K） */
    private static final Pattern DATE_ANY = Pattern.compile(
            "(20\\d{2})[.\\-/]?(0[1-9]|1[0-2])[.\\-/]?(0[1-9]|[12]\\d|3[01])");
    private static final Pattern EP_SXXEXX =
            Pattern.compile("S\\d+E(\\d+)", Pattern.CASE_INSENSITIVE);
    /** 开头集数：01- / 01. / 01国语；或整个文件名就是 02 */
    private static final Pattern EP_HEAD =
            Pattern.compile("^(\\d{1,3})(?:[-._\\s]|[\\u4e00-\\u9fff]|$)");
    private static final Pattern QUALITY = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])(?:4k|2160p|1080p|720p|480p|hdr10|hdr|web-?dl|bluray|remux|"
                    + "hevc|x265|x264|h265|h264|10bit|hq)(?:$|[^a-z0-9])");
    private static final Pattern NON_WORD = Pattern.compile("[^0-9a-zA-Z\\u4e00-\\u9fff]+");
    /** 剧集版本标记：同集国粤语必须区分，其它标题文案忽略。 */
    private static final Pattern VERSION_MARK = Pattern.compile(
            "国语|粤语|台配|国配|中字|英字|中英|简体|繁体|双语");

    private NasFileMatcher() {
    }

    /**
     * 相对路径键 → 身份键。输入形如 {@code name} 或 {@code sub/dir/name.mp4}。
     */
    public static String matchKey(String relPath) {
        if (!StringUtils.hasText(relPath)) {
            return "";
        }
        String normPath = relPath.replace('\\', '/').trim();
        int slash = normPath.lastIndexOf('/');
        String dir = slash >= 0 ? normPath.substring(0, slash) : "";
        String name = slash >= 0 ? normPath.substring(slash + 1) : normPath;
        String body = stripExt(name);
        String content = contentKey(body);
        return dir.isEmpty() ? content : dir + "|" + content;
    }

    /** 是否已「有」：exact 或身份键命中均可。 */
    public static boolean alreadyHave(String baiduRelKey, java.util.Set<String> xunleiRelKeys,
                                      java.util.Set<String> xunleiMatchKeys) {
        if (xunleiRelKeys.contains(baiduRelKey)) {
            return true;
        }
        String mk = matchKey(baiduRelKey);
        return StringUtils.hasText(mk) && xunleiMatchKeys.contains(mk);
    }

    static String contentKey(String body) {
        if (!StringUtils.hasText(body)) {
            return "F:";
        }
        // 1) 剧集优先：SxxExx / 01国语（避免把日期年里的数字当集）
        Integer ep = extractEpisodeNumber(body);
        if (ep != null) {
            String rest = stripEpisodePrefix(body, ep);
            return "E:" + ep + ":" + extractVersion(normalizeTitle(rest));
        }
        // 2) 综艺：日期 + 期题（同日多文件靠期题区分）
        DateHit date = extractDate(body);
        if (date != null) {
            String rest = body.substring(0, date.start) + body.substring(date.end);
            return "D:" + date.ymd + ":" + normalizeTitle(rest);
        }
        // 3) 兜底
        return "F:" + normalizeTitle(body);
    }

    private static Integer extractEpisodeNumber(String body) {
        // 开头像 YYYYMMDD → 不当集数（与 EpisodeExtractor 一致）
        if (DATE_HEAD.matcher(body).lookingAt()) {
            return null;
        }
        Matcher sxx = EP_SXXEXX.matcher(body);
        if (sxx.find()) {
            int ep = Integer.parseInt(sxx.group(1));
            if (ep > 0 && ep <= 500) {
                return ep;
            }
        }
        Matcher head = EP_HEAD.matcher(body);
        if (head.find()) {
            int ep = Integer.parseInt(head.group(1));
            if (ep > 0 && ep <= 500) {
                return ep;
            }
        }
        // 再问一次 EpisodeExtractor，保持与展示逻辑同源
        return EpisodeExtractor.extractEpisode(body);
    }

    private static String stripEpisodePrefix(String body, int ep) {
        Matcher sxx = EP_SXXEXX.matcher(body);
        if (sxx.find() && Integer.parseInt(sxx.group(1)) == ep) {
            return body.substring(0, sxx.start()) + body.substring(sxx.end());
        }
        Matcher head = EP_HEAD.matcher(body);
        if (head.find() && Integer.parseInt(head.group(1)) == ep) {
            if (head.end() >= body.length()) {
                return "";
            }
            // 保留「国语」等中文：匹配里中文是分隔符的一部分，需吐回去
            String g = head.group();
            char last = g.charAt(g.length() - 1);
            if (last >= 0x4e00 && last <= 0x9fff) {
                return last + body.substring(head.end());
            }
            return body.substring(head.end());
        }
        return body;
    }

    private static DateHit extractDate(String body) {
        Matcher head = DATE_HEAD.matcher(body);
        if (head.find()) {
            return new DateHit(head.start(), head.end(),
                    head.group(1) + head.group(2) + head.group(3));
        }
        Matcher any = DATE_ANY.matcher(body);
        if (any.find()) {
            return new DateHit(any.start(), any.end(),
                    any.group(1) + any.group(2) + any.group(3));
        }
        return null;
    }

    static String normalizeTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw;
        // 去掉画质/编码标签
        s = QUALITY.matcher(s).replaceAll(" ");
        // 书名号、括号等标点一律丢掉
        s = s.replace("《", "").replace("》", "")
                .replace("【", "").replace("】", "")
                .replace("（", "").replace("）", "")
                .replace("(", "").replace(")", "")
                .replace("[", "").replace("]", "")
                .replace("：", "").replace(":", "")
                .replace("\"", "").replace("“", "").replace("”", "");
        s = NON_WORD.matcher(s).replaceAll("");
        return s.toLowerCase(Locale.ROOT);
    }

    /** 从已归一化串里抽出版本标记；无则空（同集不同标题仍算同一集）。 */
    static String extractVersion(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        Matcher m = VERSION_MARK.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group());
        }
        return sb.toString();
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot >= name.length() - 5) {
            return name.substring(0, dot);
        }
        return name;
    }

    private record DateHit(int start, int end, String ymd) {
    }
}
