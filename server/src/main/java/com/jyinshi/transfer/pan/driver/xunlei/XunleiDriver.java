package com.jyinshi.transfer.pan.driver.xunlei;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.driver.AdCleanup;
import com.jyinshi.transfer.pan.driver.AdFilter;
import com.jyinshi.transfer.pan.driver.CredentialSink;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.PanFile;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.SaveResult;
import com.jyinshi.transfer.pan.driver.ShareContext;
import com.jyinshi.transfer.pan.driver.ShareInfo;
import com.jyinshi.transfer.pan.exec.StepTimer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 迅雷网盘驱动（开放平台 OpenAPI）。
 *
 * <p>鉴权走官方 openapi：{@code refresh_token}（每账号）+ 应用级 {@code client_id/secret/device_id}
 * （{@code worker.xunlei.*}）。请求带 {@code Authorization: Bearer} + {@code x-client-id} +
 * {@code x-device-id}，无需逆向的 captcha_token。</p>
 *
 * <p>refresh_token 每次刷新会滚动，driver 内存缓存；滚动后经 {@link CredentialSink} 回写主站
 * （方案A：凭据集中存主站，重启从主站拉取，不落 worker 磁盘）。</p>
 *
 * <p>转存：share 详情取 pass_code_token + file_ids → 建目标夹 → restore 到该夹 → 对夹建永久分享。</p>
 */
@Slf4j
@Component
public class XunleiDriver implements PanDriver {

    private static final String TOKEN_URL = "https://xluser-ssl.xunlei.com/v1/auth/token";
    private static final String PAN = "https://api-pan.xunlei.com";
    private static final String API_SHARE = PAN + "/drive/v1/share";
    private static final String API_SHARE_RESTORE = PAN + "/drive/v1/share/restore";
    private static final String API_FILES = PAN + "/drive/v1/files";
    private static final String API_TASKS = PAN + "/drive/v1/tasks";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 30_000;

    private final SysConfigService config;
    private final CredentialSink credentialSink;

    /** 每账号 token 内存态（accountName → state）。 */
    private final Map<String, TokenState> tokens = new ConcurrentHashMap<>();

    public XunleiDriver(SysConfigService config, CredentialSink credentialSink) {
        this.config = config;
        this.credentialSink = credentialSink;
    }

    private String clientId() {
        return config.getOrDefault(SysConfigService.TRANSFER_XUNLEI_CLIENT_ID, "");
    }

    private String deviceId() {
        return config.getOrDefault(SysConfigService.TRANSFER_XUNLEI_DEVICE_ID, "jyinshi");
    }

    @Override
    public PanType type() {
        return PanType.XUNLEI;
    }

    // ==================== 追更 / 检测 ====================

    @Override
    public ShareInfo getShareInfo(String shareUrl, String password, Account account) {
        ShareUrlInfo u = parseShareUrl(shareUrl);
        if (u == null) {
            return ShareInfo.bad("无效的迅雷链接");
        }
        try {
            String token = accessToken(account);
            String pwd = firstNonBlank(password, u.password, "");

            JSONObject detail = getShareDetail(u.shareId, pwd, token);
            if (detail == null) {
                return ShareInfo.uncertain("获取分享详情失败");
            }
            String status = detail.getStr("share_status", "");
            if (!status.isBlank() && !"OK".equalsIgnoreCase(status)) {
                return ShareInfo.bad(detail.getStr("share_status_text", "分享已失效"));
            }

            String passCodeToken = detail.getStr("pass_code_token", "");
            JSONArray files = filesOf(detail);

            // 单文件夹分享：展开一层，让追更信号（数量/时间）真实
            if (files != null && files.size() == 1
                    && "drive#folder".equals(files.getJSONObject(0).getStr("kind"))) {
                JSONArray sub = getSubFolderFiles(u.shareId, passCodeToken,
                        files.getJSONObject(0).getStr("id"), token);
                if (sub != null && !sub.isEmpty()) {
                    files = sub;
                }
            }

            long maxModify = 0, totalSize = 0;
            int count = files == null ? 0 : files.size();
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    totalSize += f.getLong("size", 0L);
                    maxModify = Math.max(maxModify, parseTime(f.getStr("modify_time")));
                }
            }

