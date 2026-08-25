package com.jyinshi.content.pool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolPasteParserTest {

    @Test
    void parseSpecExample() {
        String text = """
                「混沌少年时 (2025)」
                链接：https://pan.quark.cn/s/d1392249bbde

                ❤️10部合家欢国风动画电影
                https://pan.quark.cn/s/3b0b5b32a6f7
                下载地址: https://pan.baidu.com/s/xxxx?pwd=abcd
                提取码：ab12
                """;
        List<PoolPasteParser.Item> items = PoolPasteParser.parse(text);
        assertEquals(3, items.size());
        assertEquals("混沌少年时 (2025)", items.get(0).title());
        assertEquals("quark", items.get(0).panType());
        assertTrue(items.get(0).url().contains("d1392249bbde"));

        assertEquals("❤️10部合家欢国风动画电影", items.get(1).title());
        assertEquals("quark", items.get(1).panType());

        assertEquals("❤️10部合家欢国风动画电影", items.get(2).title());
        assertEquals("baidu", items.get(2).panType());
        assertEquals("abcd", items.get(2).password());
    }

    @Test
    void consecutiveUrlsShareTitle() {
        String text = """
                某剧
                https://pan.quark.cn/s/aaa
                https://pan.xunlei.com/s/bbb
                """;
        List<PoolPasteParser.Item> items = PoolPasteParser.parse(text);
        assertEquals(2, items.size());
        assertEquals("某剧", items.get(0).title());
        assertEquals("某剧", items.get(1).title());
        assertEquals("quark", items.get(0).panType());
        assertEquals("xunlei", items.get(1).panType());
    }

    @Test
    void skipUnknownUrl() {
        String text = """
                标题
                https://example.com/not-a-share
                https://pan.quark.cn/s/okshare
                """;
        List<PoolPasteParser.Item> items = PoolPasteParser.parse(text);
        assertEquals(1, items.size());
        assertEquals("okshare", items.get(0).url().substring(items.get(0).url().lastIndexOf('/') + 1));
    }

    @Test
    void magnetAndEd2k() {
        String text = """
                资源
                magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890
                ed2k://|file|foo.mkv|1|
                """;
        List<PoolPasteParser.Item> items = PoolPasteParser.parse(text);
        assertEquals(2, items.size());
        assertEquals("magnet", items.get(0).panType());
        assertEquals("ed2k", items.get(1).panType());
    }

    @Test
    void baiduPwdOnNextLine() {
        String text = """
                电影
                https://pan.baidu.com/s/1abcdefghijk
                密码：xy12
                """;
        List<PoolPasteParser.Item> items = PoolPasteParser.parse(text);
        assertEquals(1, items.size());
        assertEquals("xy12", items.get(0).password());
    }
}
