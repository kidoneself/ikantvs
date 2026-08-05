package com.jyinshi.transfer.pan.exec;

import com.jyinshi.content.service.EpisodeExtractor;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.driver.AdFilter;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.PanFile;
import com.jyinshi.transfer.pan.driver.ShareContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控转存·更新：分享 vs 固定夹按文件名 diff，只转新增，进固定夹不建新分享。
 *
 * <p>落地夹列不出（已删/无权限）时直接失败，禁止继续 save——否则夸克等会落到「来自：分享」造成重复灌盘。</p>
 */
@Slf4j
@Component
public class IncrementalSyncService {

    /** 递归深度上限（防异常分享结构打爆栈；短剧一般 1~2 层）。 */
    private static final int MAX_DEPTH = 6;

    /**
     * 把分享增量同步进固定目标夹。
     *
     * @param targetFolderId 监控转存·创建时建好的固定夹；为空则拒绝
     */
    public SyncResult sync(PanDriver driver, String shareUrl, String password,
                           Account account, String targetFolderId) {
        if (targetFolderId == null || targetFolderId.isBlank()) {
            return SyncResult.fail("缺少 targetFolderId：需先完成监控转存·创建");
        }
        // 落地夹必须可列：失效时立刻停，避免无效 to_pdir 落到网盘默认「来自：分享」
        List<PanFile> targetListing = driver.listFolder(account, targetFolderId);
        if (targetListing == null) {
            return SyncResult.fail("落地夹不可用（已删或无权），停止更新: " + targetFolderId);
        }
        ShareContext ctx = driver.openShare(shareUrl, password, account);
        if (ctx == null || !ctx.isOk()) {
            return SyncResult.fail(ctx != null ? ctx.getMessage() : "打开分享失败");
        }
        List<String> newFiles = new ArrayList<>();
        try {
            // 与创建对齐：分享根若只有一个文件夹则下钻，内容直接映射进固定夹
            String startSubId = ctx.getRootDirId();
            List<PanFile> root = driver.listShareDir(ctx, ctx.getRootDirId());
            if (root != null && root.size() == 1 && root.get(0).isDir()) {
                startSubId = root.get(0).childListingId();
            }
            recurse(driver, ctx, account, startSubId, targetFolderId, newFiles, 0);
        } catch (IllegalStateException folderGone) {
            log.warn("[监控转存·更新] {}", folderGone.getMessage());
            return SyncResult.fail(folderGone.getMessage());
        } catch (Exception e) {
            log.error("[监控转存·更新] 异常 shareUrl={}", shareUrl, e);
            return SyncResult.fail("同步异常: " + e.getMessage());
        }
        // 必须按整夹重算最新集：仅看本次 newFiles 会在「无新增」时永远卡在首转误选的文件名上
        // （如 01国语.mp4），即便夹内已有 25 集。
        String latest = latestInFolder(driver, account, targetFolderId);
        return SyncResult.ok(newFiles, latest);
    }

    private void recurse(PanDriver driver, ShareContext ctx, Account account,
                         String shareSubId, String targetFolderId,
                         List<String> newFiles, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("[监控转存·更新] 超过最大递归深度，停止下钻");
            return;
        }
        List<PanFile> shareItems = driver.listShareDir(ctx, shareSubId);
        if (shareItems == null || shareItems.isEmpty()) {
            return;
        }
        List<PanFile> listed = driver.listFolder(account, targetFolderId);
        if (listed == null) {
            throw new IllegalStateException("落地夹不可用（已删或无权），停止更新: " + targetFolderId);
        }
        Map<String, PanFile> existing = new HashMap<>();
        for (PanFile f : listed) {
            existing.put(f.getName(), f);
        }

        List<PanFile> toSave = new ArrayList<>();
        for (PanFile f : shareItems) {
            if (AdFilter.isAd(f.getName(), f.getSize(), f.isDir())) {
                log.info("[监控转存·更新] 跳过广告: {}", f.getName());
                continue;
            }
            if (f.isDir()) {
                PanFile ex = existing.get(f.getName());
                String childTarget = (ex != null && ex.isDir())
                        ? ex.getId()
                        : driver.ensureFolder(account, targetFolderId, f.getName());
                if (childTarget == null) {
                    log.warn("[监控转存·更新] 目标子目录创建失败，跳过: {}", f.getName());
                    continue;
                }
                recurse(driver, ctx, account, f.childListingId(), childTarget, newFiles, depth + 1);
            } else if (!existing.containsKey(f.getName())) {
                toSave.add(f);
            }
        }

        if (!toSave.isEmpty()) {
            int n = driver.saveFiles(ctx, toSave, account, targetFolderId);
            log.info("[监控转存·更新] 目标夹 {} 新增转存 {}/{} 个文件", targetFolderId, n, toSave.size());
            // 转完再列一次：一个都对不上说明落到别处了（如夸克「来自：分享」），当失败处理
            if (n > 0) {
                List<PanFile> after = driver.listFolder(account, targetFolderId);
                if (after == null) {
                    throw new IllegalStateException("落地夹不可用（已删或无权），停止更新: " + targetFolderId);
                }
                Map<String, PanFile> afterMap = new HashMap<>();
                for (PanFile f : after) {
                    afterMap.put(f.getName(), f);
                }
                boolean anyLanded = false;
                for (PanFile f : toSave) {
                    if (afterMap.containsKey(f.getName())) {
                        anyLanded = true;
                        break;
                    }
                }
                if (!anyLanded) {
                    throw new IllegalStateException(
                            "落地夹不可用（转存未落入固定夹，可能落到默认目录），停止更新: " + targetFolderId);
                }
            }
            for (PanFile f : toSave) {
                newFiles.add(f.getName());
            }
        }
    }

    /**
     * 列出本账号某夹（递归）下的文件，挑出"最新"文件名（创建后回填集数用）。
     */
    public String latestInFolder(PanDriver driver, Account account, String folderId) {
        if (driver == null || account == null || folderId == null || folderId.isBlank()
                || !driver.supportsSync()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        try {
            collectFiles(driver, account, folderId, names, 0);
        } catch (Exception e) {
            log.warn("[监控转存·创建] 列夹取最新失败 folderId={}: {}", folderId, e.getMessage());
            return null;
        }
        return pickLatest(names);
    }

    private void collectFiles(PanDriver driver, Account account, String folderId,
                              List<String> names, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        List<PanFile> items = driver.listFolder(account, folderId);
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PanFile f : items) {
            if (AdFilter.isAd(f.getName(), f.getSize(), f.isDir())) {
                continue;
            }
            if (f.isDir()) {
                collectFiles(driver, account, f.getId(), names, depth + 1);
            } else {
                names.add(f.getName());
            }
        }
    }

    /**
     * 从新增/夹内文件里挑「最新」文件名。
     * 必须用老站同款集数规则（开头数字 / SxxExx），禁止取名字里第一段数字
     * ——否则 {@code S01E02} 会把季数 01 当成集数，永远输给 {@code S01E01}。
     */
    private String pickLatest(List<String> names) {
        return EpisodeExtractor.pickLatestFileName(names);
    }
}
