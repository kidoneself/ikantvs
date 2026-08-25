package com.jyinshi.transfer.controller;

import com.jyinshi.common.api.PageResult;
import com.jyinshi.common.api.Result;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.common.security.AuthContext;
import com.jyinshi.transfer.dto.CookieAddRequest;
import com.jyinshi.transfer.dto.JobEnqueueRequest;
import com.jyinshi.transfer.dto.LoginSessionView;
import com.jyinshi.transfer.dto.MonitorEnableRequest;
import com.jyinshi.transfer.dto.XunleiAuthorizeRequest;
import com.jyinshi.transfer.dto.XunleiCodeRequest;
import org.springframework.util.StringUtils;
import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.entity.TransferJob;
import com.jyinshi.transfer.entity.TransferLoginSession;
import com.jyinshi.transfer.entity.TransferMonitor;
import com.jyinshi.transfer.service.TransferAccountService;
import com.jyinshi.transfer.service.TransferJobService;
import com.jyinshi.transfer.service.TransferLoginService;
import com.jyinshi.transfer.service.TransferMonitorService;
import com.jyinshi.transfer.service.TransferPanPointerService;
import com.jyinshi.transfer.service.XunleiOAuthClient;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台转存/追更管理（transfer 域）：任务入队/查看 + 监控启用/查看/手动补扫。
 */
@RestController
@RequestMapping("/api/admin/transfer")
public class TransferAdminController {

    private final TransferJobService jobService;
    private final TransferMonitorService monitorService;
    private final TransferAccountService accountService;
    private final TransferLoginService loginService;
    private final XunleiOAuthClient xunleiOAuth;
    private final com.jyinshi.transfer.config.TransferProperties props;
    private final TransferPanPointerService pointerService;

    public TransferAdminController(TransferJobService jobService,
                                   TransferMonitorService monitorService,
                                   TransferAccountService accountService,
                                   TransferLoginService loginService,
                                   XunleiOAuthClient xunleiOAuth,
                                   com.jyinshi.transfer.config.TransferProperties props,
                                   TransferPanPointerService pointerService) {
        this.jobService = jobService;
        this.monitorService = monitorService;
        this.accountService = accountService;
        this.loginService = loginService;
        this.xunleiOAuth = xunleiOAuth;
        this.props = props;
        this.pointerService = pointerService;
    }

    // ---- 任务 ----

    /** 手动入队一个任务（首转/追更/巡检）。 */
    @PostMapping("/jobs")
    public Result<TransferJob> enqueue(@RequestBody JobEnqueueRequest req) {
        AuthContext.requireStaff();
        return Result.success(jobService.enqueue(req));
    }

    /** 分页查看任务。 */
    @GetMapping("/jobs")
    public Result<PageResult<TransferJob>> listJobs(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String panType) {
        AuthContext.requireStaff();
        return Result.success(jobService.pageJobs(page, size, status, panType));
    }

    // ---- 追更监控 ----

    /** 启用（或更新）一条链接的追更。 */
    @PostMapping("/monitors")
    public Result<TransferMonitor> enableMonitor(@RequestBody MonitorEnableRequest req) {
        AuthContext.requireStaff();
        return Result.success(monitorService.enable(
                req.getMediaLinkId(), req.getPanType(), req.getShareUrl(), req.getSharePwd()));
    }

    /** 分页查看监控。 */
    @GetMapping("/monitors")
    public Result<PageResult<TransferMonitor>> listMonitors(@RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size,
                                                            @RequestParam(required = false) String status) {
        AuthContext.requireStaff();
        return Result.success(monitorService.page(page, size, status));
    }

    /** 立即给所有启用监控补扫一轮 probe（无视时段）。 */
    @PostMapping("/monitors/sweep")
    public Result<Map<String, Integer>> sweep() {
        AuthContext.requireStaff();
        return Result.success(Map.of("enqueued", monitorService.sweep()));
    }

    // ---- 账号 ----

    /** 账号列表（元数据，不含凭据）。 */
    @GetMapping("/accounts")
    public Result<List<TransferAccount>> accounts() {
        AuthContext.requireStaff();
        return Result.success(accountService.listAll());
    }

    /**
     * 删除账号（通常因封号）：直接删行 + 放弃其名下未删资源记录。返回放弃的资源记录条数。
     */
    @PostMapping("/accounts/{id}/delete")
    public Result<Map<String, Integer>> deleteAccount(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Result.success(Map.of("abandoned", accountService.requestRemove(id)));
    }

    /** 每盘追更号 / 片库号。 */
    @GetMapping("/pointers")
    public Result<List<com.jyinshi.transfer.dto.PanPointerVO>> listPointers() {
        AuthContext.requireStaff();
        return Result.success(pointerService.list());
    }

