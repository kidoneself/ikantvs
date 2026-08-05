package com.jyinshi.content.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebpDecodeTest {

    @Test
    void decodeWebpPoster() throws Exception {
        ImageIO.scanForPlugins();
        byte[] body = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("https://img.ikantvs.com/posters/2.webp")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
        assertNotNull(ImageIO.read(new ByteArrayInputStream(body)), "webp decode failed");
    }
}
