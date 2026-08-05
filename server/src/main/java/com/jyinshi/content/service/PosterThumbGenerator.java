package com.jyinshi.content.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

/**
 * 从海报原字节生成列表用 JPEG 缩略图。
 */
@Slf4j
final class PosterThumbGenerator {

    static {
        // 注册 WebP 解码（否则 ImageIO.read 对 .webp 返回 null）
        ImageIO.scanForPlugins();
    }

    private PosterThumbGenerator() {
    }

    static Optional<byte[]> toJpeg(byte[] source, int maxWidth) {
        if (source == null || source.length == 0 || maxWidth <= 0) {
            return Optional.empty();
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(source));
            if (img == null) {
                log.warn("无法解码图片（可能缺少格式支持），size={}B", source.length);
                return Optional.empty();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(img)
                    .width(maxWidth)
                    .outputFormat("jpg")
                    .outputQuality(0.82)
                    .toOutputStream(out);
            byte[] jpeg = out.toByteArray();
            return jpeg.length > 0 ? Optional.of(jpeg) : Optional.empty();
        } catch (Exception e) {
            log.warn("缩略图生成失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
