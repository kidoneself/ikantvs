package com.jyinshi.common.config;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.security.JwtAuthFilter;
import com.jyinshi.common.security.FrontendOriginFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jyinshi.common.security.FrontendOriginFilter;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 安全配置：无状态 JWT。公开放行健康检查/站点配置/内容读，其余需登录（运营后台）。
 */
@Configuration
public class WebSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final FrontendOriginFilter frontendOriginFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    public WebSecurityConfig(JwtAuthFilter jwtAuthFilter,
                             FrontendOriginFilter frontendOriginFilter,
                             CorsProperties corsProperties,
                             ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.frontendOriginFilter = frontendOriginFilter;
        this.corsProperties = corsProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/login",
                                "/api/site/config").permitAll()
                        // 公开读：前台信息流/详情/榜单/结果导向搜索/首页已更新（仅 GET）
                        .requestMatchers(HttpMethod.GET, "/api/media", "/api/media/**",
                                "/api/rankings", "/api/rankings/**",
                                "/api/search", "/api/search/**",
                                "/api/daily").permitAll()
                        // 公开写：前台行为埋点（匿名上报）
                        .requestMatchers(HttpMethod.POST, "/api/events").permitAll()
                        // 公开读：前台「大家在搜」热搜榜
                        .requestMatchers(HttpMethod.GET, "/api/events/hot-searches").permitAll()
                        // 公开：前台点击转存（匿名可用，控制器内按 IP 限频；仍受前端来源过滤保护）
                        .requestMatchers(HttpMethod.POST, "/api/transfer/execute").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/transfer/result").permitAll()
                        // 公开：迅雷 OAuth 回调（浏览器重定向，state=会话id 自校验；沿用老项目登记路径）
                        .requestMatchers(HttpMethod.GET, "/api/auto-resource/xunlei/callback").permitAll()
                        // 公开：短剧列表/搜索 + TGForwarder 导入（token 自校验）
                        .requestMatchers(HttpMethod.GET, "/api/drama/list", "/api/drama/search", "/api/drama/count").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/drama/import", "/api/drama/update-cover").permitAll()
                        .requestMatchers("/drama-covers/**").permitAll()
                        // 运营上传图（公告等）：本地磁盘，公开读
                        .requestMatchers("/api/uploads/**").permitAll()
                        // 活码页（微信扫码，无登录）
                        .requestMatchers(HttpMethod.GET, "/api/qr").permitAll()
                        // 飞书事件回调（口令「今日」等，无 Origin）
                        .requestMatchers("/api/feishu/**").permitAll()
                        // 微信机器人机机拉取今日更新（自带 token；无浏览器 Origin）
                        .requestMatchers(HttpMethod.GET, "/api/notify/today").permitAll()
                        // 入池脚本：Token 在控制器内校验；后台页走 JWT
                        .requestMatchers(HttpMethod.POST, "/api/admin/pool/ingest").permitAll()
                        .requestMatchers("/api/open/pool/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, resp, e) -> {
                    resp.setStatus(200);
                    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    resp.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
                }))
                .addFilterBefore(frontendOriginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 跨域：仅允许配置的页面 Origin（前台 example.com → api 子域等）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(corsProperties.originList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Refresh-Token"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
