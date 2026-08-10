package com.jyinshi.content.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebpDecodeTest {

    /** 1×1 lossy WebP（不依赖外网；生产海报可为 https://img.example.com/...） */
    private static final byte[] TINY_WEBP = Base64.getDecoder().decode(
            "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=");

    @Test
    void decodeWebpPoster() throws Exception {
        ImageIO.scanForPlugins();
        assertNotNull(ImageIO.read(new ByteArrayInputStream(TINY_WEBP)), "webp decode failed");
    }
}
