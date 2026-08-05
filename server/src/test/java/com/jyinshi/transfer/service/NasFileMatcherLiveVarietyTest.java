package com.jyinshi.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用实盘导出的 JSON（百度 walk + 迅雷 list）跑 {@link NasFileMatcher}，
 * 证明差集是 Java 代码算的，不是人工估。
 *
 * <p>数据文件：{@code /tmp/variety_lists.json}（千云脚本导出）。</p>
 */
class NasFileMatcherLiveVarietyTest {

    @Test
    void javaMatcherAgainstLiveVarietyFolders() throws Exception {
        Path p = Path.of("/tmp/variety_lists.json");
        assertTrue(Files.exists(p), "缺少 /tmp/variety_lists.json，先导出实盘列表");
        JsonNode root = new ObjectMapper().readTree(Files.readString(p));

        int totalExact = 0, totalMatch = 0, totalRescue = 0;
        System.out.println("==== Java NasFileMatcher 实盘验证 ====");
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String id = e.getKey();
            JsonNode n = e.getValue();
            String title = n.get("title").asText();
            List<String> baidu = toList(n.get("baidu"));
            List<String> xl = toList(n.get("xunlei"));

            Set<String> xlSet = new LinkedHashSet<>(xl);
            Set<String> xlMatch = new LinkedHashSet<>();
            for (String x : xl) {
                xlMatch.add(NasFileMatcher.matchKey(x));
            }

            List<String> exactMiss = new ArrayList<>();
            for (String b : baidu) {
                if (!xlSet.contains(b)) {
                    exactMiss.add(b);
                }
            }
            List<String> matchMiss = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            List<String> rescued = new ArrayList<>();
            for (String b : baidu) {
                if (xlSet.contains(b)) {
                    continue;
                }
                String mk = NasFileMatcher.matchKey(b);
                if (xlMatch.contains(mk)) {
                    rescued.add(b);
                    continue;
                }
                if (seen.add(mk)) {
                    matchMiss.add(b);
                }
            }

            totalExact += exactMiss.size();
            totalMatch += matchMiss.size();
            totalRescue += rescued.size();

            System.out.printf("%n## %s %s%n", id, title);
            System.out.printf("baidu=%d xl=%d exact_miss=%d match_miss=%d rescued=%d%n",
                    baidu.size(), xl.size(), exactMiss.size(), matchMiss.size(), rescued.size());
            if (!matchMiss.isEmpty()) {
                System.out.println("-- 真缺(Java) --");
                for (String m : matchMiss) {
                    System.out.println("MISS " + m);
                }
            }
            if (!rescued.isEmpty()) {
                System.out.println("-- 救回样例(Java) --");
                int i = 0;
                for (String b : rescued) {
                    if (i++ >= 5) {
                        System.out.println("... +" + (rescued.size() - 5));
                        break;
                    }
                    String mk = NasFileMatcher.matchKey(b);
                    String hit = xl.stream().filter(x -> NasFileMatcher.matchKey(x).equals(mk))
                            .findFirst().orElse("?");
                    System.out.println("RESCUE 百度[" + b + "] ≈ 迅雷[" + hit + "]");
                }
            }
        }
        System.out.printf("%n==== TOTAL exact_miss=%d match_miss=%d rescued=%d ====%n",
                totalExact, totalMatch, totalRescue);
        // 烟雾：至少救回过一批（半熟/推理），且真缺应明显小于 exact
        assertTrue(totalRescue > 0, "应有 cosmetic 救回");
        assertTrue(totalMatch < totalExact, "身份键真缺应小于 exact 判缺");
    }

    private static List<String> toList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode x : arr) {
            out.add(x.asText());
        }
        return out;
    }
}