    @PostMapping("/pointers")
    public Result<List<com.jyinshi.transfer.dto.PanPointerVO>> savePointer(
            @RequestBody com.jyinshi.transfer.dto.PanPointerSaveRequest req) {
        AuthContext.requireStaff();
        pointerService.save(req);
        return Result.success(pointerService.list());
    }

    // ---- 百度开放平台删除令牌（隐式授权，避开网页删除验证码）----

    /**
     * 百度隐式授权页 URL。运营在浏览器打开 → 登录目标百度号 → 同意 → 授权页(oob)直接显示
     * 一串 access_token，复制回后台按号保存。约 30 天到期，重复此步换新令牌即可。
     */
    @GetMapping("/baidu/authorize-url")
    public Result<Map<String, String>> baiduAuthorizeUrl() {
        AuthContext.requireStaff();
        String url = "https://openapi.baidu.com/oauth/2.0/authorize"
                + "?response_type=token"
                + "&client_id=" + enc(props.getBaidu().getClientId())
                + "&redirect_uri=oob"
                + "&scope=" + enc(props.getBaidu().getScope())
                + "&display=page&confirm_login=0";
        return Result.success(Map.of("authorizeUrl", url));
    }

    /** 保存某百度号的删除令牌：粘贴授权页返回的整条 URL / fragment / 纯 access_token 均可。 */
    @PostMapping("/accounts/{id}/baidu-token")
    public Result<Void> setBaiduToken(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthContext.requireStaff();
        accountService.setBaiduToken(id, body.get("token"));
        return Result.success(null);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---- 加号 / 换号 ----

    /** 夸克/百度：粘贴 cookie 加号（accountName 留空）或换号（填已有号覆盖）。 */
    @PostMapping("/accounts/cookie")
    public Result<LoginSessionView> addByCookie(@RequestBody CookieAddRequest req) {
        AuthContext.requireStaff();
        TransferLoginSession s = loginService.startCookie(
                req.getPanType(), req.getAccountName(), req.getCookie());
        return Result.success(LoginSessionView.of(s));
    }

    /** 迅雷：发起授权，返回 {sessionId, authorizeUrl}。浏览器打开链接登录目标迅雷账号并同意。 */
    @PostMapping("/xunlei/authorize")
    public Result<Map<String, String>> xunleiAuthorize(@RequestBody XunleiAuthorizeRequest req) {
        AuthContext.requireStaff();
        TransferLoginSession s = loginService.startXunlei(req.getAccountName());
        String url = xunleiOAuth.buildAuthorizeUrl(s.getSessionId());
        return Result.success(Map.of(
                "sessionId", s.getSessionId(),
                "authorizeUrl", url,
                "redirectUri", xunleiOAuth.redirectUri() == null ? "" : xunleiOAuth.redirectUri()));
    }

    /**
     * 迅雷：手动回填授权码。回调域名（example.com）不通时的兜底——运营授权后把地址栏
     * 那串（含 code 的完整 URL 或纯 code）贴回来，主站换 refresh_token 置 pending，worker 领走落号。
     * 注意迅雷 code 仅约 120s 有效，授权后要尽快提交。
     */
    @PostMapping("/xunlei/code")
    public Result<LoginSessionView> xunleiCode(@RequestBody XunleiCodeRequest req) {
        AuthContext.requireStaff();
        String code = extractCode(req.getCode());
        if (!StringUtils.hasText(req.getSessionId()) || !StringUtils.hasText(code)) {
            throw new BizException("sessionId / code 不能为空");
        }
        String refreshToken = xunleiOAuth.exchangeCode(code);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException("授权码换 token 失败（多半已超 120s 过期，或 scope/secret 不对），请重新授权");
        }
        loginService.attachXunleiToken(req.getSessionId().trim(), refreshToken);
        return Result.success(LoginSessionView.of(loginService.get(req.getSessionId().trim())));
    }

    private static final java.util.regex.Pattern CODE_P =
            java.util.regex.Pattern.compile("[?&#]code=([^&\\s]+)");

    /** 从「完整回调 URL」抽出 code；用户只贴了 code 本身时原样返回。 */
    private static String extractCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        java.util.regex.Matcher m = CODE_P.matcher(s);
        if (m.find()) {
            return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
        }
        return s;
    }

    /** 前端轮询加号会话状态/结果。 */
    @GetMapping("/login/{sessionId}")
    public Result<LoginSessionView> loginStatus(@PathVariable String sessionId) {
        AuthContext.requireStaff();
        return Result.success(LoginSessionView.of(loginService.get(sessionId)));
    }
}
