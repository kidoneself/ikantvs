package com.jyinshi.transfer.controller;

import com.jyinshi.transfer.service.TransferLoginService;
import com.jyinshi.transfer.service.XunleiOAuthClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 迅雷 OAuth 回调（公开，浏览器重定向至此）。
 *
 * <p>state = 登录会话 id：校验会话存在且待授权 → 用 code 换 refresh_token →
 * 写进会话（转 pending，worker 随后领走落号）。直接回 HTML 给浏览器看结果。</p>
 *
 * <p>路径沿用老项目登记的 {@code /api/auto-resource/xunlei/callback}（免改迅雷开放平台登记），
 * 已在 WebSecurityConfig 放行。</p>
 */
@Slf4j
@RestController
public class TransferXunleiCallbackController {

    private final TransferLoginService loginService;
    private final XunleiOAuthClient xunleiOAuth;

    public TransferXunleiCallbackController(TransferLoginService loginService, XunleiOAuthClient xunleiOAuth) {
        this.loginService = loginService;
        this.xunleiOAuth = xunleiOAuth;
    }

    @GetMapping(value = "/api/auto-resource/xunlei/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error) {
        if (StringUtils.hasText(error)) {
            return html("授权被拒绝或出错：" + escape(error));
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return html("回调参数缺失（code/state）");
        }
        String refreshToken = xunleiOAuth.exchangeCode(code);
        if (!StringUtils.hasText(refreshToken)) {
            return html("换 token 失败（多半 scope 没带 offline 或 client_secret 不对），请看后端日志");
        }
        try {
            loginService.attachXunleiToken(state, refreshToken);
        } catch (Exception e) {
            log.warn("[迅雷] 回调写会话失败 state={}: {}", state, e.getMessage());
            return html("授权成功但会话异常：" + escape(e.getMessage()) + "（可能已过期，请回后台重新发起）");
        }
        return html("✅ 迅雷授权成功，正在落号。可关闭本页，回后台稍候即见新账号。");
    }

    private String html(String msg) {
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>迅雷授权</title></head>"
                + "<body style=\"font-family:system-ui;padding:2rem;line-height:1.6;color:#333\">"
                + "<h3>迅雷授权</h3><p>" + msg + "</p></body></html>";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
