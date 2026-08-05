package com.jyinshi.transfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.ops.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 迅雷开放平台 OAuth（主站侧，照搬老项目 XunleiOpenClient 已验证的授权码换 token）。
 *
 * <p>只负责：拼授权页 URL + 用 code 换 refresh_token。应用级配置（client-id/secret/redirect-uri）
 * 走 {@code sys_config}（后台「迅雷转存」可改）。换来的 refresh_token 由 {@link TransferLoginService}
 * 就地落号入库。</p>
 */
@Slf4j
@Service
public class XunleiOAuthClient {

    private static final String TOKEN_URL = "https://xluser-ssl.xunlei.com/v1/auth/token";
    private static final String AUTH_PAGE = "https://i.xunlei.com/center/account/personal/oauth";
    private static final String SCOPE = "user offline sso pan profile";

    private final SysConfigService config;
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public XunleiOAuthClient(SysConfigService config, ObjectMapper om) {
        this.config = config;
        this.om = om;
    }

    private String clientId() {
        return config.getOrDefault(SysConfigService.TRANSFER_XUNLEI_CLIENT_ID, "");
    }

    private String clientSecret() {
        return config.getOrDefault(SysConfigService.TRANSFER_XUNLEI_CLIENT_SECRET, "");
    }

    /** 回调地址（迅雷开放平台须登记同一个）。 */
    public String redirectUri() {
        return config.getOrDefault(SysConfigService.TRANSFER_XUNLEI_REDIRECT_URI, "");
    }

    /**
     * 构造迅雷授权页 URL。浏览器打开 → 登录目标迅雷账号 → 同意 → 迅雷带 code 回调 redirectUri。
     * state 用登录会话 id，回调时据此定位会话（防串号 + 防 CSRF）。
     */
    public String buildAuthorizeUrl(String state) {
        return AUTH_PAGE
                + "?response_type=code"
                + "&client_id=" + enc(clientId())
                + "&redirect_uri=" + enc(redirectUri())
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state);
    }

    /**
     * 用授权码换 refresh_token（grant_type=authorization_code，需 client_secret）。
     * 成功返回 refresh_token，失败返回 null。
     */
    public String exchangeCode(String code) {
        try {
            String body = om.createObjectNode()
                    .put("client_id", clientId())
                    .put("client_secret", clientSecret())
                    .put("grant_type", "authorization_code")
                    .put("code", code)
                    .toString();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode r = om.readTree(resp.body());
            String rt = r.path("refresh_token").asText("");
            if (rt.isBlank()) {
                log.warn("[迅雷] 授权码换 token 失败（refresh_token 为空，检查 scope 是否含 offline / client_secret）: {}", resp.body());
                return null;
            }
            return rt;
        } catch (Exception e) {
            log.warn("[迅雷] 授权码换 token 异常: {}", e.getMessage());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
