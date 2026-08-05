package com.jyinshi.transfer.pan.driver.quark;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
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
import cn.hutool.json.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 夸克网盘驱动（从老项目 QuarkPanClient 移植 + 瘦身）。
 *
 * <p>转存流程：提取分享ID → 取 stoken → 取分享详情 → 转存(save) → 轮询 task → 拿新 fid
 * → 建分享(share) → 轮询 task → 取分享链(password)。</p>
 *
 * <p>{@link #getShareInfo} 免登录（追更/检测用）；{@link #save} 用账号 cookie。</p>
 */
@Slf4j
@Component
public class QuarkDriver implements PanDriver {

    private static final String BASE_H = "https://drive-h.quark.cn/1/clouddrive";   // 分享读取
    private static final String BASE_PC = "https://drive-pc.quark.cn/1/clouddrive";  // 分享操作
    private static final String REFERER = "https://pan.quark.cn/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";

    private static final int TIMEOUT = 10_000;

    @Override
    public PanType type() {
        return PanType.QUARK;
    }

    // ==================== 追更 / 检测 ====================

    @Override
    public ShareInfo getShareInfo(String shareUrl, String password, Account account) {
        String shareId = extractShareId(shareUrl);
        if (shareId == null) {
            return ShareInfo.bad("无效的夸克链接");
        }
        try {
            StokenResult sr = fetchStoken(shareId, password, null);
            if (!sr.ok()) {
                // 终态码才判失效；网络/限流等瞬时问题回 uncertain，避免误杀
                return sr.terminal()
                        ? ShareInfo.bad("分享已失效：" + sr.message())
                        : ShareInfo.uncertain("获取 stoken 失败，请稍后重试");
            }
            String stoken = sr.stoken();

            String url = BASE_H + "/share/sharepage/detail?pr=ucpro&fr=pc&uc_param_str="
                    + "&pwd_id=" + shareId
                    + "&stoken=" + enc(stoken)
                    + "&pdir_fid=0&_fetch_share=1&_fetch_total=1"
                    + "&__dt=" + rnd() + "&__t=" + now();

            HttpResponse resp = HttpRequest.get(url)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .timeout(TIMEOUT)
                    .execute();

            if (!resp.isOk()) {
                return ShareInfo.uncertain("详情请求 HTTP " + resp.getStatus());
            }
            JSONObject root = JSONUtil.parseObj(resp.body());
            if (root.getInt("code", -1) != 0) {
                return ShareInfo.bad(root.getStr("message", "获取详情失败"));
            }

            JSONObject share = root.getJSONObject("data").getJSONObject("share");
            ShareInfo info = new ShareInfo();
            info.setOk(true);
            info.setCheckState("ok");
            info.setTitle(share.getStr("title", ""));
            info.setUpdatedAt(share.getLong("updated_at", 0L));
            info.setFileCount(share.getInt("all_file_num", 0));
            info.setSize(share.getLong("size", 0L));
            info.setExpiredAt(share.getLong("expired_at", 0L));
            info.setMessage("ok");
            return info;

        } catch (Exception e) {
            log.warn("[夸克] getShareInfo 异常: {}", e.getMessage());
            return ShareInfo.uncertain("异常: " + e.getMessage());
        }
    }

    // ==================== 转存 ====================

    @Override
    public SaveResult save(String shareUrl, String password, Account account, String toFolderId) {
        StepTimer t = StepTimer.of("quark/" + account.getName());
        String shareId = extractShareId(shareUrl);
        if (shareId == null) {
            t.logDone(false, "err=INVALID_URL");
            return SaveResult.error("INVALID_URL", "无效的夸克链接");
        }
        String cookie = account.getCookie();
        if (cookie == null || cookie.isBlank()) {
            t.logDone(false, "err=NO_COOKIE");
            return SaveResult.error("NO_COOKIE", "账号无 cookie: " + account.getName());
        }
        try {
            StokenResult sr = fetchStoken(shareId, password, cookie);
            if (!sr.ok()) {
                String code = sr.terminal() ? "SHARE_INVALID" : "TOKEN_FAILED";
                t.logDone(false, "err=" + code);
                return SaveResult.error(code, sr.terminal()
                        ? "分享已失效：" + sr.message()
                        : "获取 stoken 失败，请稍后重试");
            }
            String stoken = sr.stoken();
            t.step("stoken");

            JSONObject detail = getDetail(shareId, stoken, cookie);
            if (detail == null) {
                t.logDone(false, "err=DETAIL_FAILED");
                return SaveResult.error("DETAIL_FAILED", "获取分享详情失败");
            }
            String title = detail.getJSONObject("share") != null
                    ? detail.getJSONObject("share").getStr("title", "转存-" + shareId)
                    : "转存-" + shareId;

            String pdirFid = "0";
            String packName = title;
            JSONArray targetList = detail.getJSONArray("list");
            if (targetList != null && targetList.size() == 1) {
                JSONObject only = targetList.getJSONObject(0);
                if (only.getBool("dir", false) && only.getInt("include_items", 0) > 0) {
                    pdirFid = only.getStr("fid");
                    packName = only.getStr("file_name", title);
                    JSONObject sub = getDetail(shareId, stoken, pdirFid, cookie);
                    targetList = sub != null ? sub.getJSONArray("list") : null;
                }
            }
            t.step("detail");

            List<String> excludeFids = new ArrayList<>();
            if (targetList != null) {
                for (int i = 0; i < targetList.size(); i++) {
                    JSONObject f = targetList.getJSONObject(i);
                    if (AdFilter.isAd(f.getStr("file_name"), f.getLong("size", 0L), f.getBool("dir", false))) {
                        excludeFids.add(f.getStr("fid"));
                        log.info("[夸克] 首转过滤广告: {}", f.getStr("file_name"));
                    }
                }
            }

            String toPdir = (toFolderId != null && !toFolderId.isBlank())
                    ? toFolderId
                    : (account.getTargetDirFid() != null && !account.getTargetDirFid().isBlank()
                        ? account.getTargetDirFid() : "0");

            String taskId = submitSave(shareId, stoken, pdirFid, toPdir, packName, excludeFids, cookie);
            if (taskId == null) {
                t.logDone(false, "err=SAVE_FAILED");
                return SaveResult.error("SAVE_FAILED", "转存任务创建失败");
            }
            t.step("submitSave");

            String savedFid;
            try {
                savedFid = waitSaveTask(taskId, cookie);
            } catch (IllegalStateException fail) {
                String msg = fail.getMessage() != null ? fail.getMessage() : "转存任务失败";
                String errCode = msg.toLowerCase().contains("capacity") ? "CAPACITY_FULL" : "TASK_FAILED";
                t.logDone(false, "err=" + errCode);
                return SaveResult.error(errCode, msg);
            }
            if (savedFid == null) {
                t.logDone(false, "err=TASK_FAILED");
                return SaveResult.error("TASK_FAILED", "转存任务未完成或无落地文件");
            }
            t.step("waitSave");

            // 深层清理：exclude_fids 只挡住顶层广告，嵌套子夹里的广告（如 GuanYing/xxx.jpg）漏网，
            // 落地后递归删。列举失败时保守不删，绝不误伤正片。
            AdCleanup.run("夸克", savedFid,
                    fid -> listFolder(account, fid),
                    ids -> delete(account, ids));
            t.step("cleanupAds");

            String shareTaskId = submitShare(savedFid, packName, cookie);
            if (shareTaskId == null) {
                t.logDone(true, "share=skipped");
                return SaveResult.ok(null, null, savedFid, account.getName());
            }
            t.step("submitShare");

            String newShareId = waitShareTask(shareTaskId, cookie);
            if (newShareId == null) {
                t.logDone(true, "share=wait_failed");
                return SaveResult.ok(null, null, savedFid, account.getName());
            }
            t.step("waitShare");

            String myShareUrl = getShareUrl(newShareId, cookie);
            t.step("getShareUrl");
            t.logDone(myShareUrl != null, null);
            return SaveResult.ok(myShareUrl, null, savedFid, account.getName());

        } catch (Exception e) {
            log.error("[夸克] 转存异常", e);
            t.logDone(false, "err=EXCEPTION");
            return SaveResult.error("EXCEPTION", "转存异常: " + e.getMessage());
        }
    }

    // ==================== 增量同步原语（追更） ====================

    @Override
    public boolean supportsSync() {
        return true;
    }

    @Override
    public ShareContext openShare(String shareUrl, String password, Account account) {
        String shareId = extractShareId(shareUrl);
        if (shareId == null) {
            return ShareContext.fail("无效的夸克链接");
        }
        String cookie = account != null ? account.getCookie() : null;
        StokenResult sr = fetchStoken(shareId, password, cookie);
        if (!sr.ok()) {
            return ShareContext.fail(sr.terminal()
                    ? "分享已失效：" + sr.message()
                    : "获取 stoken 失败，请稍后重试");
        }
        ShareContext ctx = new ShareContext();
        ctx.setOk(true);
        ctx.setShareId(shareId);
        ctx.setToken(sr.stoken());
        ctx.setRootDirId("0");
        return ctx;
    }

    @Override
    public List<PanFile> listShareDir(ShareContext ctx, String subDirId) {
        List<PanFile> out = new ArrayList<>();
        String pdir = (subDirId == null || subDirId.isBlank()) ? "0" : subDirId;
        for (int page = 1; page <= 40; page++) {
            String url = BASE_H + "/share/sharepage/detail?pr=ucpro&fr=pc&uc_param_str="
                    + "&pwd_id=" + ctx.getShareId()
                    + "&stoken=" + enc(ctx.getToken())
                    + "&pdir_fid=" + pdir
                    + "&force=0&_page=" + page + "&_size=50"
                    + "&_fetch_banner=0&_fetch_share=0&_fetch_total=1"
                    + "&_sort=file_type:asc,file_name:asc"
                    + "&__dt=" + rnd() + "&__t=" + now();
            HttpResponse resp = get(url, null);
            if (resp == null || !resp.isOk()) {
                break;
            }
            JSONObject root = JSONUtil.parseObj(resp.body());
            if (root.getInt("code", -1) != 0) {
                break;
            }
            JSONArray list = root.getJSONObject("data").getJSONArray("list");
            if (list == null || list.isEmpty()) {
                break;
            }
            for (Object o : list) {
                JSONObject f = (JSONObject) o;
                out.add(PanFile.of(
                        f.getStr("fid"),
                        f.getStr("file_name"),
                        f.getBool("dir", false),
                        f.getLong("size", 0L),
                        f.getStr("share_fid_token")));
            }
            if (list.size() < 50) {
                break;
            }
        }
        return out;
    }

    @Override
    public List<PanFile> listFolder(Account account, String folderId) {
        List<PanFile> out = new ArrayList<>();
        String pdir = (folderId == null || folderId.isBlank()) ? "0" : folderId;
        String cookie = account.getCookie();
        for (int page = 1; page <= 40; page++) {
            String url = BASE_PC + "/file/sort?pr=ucpro&fr=pc&uc_param_str="
                    + "&pdir_fid=" + pdir
                    + "&_page=" + page + "&_size=50&_fetch_total=1&_fetch_sub_dirs=0"
                    + "&_sort=file_type:asc,file_name:asc"
                    + "&__dt=" + rnd() + "&__t=" + now();
            HttpResponse resp = get(url, cookie);
            if (resp == null || !resp.isOk()) {
                // 首页失败=夹不可用；后续页失败则返回已拿到的部分
                return page == 1 ? null : out;
            }
            JSONObject root = JSONUtil.parseObj(resp.body());
            if (root.getInt("code", -1) != 0) {
                return page == 1 ? null : out;
            }
            JSONArray list = root.getJSONObject("data").getJSONArray("list");
            if (list == null || list.isEmpty()) {
                break;
            }
            for (Object o : list) {
                JSONObject f = (JSONObject) o;
                out.add(PanFile.of(
                        f.getStr("fid"),
                        f.getStr("file_name"),
                        f.getBool("dir", false),
                        f.getLong("size", 0L),
                        null));
            }
            if (list.size() < 50) {
                break;
            }
        }
        return out;
    }

    @Override
    public String ensureFolder(Account account, String parentFolderId, String name) {
        String parent = (parentFolderId == null || parentFolderId.isBlank()) ? "0" : parentFolderId;
        String cookie = account.getCookie();
        List<PanFile> siblings = listFolder(account, parent);
        if (siblings == null) {
            return null;
        }
        for (PanFile f : siblings) {
            if (f.isDir() && name.equals(f.getName())) {
                return f.getId();
            }
        }
        // 不存在则创建
        String url = BASE_PC + "/file?pr=ucpro&fr=pc&uc_param_str=";
        Map<String, Object> body = new HashMap<>();
        body.put("pdir_fid", parent);
        body.put("file_name", name);
        body.put("dir_path", "");
        body.put("dir_init_lock", false);
        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            log.warn("[夸克] 创建目录失败 {}: {}", name, root.getStr("message"));
            return null;
        }
        return root.getJSONObject("data").getStr("fid");
    }

    @Override
    public int saveFiles(ShareContext ctx, List<PanFile> files, Account account, String targetFolderId) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        String cookie = account.getCookie();
        String toPdir = (targetFolderId == null || targetFolderId.isBlank()) ? "0" : targetFolderId;
        List<String> fidList = new ArrayList<>();
        List<String> fidTokenList = new ArrayList<>();
        for (PanFile f : files) {
            fidList.add(f.getId());
            fidTokenList.add(f.getToken());
        }
        String url = BASE_PC + "/share/sharepage/save?pr=ucpro&fr=pc&uc_param_str="
                + "&__dt=" + rnd() + "&__t=" + now();
        Map<String, Object> body = new HashMap<>();
        body.put("pwd_id", ctx.getShareId());
        body.put("stoken", ctx.getToken());
        body.put("fid_list", fidList);
        body.put("fid_token_list", fidTokenList);
        body.put("to_pdir_fid", toPdir);
        body.put("pdir_save_all", false);
        body.put("scene", "link");
        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            return 0;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            log.warn("[夸克] 增量转存失败: {}", root.getStr("message"));
            return 0;
        }
        String taskId = root.getJSONObject("data").getStr("task_id");
        try {
            String savedFid = waitSaveTask(taskId, cookie);
            return savedFid != null ? files.size() : 0;
        } catch (IllegalStateException fail) {
            log.warn("[夸克] 增量转存任务失败: {}", fail.getMessage());
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    @Override
    public int delete(Account account, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String cookie = account.getCookie();
        String url = BASE_PC + "/file/delete?pr=ucpro&fr=pc&uc_param_str="
                + "&__dt=" + rnd() + "&__t=" + now();
        Map<String, Object> body = new HashMap<>();
        body.put("action_type", 2);
        body.put("filelist", ids);
        body.put("exclude_fids", new ArrayList<>());
        HttpResponse resp = post(url, body, cookie);
        if (resp == null) {
            log.warn("[夸克] 删除失败: 无响应 ids={}", ids);
            return 0;
        }
        if (!resp.isOk()) {
            log.warn("[夸克] 删除失败: HTTP {} body={}", resp.getStatus(), resp.body());
            return 0;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            log.warn("[夸克] 删除失败: code={} msg={}", root.getInt("code", -1), root.getStr("message"));
            return 0;
        }
        return ids.size();
    }

    // ==================== 内部：夸克 API ====================

    /**
     * 取 stoken 的结果。夸克的 token 接口本身就是分享活死判定：
     *   stoken != null           → 分享有效
     *   stoken == null && terminal → 分享已过期/取消/删除（真失效，可据此隐藏链接）
     *   stoken == null && !terminal → 网络抖动/限流/未知（瞬时，不得误杀链接）
     */
    private record StokenResult(String stoken, boolean terminal, String message) {
        boolean ok() {
            return stoken != null && !stoken.isEmpty();
        }
    }

    private StokenResult fetchStoken(String shareId, String password, String cookie) {
        String url = BASE_H + "/share/sharepage/token?pr=ucpro&fr=pc&uc_param_str="
                + "&__dt=" + rnd() + "&__t=" + now();
        Map<String, Object> body = new HashMap<>();
        body.put("pwd_id", shareId);
        body.put("passcode", password != null ? password : "");
        body.put("support_visit_limit_private_share", true);

        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            // HTTP 层失败：网络/限流/风控，不能据此判分享失效
            return new StokenResult(null, false, "取 stoken 请求失败 HTTP " + (resp == null ? "null" : resp.getStatus()));
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        int code = root.getInt("code", -1);
        if (code != 0) {
            String msg = root.getStr("message", "");
            boolean terminal = isTerminalShareError(code, msg);
            log.warn("[夸克] 取 stoken 失败 code={}, msg={}, terminal={}", code, msg, terminal);
            return new StokenResult(null, terminal, msg);
        }
        String stoken = root.getJSONObject("data").getStr("stoken");
        return new StokenResult(stoken, false, "ok");
    }

    /** 分享确定失效（过期/取消/删除/违规）→ 可据此隐藏链接；其余(含需提取码/网络)视为不确定。 */
    private boolean isTerminalShareError(int code, String message) {
        // 已知过期码 41019；其余按官方返回的中文文案兜底判定
        if (code == 41019) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("过期") || message.contains("取消") || message.contains("删除")
                || message.contains("失效") || message.contains("不存在") || message.contains("违规")
                || message.contains("封");
    }

    private JSONObject getDetail(String shareId, String stoken, String cookie) {
        return getDetail(shareId, stoken, "0", cookie);
    }

    private JSONObject getDetail(String shareId, String stoken, String pdirFid, String cookie) {
        String url = BASE_H + "/share/sharepage/detail?pr=ucpro&fr=pc&uc_param_str="
                + "&ver=2&pwd_id=" + shareId
                + "&stoken=" + enc(stoken)
                + "&pdir_fid=" + (pdirFid == null || pdirFid.isBlank() ? "0" : pdirFid)
                + "&force=0&_page=1&_size=200"
                + "&_fetch_banner=1&_fetch_share=1&fetch_relate_conversation=1&_fetch_total=1"
                + "&_sort=file_type:asc,file_name:asc"
                + "&__dt=" + rnd() + "&__t=" + now();
        HttpResponse resp = get(url, cookie);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            return null;
        }
        return root.getJSONObject("data");
    }

    /** 转存：pdir_save_all + pdir_fid 表示把该目录整包存到目标目录下的 packDirName 文件夹，exclude_fids 排广告。 */
    private String submitSave(String shareId, String stoken, String pdirFid,
                              String toPdirFid, String packDirName, List<String> excludeFids, String cookie) {
        String url = BASE_PC + "/share/sharepage/save?pr=ucpro&fr=pc&uc_param_str="
                + "&__dt=" + rnd() + "&__t=" + now();
        Map<String, Object> body = new HashMap<>();
        body.put("pwd_id", shareId);
        body.put("stoken", stoken);
        body.put("pdir_fid", pdirFid);
        body.put("to_pdir_fid", toPdirFid);
        body.put("pack_dir_name", packDirName);
        body.put("pdir_save_all", true);
        body.put("scene", "link");
        if (excludeFids != null && !excludeFids.isEmpty()) {
            body.put("exclude_fids", excludeFids);
        }

        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            log.warn("[夸克] 转存失败: {}", root.getStr("message"));
            return null;
        }
        return root.getJSONObject("data").getStr("task_id");
    }

    /**
     * 轮询转存任务，成功后返回 save_as.save_as_top_fids[0]。
     * status: 0/1 进行中，2 成功，其它（如 3）失败——空间满等应立刻抛，不要空等到超时。
     */
    private String waitSaveTask(String taskId, String cookie) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String url = BASE_PC + "/task?pr=ucpro&fr=pc&uc_param_str="
                    + "&task_id=" + taskId + "&retry_index=" + i
                    + "&__dt=" + rnd() + "&__t=" + now();
            HttpResponse resp = get(url, cookie);
            if (resp != null && resp.isOk()) {
                JSONObject root = JSONUtil.parseObj(resp.body());
                int code = root.getInt("code", -1);
                JSONObject data = root.getJSONObject("data");
                if (data == null) {
                    Thread.sleep(100);
                    continue;
                }
                int status = data.getInt("status", -1);
                if (status == 2 && code == 0) {
                    JSONObject saveAs = data.getJSONObject("save_as");
                    if (saveAs != null) {
                        List<Object> fids = saveAs.getBeanList("save_as_top_fids", Object.class);
                        if (fids != null && !fids.isEmpty()) {
                            return String.valueOf(fids.get(0));
                        }
                    }
                    return null;
                }
                // 终态失败：capacity limit / 风控等（实测 code=32003 status=3）
                if (status != 0 && status != 1 && status != -1) {
                    String msg = root.getStr("message", "转存任务失败");
                    log.warn("[夸克] 转存任务失败 taskId={} code={} status={} msg={}",
                            taskId, code, status, msg);
                    throw new IllegalStateException(msg);
                }
            }
            Thread.sleep(100);
        }
        return null;
    }

    private String submitShare(String fid, String title, String cookie) {
        String url = BASE_PC + "/share?pr=ucpro&fr=pc&uc_param_str=";
        Map<String, Object> body = new HashMap<>();
        body.put("fid_list", List.of(fid));
        body.put("title", title != null ? title : "分享文件");
        body.put("url_type", 1);
        body.put("expired_type", 1); // 永久
        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            return null;
        }
        return root.getJSONObject("data").getStr("task_id");
    }

    private String waitShareTask(String taskId, String cookie) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            if (i > 0) Thread.sleep(100);
            String url = BASE_PC + "/task?pr=ucpro&fr=pc&uc_param_str="
                    + "&task_id=" + taskId + "&retry_index=" + i;
            HttpResponse resp = get(url, cookie);
            if (resp != null && resp.isOk()) {
                JSONObject data = JSONUtil.parseObj(resp.body()).getJSONObject("data");
                int status = data != null ? data.getInt("status", -1) : -1;
                if (status == 2) {
                    return data.getStr("share_id");
                }
                if (status != 0 && status != 1) {
                    return null;
                }
            }
        }
        return null;
    }

    private String getShareUrl(String shareId, String cookie) {
        String url = BASE_PC + "/share/password?pr=ucpro&fr=pc&uc_param_str=";
        Map<String, Object> body = new HashMap<>();
        body.put("share_id", shareId);
        HttpResponse resp = post(url, body, cookie);
        if (resp == null || !resp.isOk()) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp.body());
        if (root.getInt("code", -1) != 0) {
            return null;
        }
        return root.getJSONObject("data").getStr("share_url");
    }

    // ==================== HTTP helpers ====================

    @Override
    public boolean supportsAlive() {
        return true;
    }

    @Override
    public boolean checkAlive(Account account) {
        if (account == null || account.getCookie() == null || account.getCookie().isBlank()) {
            return false;
        }
        HttpResponse resp = get("https://pan.quark.cn/account/info?fr=pc&platform=pc", account.getCookie());
        if (resp == null || !resp.isOk()) {
            return true; // 网络/接口抖动，不误杀
        }
        try {
            JSONObject data = JSONUtil.parseObj(resp.body()).getJSONObject("data");
            // 登录态下 data 带 nickname/mobile；未登录时 data 为空
            return data != null && (data.getStr("nickname") != null || data.getStr("mobile") != null);
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
        long total = -1;
        long used = -1;
        // 容量：/member（返回 total_capacity/use_capacity，但无顶层 nickname/member_id）
        try {
            HttpResponse resp = get(BASE_PC + "/member?pr=ucpro&fr=pc&uc_param_str="
                    + "&fetch_subscribe=false&_ch=home&fetch_identity=false", cookie);
            if (resp != null && resp.isOk()) {
                JSONObject data = JSONUtil.parseObj(resp.body()).getJSONObject("data");
                if (data != null) {
                    total = data.getLong("total_capacity", -1L);
                    used = data.getLong("use_capacity", -1L);
                    uid = data.getStr("member_id", "");
                }
            }
        } catch (Exception e) {
            log.warn("[夸克] getAccountInfo /member 解析异常: {}", e.getMessage());
        }
        // 昵称：account/info（/member 不含昵称）
        try {
            HttpResponse resp = get("https://pan.quark.cn/account/info?fr=pc&platform=pc", cookie);
            if (resp != null && resp.isOk()) {
                JSONObject data = JSONUtil.parseObj(resp.body()).getJSONObject("data");
                if (data != null) {
                    nickname = data.getStr("nickname", "");
                }
            }
        } catch (Exception e) {
            log.warn("[夸克] getAccountInfo account/info 解析异常: {}", e.getMessage());
        }
        if (nickname.isBlank() && total < 0) {
            return null;
        }
        return com.jyinshi.transfer.pan.driver.AccountInfo.of(nickname, uid, total, used);
    }

    private HttpResponse post(String url, Object body, String cookie) {
        try {
            HttpRequest req = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .timeout(TIMEOUT)
                    .body(JSONUtil.toJsonStr(body));
            if (cookie != null && !cookie.isBlank()) {
                req.header("Cookie", cookie);
            }
            return req.execute();
        } catch (Exception e) {
            log.warn("[夸克] POST 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpResponse get(String url, String cookie) {
        try {
            HttpRequest req = HttpRequest.get(url)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .timeout(TIMEOUT);
            if (cookie != null && !cookie.isBlank()) {
                req.header("Cookie", cookie);
            }
            return req.execute();
        } catch (Exception e) {
            log.warn("[夸克] GET 异常 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String extractShareId(String shareUrl) {
        if (shareUrl != null && shareUrl.contains("/s/")) {
            return shareUrl.replaceAll(".*/s/([^?&#/]+).*", "$1");
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static int rnd() {
        return (int) (Math.random() * 10000);
    }
}