            ShareInfo info = new ShareInfo();
            info.setOk(true);
            info.setCheckState("ok");
            info.setTitle(shareTitle(detail));
            info.setUpdatedAt(maxModify > 0 ? maxModify : null);
            info.setFileCount(count);
            info.setSize(totalSize);
            info.setMessage("ok");
            return info;

        } catch (Exception e) {
            log.warn("[迅雷] getShareInfo 异常: {}", e.getMessage());
            return ShareInfo.uncertain("异常: " + e.getMessage());
        }
    }

    // ==================== 转存 ====================

    @Override
    public SaveResult save(String shareUrl, String password, Account account, String toFolderId) {
        StepTimer t = StepTimer.of("xunlei/" + account.getName());
        ShareUrlInfo u = parseShareUrl(shareUrl);
        if (u == null) {
            t.logDone(false, "err=INVALID_URL");
            return SaveResult.error("INVALID_URL", "无效的迅雷链接");
        }
        try {
            String token = accessToken(account);
            t.step("token");
            String pwd = firstNonBlank(password, u.password, "");

            JSONObject detail = getShareDetail(u.shareId, pwd, token);
            if (detail == null) {
                t.logDone(false, "err=LIST_FAILED");
                return SaveResult.error("LIST_FAILED", "获取分享详情失败");
            }
            String status = detail.getStr("share_status", "");
            if (!status.isBlank() && !"OK".equalsIgnoreCase(status)) {
                t.logDone(false, "err=SHARE_INVALID");
                return SaveResult.error("SHARE_INVALID", detail.getStr("share_status_text", "分享已失效"));
            }
            String passCodeToken = detail.getStr("pass_code_token", "");
            JSONArray files = filesOf(detail);
            if (files == null || files.isEmpty()) {
                t.logDone(false, "err=NO_FILES");
                return SaveResult.error("NO_FILES", "分享为空");
            }
            t.step("detail");

            String packName = shareTitle(detail);
            List<String> fileIds = new ArrayList<>();
            if (files.size() == 1 && "drive#folder".equals(files.getJSONObject(0).getStr("kind"))) {
                JSONObject dir = files.getJSONObject(0);
                packName = dir.getStr("name", packName);
                JSONArray sub = getSubFolderFiles(u.shareId, passCodeToken, dir.getStr("id"), token);
                if (sub == null || sub.isEmpty()) {
                    t.logDone(false, "err=NO_FILES");
                    return SaveResult.error("NO_FILES", "分享文件夹为空");
                }
                collectNonAdFileIds(sub, fileIds);
            } else {
                collectNonAdFileIds(files, fileIds);
            }
            if (fileIds.isEmpty()) {
                t.logDone(false, "err=NO_FILES");
                return SaveResult.error("NO_FILES", "过滤广告后无有效文件");
            }

            String parentId = firstNonBlank(toFolderId, account.getTargetDirFid(), "");
            String folderId = mkdir(sanitize(packName), parentId, token);
            if (folderId == null) {
                t.logDone(false, "err=CREATE_FOLDER_FAILED");
                return SaveResult.error("CREATE_FOLDER_FAILED", "创建目标文件夹失败");
            }
            t.step("mkdir");

            String restoreTaskId = restore(u.shareId, passCodeToken, fileIds, folderId, token);
            if (restoreTaskId == null) {
                t.logDone(false, "err=TRANSFER_FAILED");
                return SaveResult.error("TRANSFER_FAILED", "转存请求失败");
            }
            t.step("restore");

            waitTask(restoreTaskId, token, 30);
            t.step("waitRestore");

            // 深层清理：首转过滤只覆盖顶层，嵌套子夹里的广告随文件夹递归复制进来，落地后递归删
            AdCleanup.run("迅雷", folderId,
                    fid -> listFolder(account, fid),
                    ids -> delete(account, ids));
            t.step("cleanupAds");

            String[] share = createShare(List.of(folderId), packName, token);
            if (share == null) {
                t.logDone(false, "err=SHARE_FAILED");
                return SaveResult.error("SHARE_FAILED", "转存成功但建分享失败");
            }
            t.step("share");
            t.logDone(true, null);
            return SaveResult.ok(share[0], share[1], folderId, account.getName());

        } catch (IllegalStateException e) {
            t.logDone(false, "err=AUTH_FAILED");
            return SaveResult.error("AUTH_FAILED", e.getMessage());
        } catch (Exception e) {
            log.error("[迅雷] 转存异常", e);
            t.logDone(false, "err=EXCEPTION");
            return SaveResult.error("EXCEPTION", "转存异常: " + e.getMessage());
        }
    }

    /** 收集非广告文件的 id（首转过滤广告）。 */
    private void collectNonAdFileIds(JSONArray files, List<String> out) {
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.getStr("name", "");
            boolean isDir = "drive#folder".equals(f.getStr("kind"));
            if (AdFilter.isAd(name, f.getLong("size", 0L), isDir)) {
                log.info("[迅雷] 首转过滤广告: {}", name);
                continue;
            }
            out.add(f.getStr("id"));
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
            return ShareContext.fail("无效的迅雷链接");
        }
        try {
            String token = accessToken(account);
            String pwd = firstNonBlank(password, u.password, "");
            JSONObject detail = getShareDetail(u.shareId, pwd, token);
            if (detail == null) {
                return ShareContext.fail("获取分享详情失败");
            }
            String status = detail.getStr("share_status", "");
            if (!status.isBlank() && !"OK".equalsIgnoreCase(status)) {
                return ShareContext.fail(detail.getStr("share_status_text", "分享已失效"));
            }
            ShareContext ctx = new ShareContext();
            ctx.setOk(true);
            ctx.setShareId(u.shareId);
            ctx.setToken(detail.getStr("pass_code_token", "")); // 分享令牌
            ctx.setRootDirId(""); // 分享根 parent_id 为空
            ctx.getExtra().put("accessToken", token); // sync 秒级短流程，缓存复用
            ctx.getExtra().put("pwd", pwd);
            return ctx;
        } catch (IllegalStateException e) {
            return ShareContext.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("[迅雷] openShare 异常: {}", e.getMessage());
            return ShareContext.fail("打开分享异常: " + e.getMessage());
        }
    }

    @Override
    public List<PanFile> listShareDir(ShareContext ctx, String subDirId) {
        String token = (String) ctx.getExtra().get("accessToken");
        JSONArray files;
        if (subDirId == null || subDirId.isBlank()) {
            JSONObject detail = getShareDetail(ctx.getShareId(), (String) ctx.getExtra().get("pwd"), token);
            files = detail == null ? null : filesOf(detail);
        } else {
            files = getSubFolderFiles(ctx.getShareId(), ctx.getToken(), subDirId, token);
        }
        return toPanFiles(files);
    }

    @Override
    public List<PanFile> listFolder(Account account, String folderId) {
        String token = accessToken(account);
        List<PanFile> out = new ArrayList<>();
        String pageToken = "";
        for (int page = 0; page < 50; page++) { // 上限防跑飞
            String url = API_FILES + "?parent_id=" + URLEncoder.encode(folderId == null ? "" : folderId, StandardCharsets.UTF_8)
                    + "&limit=200&page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8)
                    + "&filters=" + URLEncoder.encode("{\"trashed\":{\"eq\":false}}", StandardCharsets.UTF_8);
            HttpResponse resp = authGet(url, token);
            if (resp == null || !resp.isOk()) {
                return page == 0 ? null : out;
            }
            JSONObject root = JSONUtil.parseObj(resp.body());
            if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
                return page == 0 ? null : out;
            }
            out.addAll(toPanFiles(root.getJSONArray("files")));
            pageToken = root.getStr("next_page_token", "");
            if (pageToken == null || pageToken.isBlank()) {
                break;
            }
        }
        return out;
    }

    @Override
    public String ensureFolder(Account account, String parentFolderId, String name) {
        List<PanFile> siblings = listFolder(account, parentFolderId);
        if (siblings == null) {
            return null;
        }
        for (PanFile f : siblings) {
            if (f.isDir() && name.equals(f.getName())) {
                return f.getId();
            }
        }
        return mkdir(name, parentFolderId, accessToken(account));
    }

    /**
     * NAS / 剧级落地：在顶层 landingDir 下确保剧名夹存在，并建永久分享。
     *
     * @return [folderId, shareUrl]；任一步失败返回 null
     */
    public String[] ensureLandingShare(Account account, String parentLandingDir, String folderName) {
        if (account == null || folderName == null || folderName.isBlank()) {
            return null;
        }
        String parentId = "";
        if (parentLandingDir != null && !parentLandingDir.isBlank()) {
            parentId = ensureFolder(account, "", parentLandingDir);
            if (parentId == null || parentId.isBlank()) {
                log.warn("[迅雷] NAS 建顶层落地夹失败: {}", parentLandingDir);
                return null;
            }
        }
        String folderId = ensureFolder(account, parentId, folderName.trim());
        if (folderId == null || folderId.isBlank()) {
            log.warn("[迅雷] NAS 建剧名夹失败: {}", folderName);
            return null;
        }
        String[] share = createShare(List.of(folderId), folderName.trim(), accessToken(account));
        if (share == null || share[0] == null || share[0].isBlank()) {
            log.warn("[迅雷] NAS 建分享失败 folder={}", folderId);
            return null;
        }
        return new String[]{folderId, share[0]};
    }

    /**
     * 递归收集夹内文件相对路径键（rel_dir/name，根下仅 name），供 NAS 差集。
     *
     * @return null=夹不可列
     */
    public java.util.Set<String> collectRelativeFileKeys(Account account, String folderId) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (!collectRelativeFileKeysRecurse(account, folderId, "", keys, 0)) {
            return null;
        }
        return keys;
    }

    private boolean collectRelativeFileKeysRecurse(Account account, String folderId, String relDir,
                                                   java.util.Set<String> keys, int depth) {
        if (depth > 6) {
            return true;
        }
        List<PanFile> items = listFolder(account, folderId);
        if (items == null) {
            return false;
        }
        for (PanFile f : items) {
            if (f.isDir()) {
                String childRel = relDir.isBlank() ? f.getName() : relDir + "/" + f.getName();
                if (!collectRelativeFileKeysRecurse(account, f.getId(), childRel, keys, depth + 1)) {
                    return false;
                }
            } else {
                String key = relDir == null || relDir.isBlank() ? f.getName() : relDir + "/" + f.getName();
                keys.add(key);
            }
        }
        return true;
    }

    @Override
    public int saveFiles(ShareContext ctx, List<PanFile> files, Account account, String targetFolderId) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        String token = accessToken(account);
        List<String> fileIds = new ArrayList<>();
        for (PanFile f : files) {
            fileIds.add(f.getId());
        }
        String taskId = restore(ctx.getShareId(), ctx.getToken(), fileIds, targetFolderId, token);
        if (taskId == null) {
            return 0;
        }
        waitTask(taskId, token, 30);
        return files.size();
    }

    private List<PanFile> toPanFiles(JSONArray files) {
        List<PanFile> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        for (int i = 0; i < files.size(); i++) {
            JSONObject f = files.getJSONObject(i);
            out.add(PanFile.of(
                    f.getStr("id"),
                    f.getStr("name", ""),
                    "drive#folder".equals(f.getStr("kind")),
                    f.getLong("size", 0L),
                    null));
        }
        return out;
    }

    @Override
    public int delete(Account account, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String token = accessToken(account);
        JSONObject body = new JSONObject();
        body.set("ids", ids);
        HttpResponse resp = authPost(PAN + "/drive/v1/files:batchDelete", body.toString(), token);
        if (resp == null || !resp.isOk()) {
            log.warn("[迅雷] 删除请求失败");
            return 0;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
            log.warn("[迅雷] 删除失败: {}", root.getStr("error_description", ""));
            return 0;
        }
        return ids.size();
    }

    // ==================== 迅雷 API ====================

    private JSONObject getShareDetail(String shareId, String pwd, String token) {
        String url = API_SHARE + "?share_id=" + shareId + "&pass_code=" + (pwd == null ? "" : pwd);
        HttpResponse resp = authGet(url, token);
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
            log.warn("[迅雷] share 详情失败: {}", root.getStr("error_description", ""));
            return null;
        }
        return root;
    }

    private JSONArray getSubFolderFiles(String shareId, String passCodeToken, String parentId, String token) {
        try {
            String url = API_SHARE + "/detail?share_id=" + shareId
                    + "&parent_id=" + parentId
                    + "&pass_code_token=" + URLEncoder.encode(passCodeToken, StandardCharsets.UTF_8)
                    + "&limit=200";
            HttpResponse resp = authGet(url, token);
            if (resp == null || !resp.isOk()) return null;
            JSONObject root = JSONUtil.parseObj(resp.body());
            if (root.containsKey("error") && !root.getStr("error", "").isBlank()) return null;
            return filesOf(root);
        } catch (Exception e) {
            return null;
        }
    }

    /** 提交转存到指定文件夹，返回 restore_task_id。 */
    private String restore(String shareId, String passCodeToken, List<String> fileIds,
                           String parentId, String token) {
        JSONObject body = new JSONObject();
        body.set("share_id", shareId);
        body.set("pass_code_token", passCodeToken);
        body.set("file_ids", fileIds);
        body.set("ancestor_ids", new JSONArray());
        body.set("parent_id", parentId);
        body.set("specify_parent_id", true);
        HttpResponse resp = authPost(API_SHARE_RESTORE, body.toString(), token);
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
            log.warn("[迅雷] restore 失败: {}", root.getStr("error_description", ""));
            return null;
        }
        return root.getStr("restore_task_id", null);
    }

    private void waitTask(String taskId, String token, int timeoutSec) {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse resp = authGet(API_TASKS + "/" + taskId, token);
            if (resp != null && resp.isOk()) {
                String phase = JSONUtil.parseObj(resp.body()).getStr("phase", "");
                if ("PHASE_TYPE_COMPLETE".equals(phase) || "PHASE_TYPE_ERROR".equals(phase)) {
                    return;
                }
            }
            sleep(500);
        }
    }

    private String mkdir(String name, String parentId, String token) {
        JSONObject body = new JSONObject();
        body.set("kind", "drive#folder");
        body.set("name", name);
        body.set("parent_id", parentId == null ? "" : parentId);
        body.set("ignore_duplicated_name", true);
        HttpResponse resp = authPost(API_FILES, body.toString(), token);
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
            log.warn("[迅雷] 建文件夹失败: {}", root.getStr("error_description", ""));
            return null;
        }
        JSONObject file = root.getJSONObject("file");
        String id = file != null ? file.getStr("id", "") : "";
        return id.isBlank() ? null : id;
    }

    /** 对文件建永久分享，返回 [shareUrl(含pwd), passCode]。 */
    private String[] createShare(List<String> fileIds, String title, String token) {
        JSONObject params = new JSONObject();
        params.set("subscribe_push", "false");
        params.set("WithPassCodeInLink", "true");
        JSONObject body = new JSONObject();
        body.set("file_ids", fileIds);
        body.set("share_to", "copy");
        body.set("title", title == null ? "资源分享" : title);
        body.set("expiration_days", "-1");
        body.set("restore_limit", "-1");
        body.set("params", params);
        HttpResponse resp = authPost(API_SHARE, body.toString(), token);
        if (resp == null || !resp.isOk()) return null;
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.containsKey("error") && !root.getStr("error", "").isBlank()) {
            log.warn("[迅雷] 建分享失败: {}", root.getStr("error_description", ""));
            return null;
        }
        String shareId = "", shareUrl = "", passcode = "";
        JSONArray list = root.getJSONArray("share_list");
        if (list != null && !list.isEmpty()) {
            JSONObject first = list.getJSONObject(0);
            shareId = first.getStr("share_id", "");
            shareUrl = first.getStr("share_url", "");
            passcode = first.getStr("pass_code", "");
        }
        if (shareId.isBlank()) shareId = root.getStr("share_id", "");
        if (shareUrl.isBlank() && !shareId.isBlank()) shareUrl = "https://pan.xunlei.com/s/" + shareId;
        if (passcode.isBlank()) passcode = root.getStr("pass_code", "");
        if (shareUrl.isBlank()) return null;
        if (!passcode.isBlank() && !shareUrl.contains("?pwd=")) {
            shareUrl = shareUrl + "?pwd=" + passcode;
        }
        return new String[]{shareUrl, passcode};
    }

    // ==================== 凭据探活 ====================

    @Override
    public boolean supportsAlive() {
        return true;
    }

    @Override
    public boolean checkAlive(Account account) {
        if (account == null) {
            return false;
        }
        try {
            String token = accessToken(account);
            return token != null && !token.isBlank();
        } catch (IllegalStateException e) {
            // refresh_token 失效/被拒 → 确认失效需重新授权
            log.warn("[迅雷] 探活失败 {}: {}", account.getName(), e.getMessage());
            return false;
        } catch (Exception e) {
            return true; // 其它异常按网络抖动，不误杀
        }
    }

    // ==================== 账号信息 ====================

    private static final String XLUSER = "https://xluser-ssl.xunlei.com";

    @Override
    public boolean supportsAccountInfo() {
        return true;
    }

    @Override
    public com.jyinshi.transfer.pan.driver.AccountInfo getAccountInfo(Account account) {
        if (account == null) {
            return null;
        }
        try {
            String token = accessToken(account);
            String nickname = "";
            String uid = "";
            // 用户信息：xluser /v1/user/me（Bearer + x-client-id + x-device-id）
            HttpResponse uResp = authGet(XLUSER + "/v1/user/me", token);
            if (uResp != null && uResp.isOk()) {
                JSONObject u = JSONUtil.parseObj(uResp.body());
                nickname = firstNonBlank(u.getStr("nickname"), u.getStr("name"), "");
                uid = firstNonBlank(u.getStr("sub"), u.getStr("userid"), "");
            }
            // 空间：pan /drive/v1/about → quota.limit / quota.usage（字符串数字）
            long total = -1;
            long used = -1;
            HttpResponse aResp = authGet(PAN + "/drive/v1/about", token);
            if (aResp != null && aResp.isOk()) {
                JSONObject quota = JSONUtil.parseObj(aResp.body()).getJSONObject("quota");
                if (quota != null) {
                    total = parseLong(quota.getStr("limit"));
                    used = parseLong(quota.getStr("usage"));
                }
            }
            if (nickname.isBlank() && total < 0) {
                return null;
            }
            return com.jyinshi.transfer.pan.driver.AccountInfo.of(nickname, uid, total, used);
        } catch (IllegalStateException e) {
            log.warn("[迅雷] getAccountInfo 授权失败 {}: {}", account.getName(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[迅雷] getAccountInfo 异常: {}", e.getMessage());
            return null;
        }
    }

    private long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ==================== token ====================

    /** 换号/重新授权后清掉旧 token 内存缓存，让新 refresh_token 立即生效。 */
    @Override
    public void onCredentialUpdated(Account account) {
        if (account == null || account.getName() == null) {
            return;
        }
        tokens.remove(account.getName());
        log.info("[迅雷] 账号 {} 凭据已更新，清空旧 token 缓存", account.getName());
    }

    /** 取可用 access_token；过期则用 refresh_token 续期，滚动后回写主站（方案A，不落盘）。 */
    private String accessToken(Account account) {
        if (clientId().isBlank()) {
            throw new IllegalStateException("未配置迅雷 client_id（后台系统配置→迅雷转存）");
        }
        String name = account.getName();
        TokenState st = tokens.computeIfAbsent(name, k -> seedToken(account.getRefreshToken()));
        synchronized (st) {
            if (!st.accessToken.isBlank() && Instant.now().getEpochSecond() < st.expireAtSec) {
                return st.accessToken;
            }
            if (st.refreshToken == null || st.refreshToken.isBlank()) {
                throw new IllegalStateException("迅雷账号无 refresh_token: " + name);
            }
            JSONObject body = new JSONObject();
            body.set("client_id", clientId());
            // 刷新只带 client_id + refresh_token；开放平台明确禁止刷新时带 client_secret
            // （带了会 403 permission_denied: "Do Not Save Your client_secret in browser"）
            body.set("grant_type", "refresh_token");
            body.set("refresh_token", st.refreshToken);
            HttpResponse resp = post(TOKEN_URL, body.toString(), false, null);
            if (resp == null || !resp.isOk()) {
                String detail = resp == null ? "无响应" : (resp.getStatus() + " " + resp.body());
                throw new IllegalStateException("迅雷刷新 token 请求失败（需重新授权）: " + detail);
            }
            JSONObject r = JSONUtil.parseObj(resp.body());
            String at = r.getStr("access_token", "");
            if (at.isBlank()) {
                throw new IllegalStateException("迅雷刷新 token 失败: " + r.getStr("error_description", r.toString()));
            }
            st.accessToken = at;
            String rolled = r.getStr("refresh_token", st.refreshToken);
            boolean changed = rolled != null && !rolled.isBlank() && !rolled.equals(st.refreshToken);
            st.refreshToken = rolled;
            st.expireAtSec = Instant.now().getEpochSecond() + r.getLong("expires_in", 7200L) - 120;
            if (changed) {
                // 滚动了新 refresh_token：同步内存账号 + 回写主站，重启后仍可用
                account.setRefreshToken(rolled);
                if (credentialSink != null) {
                    credentialSink.onRefreshTokenRolled(PanType.XUNLEI, name, rolled);
                }
            }
            return st.accessToken;
        }
    }

    private TokenState seedToken(String seedRefreshToken) {
        TokenState st = new TokenState();
        st.refreshToken = seedRefreshToken;
        return st;
    }

    private static class TokenState {
        String accessToken = "";
        String refreshToken = "";
        long expireAtSec = 0L;
    }

    // ==================== HTTP ====================

    private HttpResponse authGet(String url, String token) {
        try {
            return HttpRequest.get(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("x-client-id", clientId())
                    .header("x-device-id", deviceId())
                    .timeout(TIMEOUT)
                    .execute();
        } catch (Exception e) {
            log.warn("[迅雷] GET 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpResponse authPost(String url, String body, String token) {
        return post(url, body, true, token);
    }

    private HttpResponse post(String url, String body, boolean auth, String token) {
        try {
            HttpRequest req = HttpRequest.post(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId())
                    .body(body)
                    .timeout(TIMEOUT);
            if (auth) {
                req.header("Authorization", "Bearer " + token)
                        .header("x-device-id", deviceId());
            }
            return req.execute();
        } catch (Exception e) {
            log.warn("[迅雷] POST 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ==================== helpers ====================

    private JSONArray filesOf(JSONObject root) {
        JSONArray files = root.getJSONArray("file_infos");
        if (files == null || files.isEmpty()) {
            files = root.getJSONArray("files");
        }
        return files;
    }

    private String shareTitle(JSONObject detail) {
        JSONObject share = detail.getJSONObject("share");
        String t = share != null ? share.getStr("title", "") : "";
        if (t.isBlank()) t = detail.getStr("title", "");
        return t.isBlank() ? "资源分享" : t;
    }

    private ShareUrlInfo parseShareUrl(String shareUrl) {
        if (shareUrl == null) return null;
        ShareUrlInfo info = new ShareUrlInfo();
        Matcher m1 = Pattern.compile("/s/([a-zA-Z0-9_-]+)").matcher(shareUrl);
        if (m1.find()) info.shareId = m1.group(1);
        Matcher m2 = Pattern.compile("[?&]pwd=([a-zA-Z0-9]+)").matcher(shareUrl);
        if (m2.find()) info.password = m2.group(1);
        return info.shareId != null ? info : null;
    }

    /** RFC3339 时间字符串 → epoch 毫秒；失败返回 0。 */
    private long parseTime(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private String sanitize(String name) {
        return name == null ? "资源分享" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ShareUrlInfo {
        String shareId;
        String password;
    }
}
