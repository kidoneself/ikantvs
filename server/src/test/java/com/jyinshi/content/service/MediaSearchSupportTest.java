package com.jyinshi.content.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaSearchSupportTest {

    @Test
    void splitHanChars() {
        assertEquals(List.of("沙", "丘"), MediaSearchSupport.splitTokens("沙丘"));
        assertEquals(List.of("丘", "沙"), MediaSearchSupport.splitTokens("丘沙"));
        assertEquals(List.of("沙", "邱"), MediaSearchSupport.splitTokens("沙邱"));
        assertTokens("繁花", "繁", "花");
        assertTokens("花繁", "繁", "花");
    }

    private static void assertTokens(String input, String... expected) {
        List<String> t = MediaSearchSupport.splitTokens(input);
        assertEquals(expected.length, t.size());
        for (String e : expected) {
            assertTrue(t.contains(e), "missing " + e + " in " + t);
        }
    }

    @Test
    void splitEnglishWholeWord() {
        assertEquals(List.of("Dune"), MediaSearchSupport.splitTokens("Dune"));
    }

    @Test
    void splitSingleHan() {
        assertEquals(List.of("爱"), MediaSearchSupport.splitTokens("爱"));
    }

    @Test
    void splitSkipsSpace() {
        List<String> t = MediaSearchSupport.splitTokens("沙 丘");
        assertTrue(t.contains("沙"));
        assertTrue(t.contains("丘"));
    }
}
