package com.jyinshi.search.docmonitor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可配置剧目聚合：按 {@link ParseRules} 把行列表聚成 {@link DramaEntry}。
 * 规则存在库里，运营改前缀/噪声无需发版。
 */
@Component
public class DramaAggregator {

    public List<DramaEntry> aggregate(List<ContentLine> lines, ParseRules rules, String source) {
        ParseRules r = rules != null ? rules : ParseRuleTemplates.forSource(source);
        Pattern namePat = compileSafe(r.getNameExtractRegex(), Pattern.compile("[「《]([^」》]+)[」》]"));
        Pattern pwdPat = compileSafe(r.getPwdRegex(), Pattern.compile("提取码\\s*[:：]\\s*([a-zA-Z0-9]+)"));
        boolean labeled = "labeled".equalsIgnoreCase(r.getMatchMode());

        List<DramaEntry> out = new ArrayList<>();
        DramaEntry current = null;

        for (ContentLine line : lines) {
            if (line == null) {
                continue;
            }
            String text = line.getText() == null ? "" : line.getText().trim();
            if (text.isEmpty() && line.getUrls().isEmpty()) {
                continue;
            }
            if (isNoise(text, r)) {
                continue;
            }

            boolean hasBaidu = line.getUrls().stream().anyMatch(PanLinkExtractor::isBaidu);
            boolean hasQuark = line.getUrls().stream().anyMatch(PanLinkExtractor::isQuark);
            boolean hasXunlei = line.getUrls().stream().anyMatch(PanLinkExtractor::isXunlei);
            boolean isBd = matchesLinkLine(text, r.getBaiduPrefixes(), labeled) && (!labeled || hasBaidu);
            boolean isKk = matchesLinkLine(text, r.getQuarkPrefixes(), labeled) && (!labeled || hasQuark);
            boolean isXl = matchesLinkLine(text, r.getXunleiPrefixes(), labeled) && (!labeled || hasXunlei);

            // startsWith 模式：有前缀即可挂链，不强求行内已有 URL（URL 可能在 title 里）
            if (!labeled) {
                if (matchesLinkLine(text, r.getBaiduPrefixes(), false)) {
                    isBd = true;
                }
                if (matchesLinkLine(text, r.getQuarkPrefixes(), false)) {
                    isKk = true;
                }
                if (matchesLinkLine(text, r.getXunleiPrefixes(), false)) {
                    isXl = true;
                }
            }

            if (isBd && current != null && current.getBaiduUrl() == null) {
                String bd = firstUrl(line, PanLinkExtractor::isBaidu);
                if (bd == null) {
                    bd = firstPanInText(text, PanLinkExtractor::isBaidu);
                }
                if (bd != null) {
                    current.setBaiduUrl(appendPwdIfMissing(bd, text, pwdPat));
                }
            } else if (isKk && current != null && current.getQuarkUrl() == null) {
                String kk = firstUrl(line, PanLinkExtractor::isQuark);
                if (kk == null) {
                    kk = firstPanInText(text, PanLinkExtractor::isQuark);
                }
                if (kk != null) {
                    current.setQuarkUrl(kk);
                }
            } else if (isXl && current != null && current.getXunleiUrl() == null) {
                String xl = firstUrl(line, PanLinkExtractor::isXunlei);
                if (xl == null) {
                    xl = firstPanInText(text, PanLinkExtractor::isXunlei);
                }
                if (xl != null) {
                    current.setXunleiUrl(appendPwdIfMissing(xl, text, pwdPat));
                }
            } else if (!isBd && !isKk && !isXl) {
                flush(current, out);
                current = new DramaEntry();
                current.setFullTitle(text);
                current.setName(extractName(text, namePat));
                current.setSource(source);
            }
        }
        flush(current, out);
        return out;
    }

    private static void flush(DramaEntry cur, List<DramaEntry> out) {
        if (cur != null && StringUtils.hasText(cur.getFullTitle())
                && (StringUtils.hasText(cur.getBaiduUrl())
                || StringUtils.hasText(cur.getQuarkUrl())
                || StringUtils.hasText(cur.getXunleiUrl()))) {
            out.add(cur);
        }
    }

    private static String appendPwdIfMissing(String url, String text, Pattern pwdPat) {
        if (url == null || url.contains("?pwd=") || url.contains("&pwd=")) {
            return url;
        }
        Matcher m = pwdPat.matcher(text);
        if (m.find()) {
            return url + "?pwd=" + m.group(1);
        }
        return url;
    }

    private static boolean isNoise(String text, ParseRules r) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        String t = text.trim();
        if (r.getNoisePrefixes() != null) {
            for (String p : r.getNoisePrefixes()) {
                if (StringUtils.hasText(p) && startsWithIgnoreCase(t, p.trim())) {
                    return true;
                }
            }
        }
        if (r.getNoiseContains() != null) {
            for (String c : r.getNoiseContains()) {
                if (StringUtils.hasText(c) && t.contains(c)) {
                    return true;
                }
            }
        }
        // 极短无意义行
        return t.length() <= 2 && t.matches("[a-zA-Z0-9⇨]+");
    }

    private static boolean matchesLinkLine(String text, List<String> prefixes, boolean labeled) {
        if (!StringUtils.hasText(text) || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        String t = text.trim();
        for (String raw : prefixes) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String p = raw.trim();
            if (labeled) {
                // 可选「第x季/部」「缺集备用」后再跟前缀与冒号
                Pattern pat = Pattern.compile(
                        "^(?:第[一二三四五六七八九十\\d]+[季部])?(?:缺集备用)?"
                                + Pattern.quote(p) + "\\s*[:：]",
                        Pattern.CASE_INSENSITIVE);
                if (pat.matcher(t).find()) {
                    return true;
                }
            } else if (startsWithIgnoreCase(t, p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String firstUrl(ContentLine line, java.util.function.Predicate<String> pred) {
        return line.getUrls().stream().filter(pred).findFirst().orElse(null);
    }

    private static String firstPanInText(String text, java.util.function.Predicate<String> pred) {
        for (String u : PanLinkExtractor.extractUrls(text)) {
            if (pred.test(u)) {
                return u;
            }
        }
        return null;
    }

    private static String extractName(String fullTitle, Pattern namePat) {
        Matcher m = namePat.matcher(fullTitle);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (StringUtils.hasText(m.group(i))) {
                    return m.group(i);
                }
            }
        }
        String name = fullTitle.replaceAll("【[^】]*】", "").trim();
        int dot = name.indexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot).trim();
        }
        name = name.replaceAll("(?i)(更\\s*\\d+集?|1080P|4K|HD\\d?K?|超前完结|完结|第[一二三四五六七八九十\\d]+季).*$", "").trim();
        return name.isEmpty() ? fullTitle : name;
    }

    private static Pattern compileSafe(String regex, Pattern fallback) {
        if (!StringUtils.hasText(regex)) {
            return fallback;
        }
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return fallback;
        }
    }
}
