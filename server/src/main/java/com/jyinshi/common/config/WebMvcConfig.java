package com.jyinshi.common.config;

import com.jyinshi.common.security.ratelimit.RateLimitInterceptor;
import com.jyinshi.content.config.DramaProperties;
import com.jyinshi.ops.config.UploadProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Spring MVC：限流拦截器；短剧封面 / 运营上传静态目录。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final DramaProperties dramaProperties;
    private final UploadProperties uploadProperties;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
                        DramaProperties dramaProperties,
                        UploadProperties uploadProperties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.dramaProperties = dramaProperties;
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dramaDir = toFileUri(dramaProperties.getCoverPath());
        registry.addResourceHandler("/drama-covers/**")
                .addResourceLocations(dramaDir)
                .setCachePeriod(2_592_000);

        String uploadDir = toFileUri(uploadProperties.getPath());
        // 挂在 /api/uploads 下，复用各站已有 /api/* 反代，无需改 Caddy
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations(uploadDir)
                .setCachePeriod(2_592_000);
    }

    private static String toFileUri(String path) {
        String dir = Path.of(path).toAbsolutePath().toUri().toString();
        return dir.endsWith("/") ? dir : dir + "/";
    }
}
