package com.jyinshi.transfer.pan.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 首转落地后的「深层广告清理」（各盘驱动共用，避免规则漂移）。
 *
 * <p>为什么需要：各盘首转都是整包/按 fid 递归复制，首转前的 {@link AdFilter} 过滤只能覆盖分享
 * <b>顶层</b>；藏在子文件夹里的广告（如 {@code GuanYing/用网盘扫↓裙下方群文件里.jpg}）会随子夹整包进来。
 * 本工具在落地后递归遍历目标目录，删掉命中 {@link AdFilter} 的文件、以及被清空的广告空壳夹。</p>
 *
 * <p>保守原则：某目录列举为空（真空或接口抖动失败）时一律当作「有内容」保留，绝不因列举失败误删正片。</p>
 */
public final class AdCleanup {

    private static final Logger log = LoggerFactory.getLogger(AdCleanup.class);
    private static final int MAX_DEPTH = 8;

    private AdCleanup() {
    }

    /** 列目录：给定目录 id，返回其直接子项。 */
    public interface Lister {
        List<PanFile> list(String folderId);
    }

    /** 删除：给定 id 列表，返回实际删除数。 */
    public interface Deleter {
        int delete(List<String> ids);
    }

    /**
     * 从 {@code rootId} 起递归清理广告。自吞异常，绝不影响主转存流程。
     *
     * @param label 日志前缀（如「夸克」）
     */
    public static void run(String label, String rootId, Lister lister, Deleter deleter) {
        try {
            int[] removed = {0};
            clean(rootId, 0, lister, deleter, removed);
            if (removed[0] > 0) {
                log.info("[{}] 首转深层清理广告 {} 个", label, removed[0]);
            }
        } catch (Exception e) {
            log.warn("[{}] 深层清理广告异常（忽略）: {}", label, e.getMessage());
        }
    }

    /** 清理单目录并递归子目录；返回清理后是否仍有内容（true=保留，false=已清空可删）。 */
    private static boolean clean(String folderId, int depth, Lister lister, Deleter deleter, int[] removed) {
        if (depth > MAX_DEPTH) {
            return true; // 过深：保守保留
        }
        List<PanFile> children = lister.list(folderId);
        if (children == null || children.isEmpty()) {
            return true; // 空或列举失败：保守保留，不让父级删它
        }
        List<String> del = new ArrayList<>();
        for (PanFile f : children) {
            if (f.isDir()) {
                if (AdFilter.isAd(f.getName(), f.getSize(), true)) {
                    del.add(f.getId());
                    continue; // 广告名文件夹整夹删，不再递归
                }
                boolean keep = clean(f.getId(), depth + 1, lister, deleter, removed);
                if (!keep) {
                    del.add(f.getId()); // 子夹里全是广告、已被清空 → 连空壳一起删
                }
            } else if (AdFilter.isAd(f.getName(), f.getSize(), false)) {
                del.add(f.getId());
            }
        }
        if (!del.isEmpty()) {
            removed[0] += deleter.delete(del);
        }
        return del.size() < children.size();
    }
}
