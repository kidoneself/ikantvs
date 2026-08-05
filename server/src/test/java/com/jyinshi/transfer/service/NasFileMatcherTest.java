package com.jyinshi.transfer.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NasFileMatcherTest {

    @Test
    void varietyCosmeticVariantsSameKey() {
        // 开始推理吧：尾部 -、空格、4K、书名号
        assertEquals(
                NasFileMatcher.matchKey("2026.05.20-序.mp4"),
                NasFileMatcher.matchKey("2026.05.20-序-.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("2026.06.11-第3期下.mp4"),
                NasFileMatcher.matchKey("2026.06.11-第3期下-4K.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("2026.06.02-《副本解锁中》第2期.mp4"),
                NasFileMatcher.matchKey("2026.06.02-副本解锁中第2期.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("2026.07.14-解锁中加更第6期.mp4"),
                NasFileMatcher.matchKey("2026.07.14 解锁中加更第6期.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("2026.07.17-居民采访第6期.mp4"),
                NasFileMatcher.matchKey("2026.07.17.居民采访第6期.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("2026.07.25-推门彩蛋.mp4"),
                NasFileMatcher.matchKey("2026.07.25 推门彩蛋.mp4"));
    }

    @Test
    void varietySameDayDifferentEpisodesStayDistinct() {
        assertNotEquals(
                NasFileMatcher.matchKey("2026.07.11-推门彩蛋：侠客.mp4"),
                NasFileMatcher.matchKey("2026.07.11-推门彩蛋：神算子.mp4"));
        assertNotEquals(
                NasFileMatcher.matchKey("2026.07.09-第5期下.mp4"),
                NasFileMatcher.matchKey("2026.07.09-《副本存档中》第5期.mp4"));
        assertNotEquals(
                NasFileMatcher.matchKey("2026.07.08-第5期上.mp4"),
                NasFileMatcher.matchKey("2026.07.08-第5期下.mp4"));
    }

    @Test
    void varietyDateEmbeddedInTitle() {
        assertEquals(
                NasFileMatcher.matchKey("半熟恋人.20260728.4K.mkv"),
                NasFileMatcher.matchKey("半熟恋人.2026-07-28.1080P.mp4"));
    }

    @Test
    void dramaKeepsGuoyuYueyuDistinct() {
        assertNotEquals(
                NasFileMatcher.matchKey("01国语.mp4"),
                NasFileMatcher.matchKey("01粤语.mp4"));
        assertEquals(
                NasFileMatcher.matchKey("06粤语.mp4"),
                NasFileMatcher.matchKey("06粤语-4K.mkv"));
        assertEquals(
                NasFileMatcher.matchKey("02-4K.mkv"),
                NasFileMatcher.matchKey("02.mkv"));
        assertEquals(
                NasFileMatcher.matchKey("S01E02.2026.2160p.mp4"),
                NasFileMatcher.matchKey("02.一斩苍穹.mp4"));
    }

    @Test
    void alreadyHaveExactOrMatch() {
        Set<String> have = Set.of("2026.05.20-序-.mp4");
        Set<String> haveMatch = Set.of(NasFileMatcher.matchKey("2026.05.20-序-.mp4"));
        assertTrue(NasFileMatcher.alreadyHave("2026.05.20-序.mp4", have, haveMatch));
        assertFalse(NasFileMatcher.alreadyHave("2026.07.28-解锁中加更第8期.mp4", have, haveMatch));
    }

    @Test
    void realStartReasoningMissesStayMissing() {
        // 迅雷实有归一后仍缺的百度文件，键不能碰巧撞上无关文件
        Set<String> xl = Set.of(
                "2026.05.20-序-.mp4",
                "2026.07.26-花絮第7期.mp4",
                "2026.06.21.补给站加更.mp4");
        Set<String> xlMatch = xl.stream().map(NasFileMatcher::matchKey).collect(java.util.stream.Collectors.toSet());
        assertFalse(NasFileMatcher.alreadyHave("2026.04.29-名场面特辑第1期.mp4", xl, xlMatch));
        assertFalse(NasFileMatcher.alreadyHave("2026.07.28-解锁中加更第8期.mp4", xl, xlMatch));
        // 「补给站加更低2期」≠「补给站加更」
        assertFalse(NasFileMatcher.alreadyHave("2026.06.21-补给站加更低2期.mp4", xl, xlMatch));
    }
}
