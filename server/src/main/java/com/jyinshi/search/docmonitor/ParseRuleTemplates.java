package com.jyinshi.search.docmonitor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置解析模板；新建任务可一键套用，仍可按任务再改。 */
public final class ParseRuleTemplates {

    public static final String FLOWUS_DEFAULT = "flowus-default";
    public static final String KDOCS_DEFAULT = "kdocs-default";

    private ParseRuleTemplates() {}

    public static Map<String, ParseRules> all() {
        Map<String, ParseRules> m = new LinkedHashMap<>();
        m.put(FLOWUS_DEFAULT, flowusDefault());
        m.put(KDOCS_DEFAULT, kdocsDefault());
        return m;
    }

    public static ParseRules forSource(String source) {
        if (source != null && source.toLowerCase().contains("kdocs")) {
            return kdocsDefault();
        }
        return flowusDefault();
    }

    public static ParseRules flowusDefault() {
        ParseRules r = new ParseRules();
        r.setTemplate(FLOWUS_DEFAULT);
        r.setMatchMode("startsWith");
        r.setQuarkPrefixes(List.of("夸盘", "KK", "夸克", "Kk", "kk"));
        r.setBaiduPrefixes(List.of("度盘", "BD", "百度", "度"));
        r.setXunleiPrefixes(List.of("迅雷", "雷盘", "XL", "Xl", "xl"));
        r.setNoisePrefixes(List.of("UC", "链接", "注意"));
        r.setNoiseContains(List.of("持续更新中"));
        return r;
    }

    public static ParseRules kdocsDefault() {
        ParseRules r = new ParseRules();
        r.setTemplate(KDOCS_DEFAULT);
        r.setMatchMode("labeled");
        r.setQuarkPrefixes(List.of("KK", "KD", "夸克", "夸盘", "Kk", "kK", "kk", "K"));
        r.setBaiduPrefixes(List.of("BD", "百度", "度盘", "链接", "D"));
        r.setXunleiPrefixes(List.of("XL", "迅雷", "雷盘", "Xl", "xL", "xl", "X"));
        r.setNoisePrefixes(List.of("搜索方法", "注意", "【夸克", "【使用方法", "【双击", "复制网盘链接", "看剧复制", "不用打开", "复制这段内容", "【超级会员"));
        r.setNoiseContains(List.of());
        return r;
    }
}
