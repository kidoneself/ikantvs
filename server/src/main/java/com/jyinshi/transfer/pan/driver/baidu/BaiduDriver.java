package com.jyinshi.transfer.pan.driver.baidu;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.driver.AdCleanup;
import com.jyinshi.transfer.pan.driver.AdFilter;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.PanFile;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.SaveResult;
import com.jyinshi.transfer.pan.driver.ShareContext;
import com.jyinshi.transfer.pan.driver.ShareInfo;
import com.jyinshi.transfer.pan.exec.StepTimer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度网盘驱动（从老项目 BaiduPanClient 移植 + 瘦身）。
 *
 * <p>转存流程：解析链接 → 验证提取码取 sekey → 取分享列表(shareId/uk/files)
 * → 建目标文件夹 → transfer → 建分享。文件夹 fs_id 转存时服务端会递归复制，
 * 故这里不做逐层递归/广告过滤（保持薄）。</p>
 *
 * <p>{@link #getShareInfo} 免登录（用分享自带 sekey/BDCLND）；{@link #save} 用账号 cookie。</p>
 */
@Slf4j
@Component
public class BaiduDriver implements PanDriver {

    private static final String API_VERIFY = "https://pan.baidu.com/share/verify";
    private static final String API_SHARE_LIST = "https://pan.baidu.com/share/list";
    private static final String API_TRANSFER = "https://pan.baidu.com/share/transfer";
    private static final String API_CREATE = "https://pan.baidu.com/api/create";
    private static final String API_SHARE = "https://pan.baidu.com/share/pset";
    private static final String API_LIST = "https://pan.baidu.com/api/list";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";
    private static final String DEFAULT_PWD = "yyds";
    private static final String DEFAULT_TARGET_PATH = "/临时转存文件夹";
    private static final int TIMEOUT = 30_000;

    @Override
    public PanType type() {
        return PanType.BAIDU;
    }

    // ==================== 追更 / 检测 ====================

    @Override
    public ShareInfo getShareInfo(String shareUrl, String password, Account account) {
        ShareUrlInfo u = parseShareUrl(shareUrl);
        if (u == null) {
            return ShareInfo.bad("无效的百度链接");
        }
        String pwd = firstNonBlank(password, u.pwd, DEFAULT_PWD);
        try {
            VerifyOutcome vr = verify(u.surl, pwd, null, "");
            if (!vr.ok()) {
                // 终态码才判失效；提取码错误/网络等瞬时问题回 uncertain，避免误杀
                return vr.terminal()
                        ? ShareInfo.bad("分享已失效：" + vr.message())
                        : ShareInfo.uncertain("提取码验证失败，请稍后重试");
            }
            String sekey = vr.sekey();
            JSONObject list = shareList(u.surl, sekey, null, "");
            if (list == null) {
                return ShareInfo.uncertain("获取分享列表请求失败（网络/HTTP）");
            }
            int errno = list.getInt("errno", -1);
            if (errno != 0) {
                // 分享删除/取消/过期/失效 → 明确死链；其余不确定，主站不据此标失效
                if (errno == -21 || errno == -19 || errno == -12 || errno == 105) {
                    return ShareInfo.bad(mapErr(errno));
                }
                return ShareInfo.uncertain(mapErr(errno));
            }

            JSONArray files = list.getJSONArray("list");
            long maxMtime = 0, totalSize = 0;
            int count = files == null ? 0 : files.size();
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    totalSize += f.getLong("size", 0L);
                    maxMtime = Math.max(maxMtime, f.getLong("server_mtime", 0L));
                }
            }

            ShareInfo info = new ShareInfo();
            info.setOk(true);
            info.setCheckState("ok");
            info.setTitle(list.getStr("title", ""));
            info.setUpdatedAt(maxMtime * 1000); // 百度是秒，统一转毫秒
            info.setFileCount(count);
            info.setSize(totalSize);
            info.setMessage("ok");
            return info;

        } catch (Exception e) {
            log.warn("[百度] getShareInfo 异常: {}", e.getMessage());
            return ShareInfo.uncertain("异常: " + e.getMessage());
        }
    }

    // ==================== 转存 ====================

    @Override
    public SaveResult save(String shareUrl, String password, Account account, String toFolderId) {
        StepTimer t = StepTimer.of("baidu/" + account.getName());
        ShareUrlInfo u = parseShareUrl(shareUrl);
        if (u == null) {
            t.logDone(false, "err=INVALID_URL");
            return SaveResult.error("INVALID_URL", "无效的百度链接");
        }
        String cookie = account.getCookie();
        if (cookie == null || cookie.isBlank()) {
            t.logDone(false, "err=NO_COOKIE");
            return SaveResult.error("NO_COOKIE", "账号无 cookie: " + account.getName());
        }
        try {
            String bdstoken = fetchBdstoken(cookie);
            if (bdstoken == null) bdstoken = "";
            t.step("bdstoken");

            String pwd = firstNonBlank(password, u.pwd, null);
            String sekey;
            if (pwd != null && !pwd.isBlank()) {
                VerifyOutcome vr = verify(u.surl, pwd, cookie, bdstoken);
                if (!vr.ok()) {
                    String code = vr.terminal() ? "SHARE_INVALID" : "VERIFY_FAILED";
                    t.logDone(false, "err=" + code);
                    return SaveResult.error(code, vr.terminal()
                            ? "分享已失效：" + vr.message()
                            : "提取码验证失败，请稍后重试");
                }
                sekey = vr.sekey();
            } else {
                sekey = decode(extractFromCookie(cookie, "BDCLND"));
            }
            t.step("verify");

            JSONObject list = shareList(u.surl, sekey, cookie, bdstoken);
            if (list == null) {
                t.logDone(false, "err=LIST_FAILED");
                return SaveResult.error("LIST_FAILED", "获取分享列表请求失败");
            }
            int listErrno = list.getInt("errno", -1);
            if (listErrno != 0) {
                String code = isTerminalShareErrno(listErrno) ? "SHARE_INVALID" : "LIST_FAILED";
                t.logDone(false, "err=" + code);
                return SaveResult.error(code, mapErr(listErrno));
            }
            long shareId = list.getLong("share_id", 0L);
            long uk = list.getLong("uk", 0L);
            JSONArray files = list.getJSONArray("list");
            if (files == null || files.isEmpty()) {
                t.logDone(false, "err=NO_FILES");
                return SaveResult.error("NO_FILES", "分享为空");
            }

            String packName;
            List<Long> fsIds = new ArrayList<>();
            if (files.size() == 1 && files.getJSONObject(0).getInt("isdir", 0) == 1) {
                JSONObject dir = files.getJSONObject(0);
                packName = sanitize(dir.getStr("server_filename", ""));
                JSONObject sub = shareListDir(u.surl, sekey, cookie, bdstoken, dir.getStr("path"));
                JSONArray subFiles = sub == null ? null : sub.getJSONArray("list");
                if (subFiles == null || subFiles.isEmpty()) {
                    t.logDone(false, "err=NO_FILES");
                    return SaveResult.error("NO_FILES", "分享文件夹为空");
                }
                collectNonAdFsIds(subFiles, fsIds);
            } else {
                packName = packDirName(list.getStr("title", ""), files, shareId);
                collectNonAdFsIds(files, fsIds);
            }
            if (fsIds.isEmpty()) {
                t.logDone(false, "err=NO_FILES");
                return SaveResult.error("NO_FILES", "过滤广告后无有效文件");
            }
            t.step("list");

            // 落地父目录：主站下发的 toFolderId(如 /追更资源、/临时转存) 优先，其次账号级默认夹，最后兜底默认
            String base = (toFolderId != null && !toFolderId.isBlank())
                    ? toFolderId
                    : (account.getTargetDirFid() != null && !account.getTargetDirFid().isBlank()
                        ? account.getTargetDirFid() : DEFAULT_TARGET_PATH);
            Folder folder = createFolder(base + "/" + packName, cookie, bdstoken);
            if (folder == null) {
                t.logDone(false, "err=CREATE_FOLDER_FAILED");
                return SaveResult.error("CREATE_FOLDER_FAILED", "创建目标文件夹失败");
            }
            long targetFolderId = folder.fsId();
            String savePath = folder.path();
            t.step("mkdir");

            String transferErr = doTransfer(shareId, uk, sekey, fsIds, savePath, cookie, bdstoken);
            if (transferErr != null) {
                t.logDone(false, "err=TRANSFER_FAILED");
                return SaveResult.error("TRANSFER_FAILED", transferErr);
            }
            t.step("transfer");

            // 深层清理：首转过滤只覆盖顶层，嵌套子夹里的广告随文件夹递归复制进来，落地后递归删
            AdCleanup.run("百度", savePath,
                    path -> listFolder(account, path),
                    paths -> delete(account, paths));
            t.step("cleanupAds");

            String link = createShare(List.of(targetFolderId), DEFAULT_PWD, cookie, bdstoken);
            String myShareUrl = link == null ? null : link + "?pwd=" + DEFAULT_PWD;
            t.step("share");
            t.logDone(link != null, null);
            return SaveResult.ok(myShareUrl, DEFAULT_PWD, savePath, account.getName());

        } catch (Exception e) {
            log.error("[百度] 转存异常", e);
            t.logDone(false, "err=EXCEPTION");
            return SaveResult.error("EXCEPTION", "转存异常: " + e.getMessage());
        }
    }

    /** 收集非广告文件的 fs_id（首转过滤广告）。 */
    private void collectNonAdFsIds(JSONArray files, List<Long> out) {
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.getStr("server_filename", "");
            if (AdFilter.isAd(name, f.getLong("size", 0L), f.getInt("isdir", 0) == 1)) {
                log.info("[百度] 首转过滤广告: {}", name);
                continue;
            }
            out.add(f.getLong("fs_id"));
        }
    }

    // ==================== 增量同步原语（追更） ====================

    @Override
    public boolean supportsSync() {
        return true;
    }

    @Override
    public ShareContext openShare(String shareUrl, String password, Account account) {
        ShareUrlInfo u = parseShareUrl(shareUrl);
        if (u == null) {
            return ShareContext.fail("无效的百度链接");
        }
        String cookie = account != null ? account.getCookie() : null;
        if (cookie == null || cookie.isBlank()) {
            return ShareContext.fail("百度增量同步需要账号 cookie");
        }
        try {
            String bdstoken = fetchBdstoken(cookie);
            if (bdstoken == null) {
                bdstoken = "";
            }
            String pwd = firstNonBlank(password, u.pwd, null);
            String sekey;
            if (pwd != null && !pwd.isBlank()) {
                VerifyOutcome vr = verify(u.surl, pwd, cookie, bdstoken);
                if (!vr.ok()) {
                    return ShareContext.fail(vr.terminal()
                            ? "分享已失效：" + vr.message()
                            : "提取码验证失败，请稍后重试");
                }
                sekey = vr.sekey();
            } else {
                sekey = decode(extractFromCookie(cookie, "BDCLND"));
            }
            JSONObject list = shareList(u.surl, sekey, cookie, bdstoken);
            if (list == null || list.getInt("errno", -1) != 0) {
                return ShareContext.fail("获取分享信息失败");
            }
            ShareContext ctx = new ShareContext();
            ctx.setOk(true);
            ctx.setShareId(String.valueOf(list.getLong("share_id", 0L)));
            ctx.setToken(sekey);
            ctx.setRootDirId(""); // 百度分享根用空串（listShareDir 走 root=1）
            ctx.getExtra().put("uk", list.getLong("uk", 0L));
            ctx.getExtra().put("surl", u.surl);
            ctx.getExtra().put("cookie", cookie);
            ctx.getExtra().put("bdstoken", bdstoken);
            return ctx;
        } catch (Exception e) {
            log.warn("[百度] openShare 异常: {}", e.getMessage());
            return ShareContext.fail("打开分享异常: " + e.getMessage());
        }
    }

    @Override
    public List<PanFile> listShareDir(ShareContext ctx, String subDirId) {
        List<PanFile> out = new ArrayList<>();
        String surl = (String) ctx.getExtra().get("surl");
        String cookie = (String) ctx.getExtra().get("cookie");
        String bdstoken = (String) ctx.getExtra().get("bdstoken");
        JSONObject root = (subDirId == null || subDirId.isBlank())
                ? shareList(surl, ctx.getToken(), cookie, bdstoken)
                : shareListDir(surl, ctx.getToken(), cookie, bdstoken, subDirId);
        if (root == null || root.getInt("errno", -1) != 0) {
            return out;
        }
        JSONArray files = root.getJSONArray("list");
        if (files == null) {
            return out;
        }
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            PanFile pf = PanFile.of(
                    String.valueOf(f.getLong("fs_id", 0L)),
                    f.getStr("server_filename", ""),
                    f.getInt("isdir", 0) == 1,
                    f.getLong("size", 0L),
                    null);
            pf.setSubId(f.getStr("path")); // 分享内路径，递归子目录用
            out.add(pf);
        }
        return out;
    }

    @Override
    public List<PanFile> listFolder(Account account, String folderId) {
        List<PanFile> out = new ArrayList<>();
        JSONArray files = listFolderRaw(account, folderId);
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            // 目标夹侧 id 用「本账号内路径」，供递归/转存作为目标目录
            out.add(PanFile.of(
                    f.getStr("path"),
                    f.getStr("server_filename", ""),
                    f.getInt("isdir", 0) == 1,
                    f.getLong("size", 0L),
                    null));
        }
        return out;
    }

    /** 列目录原始 JSON；失败返回 null。 */
    private JSONArray listFolderRaw(Account account, String folderId) {
        String cookie = account.getCookie();
        String bdstoken = fetchBdstoken(cookie);
        String path = (folderId == null || folderId.isBlank()) ? "/" : folderId;
        String url = API_LIST + "?order=name&desc=0&showempty=0&web=1&page=1&num=1000"
                + "&dir=" + URLEncoder.encode(path, StandardCharsets.UTF_8)
                + "&bdstoken=" + (bdstoken == null ? "" : bdstoken)
                + "&channel=chunlei&clienttype=0&app_id=250528";
        Map<String, String> headers = baseHeaders(cookie);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Referer", "https://pan.baidu.com/disk/main");
        HttpResponse resp = get(url, headers);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject rootObj = JSONUtil.parseObj(resp.body());
        if (rootObj.getInt("errno", -1) != 0) {
            return null;
        }
        JSONArray files = rootObj.getJSONArray("list");
        return files != null ? files : new JSONArray();
    }

    @Override
    public String ensureFolder(Account account, String parentFolderId, String name) {
        String parent = (parentFolderId == null || parentFolderId.isBlank()) ? "/" : parentFolderId;
        List<PanFile> siblings = listFolder(account, parent);
        if (siblings == null) {
            return null;
        }
        for (PanFile f : siblings) {
            if (f.isDir() && name.equals(f.getName())) {
                return f.getId(); // 已存在，复用其路径
            }
        }
        String cookie = account.getCookie();
        String bdstoken = fetchBdstoken(cookie);
        String childPath = parent.endsWith("/") ? parent + name : parent + "/" + name;
        Folder folder = createFolder(childPath, cookie, bdstoken == null ? "" : bdstoken);
        return folder != null ? folder.path() : null;
    }

    @Override
    public int saveFiles(ShareContext ctx, List<PanFile> files, Account account, String targetFolderId) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        long shareId = Long.parseLong(ctx.getShareId());
        long uk = ((Number) ctx.getExtra().get("uk")).longValue();
        String cookie = account.getCookie();
        String bdstoken = (String) ctx.getExtra().get("bdstoken");
        List<Long> fsIds = new ArrayList<>();
        for (PanFile f : files) {
            fsIds.add(Long.parseLong(f.getId()));
        }
        String err = doTransfer(shareId, uk, ctx.getToken(), fsIds,
                targetFolderId, cookie, bdstoken == null ? "" : bdstoken);
        return err == null ? files.size() : 0;
    }

    /** 取分享内子目录列表（dir 为分享内路径）。 */
    private JSONObject shareListDir(String surl, String sekey, String cookie, String bdstoken, String dir) {
        String url = API_SHARE_LIST + "?web=5&app_id=250528&desc=1&showempty=0&page=1&num=1000"
                + "&order=time&shorturl=" + surl
                + "&dir=" + URLEncoder.encode(dir, StandardCharsets.UTF_8)
                + "&view_mode=1&channel=chunlei&web=1&bdstoken=" + bdstoken + "&clienttype=0";
        Map<String, String> headers = baseHeaders(cookie);
        headers.put("Accept", "*/*");
        headers.put("Referer", "https://pan.baidu.com/s/1" + surl);
        applyBdclnd(headers, sekey);
        HttpResponse resp = get(url, headers);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        return JSONUtil.parseObj(resp.body());
    }

    /** 开放平台删除令牌失效（需后台重新授权）的哨兵返回值。 */
    private static final int TOKEN_EXPIRED = -1;

    @Override
    public int delete(Account account, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }
        // 优先走开放平台官方接口删除（避开网页接口天天要短信验证码）
        String accessToken = account.getBaiduAccessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            int r = deleteViaOpenApi(accessToken, paths);
            if (r >= 0) {
                return r;
            }
            if (r == TOKEN_EXPIRED) {
                log.warn("[百度] 开放平台删除令牌失效，请后台「换删除令牌」重新授权；本次不回退网页接口以免触发验证码");
                return 0; // 交回重试，重授权后自然删掉
            }
            log.warn("[百度] 开放平台删除异常，回退网页接口");
        }
        return deleteViaWeb(account, paths); // 无令牌 / 非令牌类失败 → 老网页接口兜底
    }

    /**
     * 开放平台官方删除：POST xpan/file?method=filemanager&opera=delete&access_token=...
     * filelist 传绝对路径数组（与转存记录里的目录 path 一致）。
     * 返回删除条数；令牌失效返回 {@link #TOKEN_EXPIRED}(-1)；其它失败返回 -2。
     */
    private int deleteViaOpenApi(String accessToken, List<String> paths) {
        String url = "https://pan.baidu.com/rest/2.0/xpan/file"
                + "?method=filemanager&opera=delete&access_token="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray();
        for (String p : paths) {
            arr.add(p);
        }
        String body = "async=0&ondup=fail&filelist="
                + URLEncoder.encode(arr.toString(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        HttpResponse resp = post(url, body, headers);
        if (resp == null || !resp.isOk()) {
            return -2;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        int errno = root.getInt("errno", -1);
        if (errno == 0) {
            return paths.size();
        }
        if (errno == 110 || errno == 111 || errno == -6) { // token 无效/过期/鉴权失败
            return TOKEN_EXPIRED;
        }
        log.warn("[百度] 开放平台删除失败 errno={}", errno);
        return -2;
    }

    /** 网页接口删除（老实现，作为无开放平台令牌时的兜底；风控会返回 errno 132 要验证码）。 */
    private int deleteViaWeb(Account account, List<String> paths) {
        String cookie = account.getCookie();
        if (cookie == null || cookie.isBlank()) {
            return 0;
        }
        String bdstoken = fetchBdstoken(cookie);
        if (bdstoken == null) {
            bdstoken = "";
        }
        String url = "https://pan.baidu.com/api/filemanager?opera=delete&async=1&onnest=fail"
                + "&channel=chunlei&web=1&app_id=250528&bdstoken=" + bdstoken
                + "&clienttype=0&dp-logid=" + dpLogid();
        JSONArray arr = new JSONArray();
        for (String p : paths) {
            arr.add(p);
        }
        String body = "filelist=" + URLEncoder.encode(arr.toString(), StandardCharsets.UTF_8);
        HttpResponse resp = post(url, body, shareHeaders(cookie));
        if (resp == null || !resp.isOk()) {
            return 0;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        int errno = root.getInt("errno", -1);
        if (errno != 0) {
            log.warn("[百度] 删除失败: {}", mapErr(errno));
            return 0;
        }
        return paths.size();
    }

    // ==================== 内部：百度 API ====================

    /** 验证提取码 → sekey（randsk 解码）。cookie 可空（匿名巡检）。 */
    /**
     * verify 结果。errno 蕴含活死信息，不能一律丢弃：
     *   sekey != null            → 提取码通过
     *   sekey == null && terminal → 分享过期/取消/删除（真失效，可据此隐藏链接）
     *   sekey == null && !terminal → 提取码错误/Cookie 失效/网络（瞬时或账号问题，不得误杀）
     */
    private record VerifyOutcome(String sekey, boolean terminal, String message) {
        boolean ok() {
            return sekey != null;
        }
    }

    private VerifyOutcome verify(String surl, String pwd, String cookie, String bdstoken) {
        String url = API_VERIFY + "?t=" + System.currentTimeMillis()
                + "&surl=" + surl + "&channel=chunlei&web=1&app_id=250528"
                + "&bdstoken=" + bdstoken + "&clienttype=0";
        Map<String, String> headers = baseHeaders(cookie);
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        headers.put("Origin", "https://pan.baidu.com");
        headers.put("Referer", "https://pan.baidu.com/share/init?surl=" + surl);

        HttpResponse resp = post(url, "pwd=" + pwd + "&vcode=&vcode_str=", headers);
        if (resp == null || !resp.isOk()) {
            return new VerifyOutcome(null, false, "verify 请求失败");
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        int errno = root.getInt("errno", -1);
        if (errno != 0) {
            // 仅过期/取消/删除算真失效；提取码错误(-9)、Cookie 失效(-6/-62) 属瞬时/账号问题
            boolean terminal = errno == -12 || errno == -19 || errno == -21;
            log.warn("[百度] verify 失败 errno={}, msg={}, terminal={}", errno, mapErr(errno), terminal);
            return new VerifyOutcome(null, terminal, mapErr(errno));
        }
        return new VerifyOutcome(decode(root.getStr("randsk")), false, "ok");
    }

    /** 取分享根目录列表。 */
    private JSONObject shareList(String surl, String sekey, String cookie, String bdstoken) {
        String url = API_SHARE_LIST + "?web=5&app_id=250528&desc=1&showempty=0&page=1&num=100"
                + "&order=time&shorturl=" + surl + "&root=1&view_mode=1"
                + "&channel=chunlei&web=1&bdstoken=" + bdstoken + "&clienttype=0";
        Map<String, String> headers = baseHeaders(cookie);
        headers.put("Accept", "*/*");
        headers.put("Referer", "https://pan.baidu.com/s/1" + surl);
        applyBdclnd(headers, sekey);
        HttpResponse resp = get(url, headers);
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        int errno = root.getInt("errno", -1);
        if (errno != 0) {
            log.warn("[百度] list 失败: {}", mapErr(errno));
        }
        return root; // 即使 errno!=0 也回传，由调用方按 errno 决定 bad/uncertain
    }

    /** 创建目标文件夹，返回 fsId + 实际路径（重名时百度会加时间戳后缀）。 */
    private Folder createFolder(String path, String cookie, String bdstoken) {
        String url = API_CREATE + "?a=commit&bdstoken=" + bdstoken
                + "&clienttype=0&app_id=250528&web=1&dp-logid=" + dpLogid();
        String body = "path=" + URLEncoder.encode(path, StandardCharsets.UTF_8)
                + "&isdir=1&block_list=" + URLEncoder.encode("[]", StandardCharsets.UTF_8);
        HttpResponse resp = post(url, body, shareHeaders(cookie));
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("errno", -1) != 0) {
            log.warn("[百度] 建文件夹失败: {}", mapErr(root.getInt("errno", -1)));
            return null;
        }
        return new Folder(root.getLong("fs_id", 0L), root.getStr("path", path));
    }

    /** 执行转存；成功返回 null，失败返回错误消息。 */
    private String doTransfer(long shareId, long uk, String sekey, List<Long> fsIds,
                              String savePath, String cookie, String bdstoken) {
        String url = API_TRANSFER + "?shareid=" + shareId + "&from=" + uk
                + "&sekey=" + URLEncoder.encode(sekey == null ? "" : sekey, StandardCharsets.UTF_8)
                + "&ondup=newcopy&async=1&channel=chunlei&web=1&app_id=250528"
                + "&bdstoken=" + bdstoken + "&clienttype=0";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fsIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(fsIds.get(i));
        }
        sb.append("]");
        String body = "fsidlist=" + URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8)
                + "&path=" + URLEncoder.encode(savePath, StandardCharsets.UTF_8);
        Map<String, String> headers = baseHeaders(cookie);
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        headers.put("Origin", "https://pan.baidu.com");
        headers.put("Referer", "https://pan.baidu.com/");

        HttpResponse resp = post(url, body, headers);
        if (resp == null || !resp.isOk()) return "转存请求失败";
        JSONObject root = JSONUtil.parseObj(resp.body());
        int errno = root.getInt("errno", -1);
        return errno == 0 ? null : mapErr(errno);
    }

    /** 对文件夹创建分享，返回分享链接（不含 pwd）。 */
    private String createShare(List<Long> fidList, String pwd, String cookie, String bdstoken) {
        String url = API_SHARE + "?channel=chunlei&bdstoken=" + bdstoken
                + "&clienttype=0&app_id=250528&web=1&dp-logid=" + dpLogid();
        StringBuilder fids = new StringBuilder("[");
        for (int i = 0; i < fidList.size(); i++) {
            if (i > 0) fids.append(",");
            fids.append(fidList.get(i));
        }
        fids.append("]");
        String body = "is_knowledge=0&public=0&period=0&pwd=" + pwd
                + "&eflag_disable=true&linkOrQrcode=link"
                + "&channel_list=" + URLEncoder.encode("[]", StandardCharsets.UTF_8)
                + "&schannel=4&fid_list=" + URLEncoder.encode(fids.toString(), StandardCharsets.UTF_8);
        HttpResponse resp = post(url, body, shareHeaders(cookie));
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("errno", -1) != 0) {
            log.warn("[百度] 建分享失败: {}", mapErr(root.getInt("errno", -1)));
            return null;
        }
        return root.getStr("link");
    }

    /** bdstoken 会话内稳定，按 cookie 缓存 30 分钟，省掉每次首转都抓 /disk/main 整页 HTML。 */
    private static final long BDSTOKEN_TTL_MS = 30 * 60 * 1000L;
    private final Map<String, String[]> bdstokenCache = new ConcurrentHashMap<>(); // cookie -> [token, expireAtMs]

    private String fetchBdstoken(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        String[] cached = bdstokenCache.get(cookie);
        if (cached != null && Long.parseLong(cached[1]) > now) {
            return cached[0];
        }
        Map<String, String> headers = baseHeaders(cookie);
        HttpResponse resp = get("https://pan.baidu.com/disk/main", headers);
        if (resp == null || !resp.isOk()) {
            return cached != null ? cached[0] : null; // 抖动时退回旧值，别让首转白挂
        }
        Matcher m = Pattern.compile("\"bdstoken\"\\s*:\\s*\"([a-f0-9]{32})\"").matcher(resp.body());
        if (!m.find()) {
            return cached != null ? cached[0] : null;
        }
        String token = m.group(1);
        bdstokenCache.put(cookie, new String[]{token, String.valueOf(now + BDSTOKEN_TTL_MS)});
        return token;
    }

    // ==================== helpers ====================

    private Map<String, String> baseHeaders(String cookie) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", USER_AGENT);
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        h.put("X-Requested-With", "XMLHttpRequest");
        if (cookie != null && !cookie.isBlank()) {
            h.put("Cookie", cookie);
        }
        return h;
    }

    private Map<String, String> shareHeaders(String cookie) {
        Map<String, String> h = baseHeaders(cookie);
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Content-Type", "application/x-www-form-urlencoded");
        h.put("Origin", "https://pan.baidu.com");
        h.put("Referer", "https://pan.baidu.com/disk/main");
        return h;
    }

    @Override
    public boolean supportsAlive() {
        return true;
    }

    @Override
    public boolean checkAlive(Account account) {
        if (account == null || account.getCookie() == null || account.getCookie().isBlank()) {
            return false;
        }
        String url = "https://pan.baidu.com/api/gettemplatevariable?fields=%5B%22username%22%5D";
        HttpResponse resp = get(url, baseHeaders(account.getCookie()));
        if (resp == null || !resp.isOk()) {
            return true; // 网络/接口抖动，不误杀
        }
        try {
            cn.hutool.json.JSONObject root = cn.hutool.json.JSONUtil.parseObj(resp.body());
            if (root.getInt("errno", -1) != 0) {
                return false; // 未登录/身份失效
            }
            cn.hutool.json.JSONObject result = root.getJSONObject("result");
            String username = result == null ? null : result.getStr("username");
            return username != null && !username.isBlank();
        } catch (Exception e) {
            return true;
        }
    }

    // ==================== 账号信息 ====================

    @Override
    public boolean supportsAccountInfo() {
        return true;
    }

    @Override
    public com.jyinshi.transfer.pan.driver.AccountInfo getAccountInfo(Account account) {
        if (account == null || account.getCookie() == null || account.getCookie().isBlank()) {
            return null;
        }
        String cookie = account.getCookie();
        String nickname = "";
        String uid = "";
        // 昵称 + uk：gettemplatevariable（cookie 版，和 checkAlive 同源）
        HttpResponse uResp = get(
                "https://pan.baidu.com/api/gettemplatevariable?fields=%5B%22username%22%2C%22uk%22%5D",
                baseHeaders(cookie));
        if (uResp != null && uResp.isOk()) {
            try {
                JSONObject root = JSONUtil.parseObj(uResp.body());
                if (root.getInt("errno", -1) == 0) {
                    JSONObject result = root.getJSONObject("result");
                    if (result != null) {
                        nickname = result.getStr("username", "");
                        uid = result.getStr("uk", "");
                    }
                }
            } catch (Exception e) {
                log.warn("[百度] 取用户信息解析异常: {}", e.getMessage());
            }
        }
        // 空间：/api/quota（cookie 版）
        long total = -1;
        long used = -1;
        HttpResponse qResp = get(
                "https://pan.baidu.com/api/quota?checkfree=1&checkexpire=1&clienttype=0&app_id=250528&web=1",
                baseHeaders(cookie));
        if (qResp != null && qResp.isOk()) {
            try {
                JSONObject root = JSONUtil.parseObj(qResp.body());
                if (root.getInt("errno", -1) == 0) {
                    total = root.getLong("total", -1L);
                    used = root.getLong("used", -1L);
                }
            } catch (Exception e) {
                log.warn("[百度] 取容量解析异常: {}", e.getMessage());
            }
        }
        if (nickname.isBlank() && total < 0) {
            return null; // 全没拿到，视为失败
        }
        return com.jyinshi.transfer.pan.driver.AccountInfo.of(nickname, uid, total, used);
    }

    private HttpResponse post(String url, String body, Map<String, String> headers) {
        try {
            return HttpRequest.post(url).addHeaders(headers).body(body).timeout(TIMEOUT).execute();
        } catch (Exception e) {
            log.warn("[百度] POST 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpResponse get(String url, Map<String, String> headers) {
        try {
            return HttpRequest.get(url).addHeaders(headers).timeout(TIMEOUT).execute();
        } catch (Exception e) {
            log.warn("[百度] GET 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private ShareUrlInfo parseShareUrl(String shareUrl) {
        if (shareUrl == null) return null;
        ShareUrlInfo info = new ShareUrlInfo();
        Matcher m1 = Pattern.compile("/s/1([a-zA-Z0-9_-]+)").matcher(shareUrl);
        if (m1.find()) info.surl = m1.group(1);
        Matcher m2 = Pattern.compile("[?&]surl=([a-zA-Z0-9_-]+)").matcher(shareUrl);
        if (m2.find()) info.surl = m2.group(1);
        Matcher m3 = Pattern.compile("[?&]pwd=([a-zA-Z0-9]+)").matcher(shareUrl);
        if (m3.find()) info.pwd = m3.group(1);
        return info.surl != null ? info : null;
    }

    private String packDirName(String title, JSONArray files, long shareId) {
        if (files != null && files.size() == 1) {
            JSONObject f = files.getJSONObject(0);
            if (f.getInt("isdir", 0) == 1) {
                String name = f.getStr("server_filename", "");
                if (!name.isBlank()) return sanitize(name);
            }
        }
        if (title != null && !title.isBlank()) return sanitize(title);
        return "分享_" + shareId;
    }

    private String sanitize(String name) {
        return name == null ? "未命名" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * 把 sekey 写入请求 Cookie 头的 BDCLND：先剔除整段 cookie 里可能已带的旧 BDCLND，再追加新值。
     *
     * <p>后台是「整段浏览器 cookie」粘贴的，常残留上次开分享的旧 BDCLND；若无脑追加会出现两个
     * BDCLND，百度取到旧的那个 → share/list 报 errno -9「提取码验证失败」。故这里统一去重覆盖。</p>
     */
    private void applyBdclnd(Map<String, String> headers, String sekey) {
        if (sekey == null || sekey.isBlank()) {
            return;
        }
        String enc = URLEncoder.encode(sekey, StandardCharsets.UTF_8);
        String c = headers.getOrDefault("Cookie", "");
        c = c.replaceAll("(?i)BDCLND=[^;]*;?\\s*", "").replaceAll(";\\s*$", "").trim();
        headers.put("Cookie", c.isBlank() ? "BDCLND=" + enc : c + "; BDCLND=" + enc);
    }

    private String extractFromCookie(String cookie, String key) {
        if (cookie == null) return null;
        Matcher m = Pattern.compile(key + "=([^;]+)").matcher(cookie);
        return m.find() ? m.group(1) : null;
    }

    private String decode(String s) {
        if (s == null) return null;
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private String dpLogid() {
        return System.currentTimeMillis() + String.format("%05d", new Random().nextInt(100000));
    }

    private String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** 分享失效类 errno（提取码错误 / 过期 / 取消 / 删除）：确定性失败，重试无意义。 */
    private boolean isTerminalShareErrno(int errno) {
        return errno == -3 || errno == -12 || errno == -19 || errno == -21;
    }

    private String mapErr(int errno) {
        return switch (errno) {
            case -3 -> "提取码错误";
            case -6 -> "登录状态失效，请更新Cookie";
            case -9 -> "提取码验证失败，请更新Cookie";
            case -12 -> "分享已过期";
            case -19 -> "分享已被取消";
            case -21 -> "分享已被删除";
            case 105 -> "链接已失效";
            case 115 -> "转存次数超限";
            case 132 -> "需安全验证，请登录网页版手动操作一次";
            case -62 -> "Cookie已过期";
            default -> "操作失败 (errno=" + errno + ")";
        };
    }

    private static class ShareUrlInfo {
        String surl;
        String pwd;
    }

    private record Folder(long fsId, String path) {
    }
}
