package com.jyinshi.content.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodeExtractorTest {

    @Test
    void extractHeadNumber() {
        assertEquals(2, EpisodeExtractor.extractEpisode("02-4K.mkv"));
        assertEquals("2", EpisodeExtractor.extractDisplay("02-4K.mkv"));
        assertEquals(12, EpisodeExtractor.extractEpisode("12.一斩苍穹.mp4"));
    }

    @Test
    void extractChineseSuffixEpisode() {
        // 01国语 / 06粤语：数字后直接跟中文
        assertEquals(1, EpisodeExtractor.extractEpisode("01国语.mp4"));
        assertEquals(6, EpisodeExtractor.extractEpisode("06粤语.mp4"));
        assertEquals("6", EpisodeExtractor.extractDisplay("06粤语.mp4"));
        assertEquals("6", EpisodeExtractor.pickLatest(
                EpisodeExtractor.extractDisplay("01国语.mp4"),
                EpisodeExtractor.extractDisplay("06粤语.mp4")));
        assertEquals("06粤语.mp4", EpisodeExtractor.pickLatestFileName(List.of(
                "01国语.mp4", "06粤语.mp4", "03国语.mp4")));
    }

    @Test
    void yyyymmddNotTakenAsEpisode() {
        // 不能把 20260117 开头 3 位当成集 202
        assertNull(EpisodeExtractor.extractEpisode("20260117.某综艺.mkv"));
        assertEquals("1.17", EpisodeExtractor.extractDisplay("20260117.某综艺.mkv"));
    }


    @Test
    void extractSxxExxUsesEpisodeNotSeason() {
        assertEquals(1, EpisodeExtractor.extractEpisode("S01E01.2026.2160p.mp4"));
        assertEquals(2, EpisodeExtractor.extractEpisode("S01E02.2026.2160p.mp4"));
        assertEquals("2", EpisodeExtractor.extractDisplay("S01E02.2026.2160p.mp4"));
    }

    @Test
    void pickLatestFilePrefersHigherEpisodeNotFirstDigit() {
        // 旧 bug：取名字里第一段数字 → S01E01/S01E02 都是 01，误选 E01
        String best = EpisodeExtractor.pickLatestFileName(List.of(
                "S01E01.2026.2160p.HQ.WEB-DL.H265.10bit.AAC.mp4",
                "S01E02.2026.2160p.HQ.WEB-DL.H265.10bit.AAC.mp4"));
        assertEquals("S01E02.2026.2160p.HQ.WEB-DL.H265.10bit.AAC.mp4", best);
    }

    @Test
    void shouldAdvanceOnlyWhenNewer() {
        assertTrue(EpisodeExtractor.shouldAdvanceFile(
                "S01E01.mp4", "S01E02.mp4"));
        assertFalse(EpisodeExtractor.shouldAdvanceFile(
                "S01E02.mp4", "S01E01.mp4"));
        assertFalse(EpisodeExtractor.shouldAdvanceFile(
                "02-4K.mkv", "01-4K.mkv"));
    }

    @Test
    void varietyDateFromYyyymmdd() {
        // 老站综艺：YYYYMMDD / YYYY-MM-DD → M.D（去前导 0）
        assertEquals("7.28", EpisodeExtractor.extractDisplay("半熟恋人.20260728.4K.mkv"));
        assertEquals("1.17", EpisodeExtractor.extractDisplay("某综艺.2026-01-17.1080P.mp4"));
        assertEquals("7.3", EpisodeExtractor.extractDisplay("乘风2026.2026.07.03.mkv"));
    }

    @Test
    void varietyDateOnlyAdvancesForward() {
        assertEquals("7.28", EpisodeExtractor.pickLatest("7.25", "7.28"));
        assertEquals("7.28", EpisodeExtractor.pickLatest("7.28", "7.25"));
        assertTrue(EpisodeExtractor.shouldAdvanceFile("节目.20260725.mkv", "节目.20260728.mkv"));
        assertFalse(EpisodeExtractor.shouldAdvanceFile("节目.20260728.mkv", "节目.20260725.mkv"));
    }

    @Test
    void episodeBeatsEmbeddedYearLikeOldSite() {
        // 优先级：集数 > 日期；S01E02.2026... 仍取 2，不取日期
        assertEquals("2", EpisodeExtractor.extractDisplay("S01E02.2026.2160p.HQ.mp4"));
    }

    @Test
    void empty() {
        assertNull(EpisodeExtractor.extractDisplay(null));
        assertNull(EpisodeExtractor.pickLatestFileName(List.of()));
    }
}
