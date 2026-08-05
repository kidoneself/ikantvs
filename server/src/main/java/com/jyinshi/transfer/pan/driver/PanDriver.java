package com.jyinshi.transfer.pan.driver;

import com.jyinshi.transfer.pan.account.Account;

import java.util.List;

/**
 * 网盘驱动统一契约。每家网盘（夸克/百度/迅雷）实现一份。
 *
 * <p>能力分两组：<br>
 * 1) 基础：{@link #getShareInfo}（检查源）、{@link #save}（监控/用户转存整包落盘 + 建分享）。<br>
 * 2) 监控更新原语：{@link #openShare}/{@link #listShareDir}/{@link #listFolder}/
 *    {@link #ensureFolder}/{@link #saveFiles}，供 IncrementalSyncService 只转新增；
 *    尚未实现的驱动保持 {@link #supportsSync()}=false。</p>
 *
 * <p>新增网盘 = 加一个实现，不动其它代码（插件式）。</p>
 */
public interface PanDriver {

    PanType type();

    /**
     * 读分享信息（追更巡检 + 死活检测共用）。
     * 夸克/百度可不传 account（免登录）；迅雷需要 account。
     */
    ShareInfo getShareInfo(String shareUrl, String password, Account account);

    /**
     * 转存分享到本机账号，返回我们自己账号下的新分享链。
     *
     * @param toFolderId 目标目录 fid（追更补转时复用已有文件夹；首次可空，用账号默认目录）
     */
    SaveResult save(String shareUrl, String password, Account account, String toFolderId);

    // ==================== 增量同步原语（追更用） ====================

    /** 是否已实现增量同步原语。默认 false。 */
    default boolean supportsSync() {
        return false;
    }

    /** 打开分享，取回全程复用的上下文（token/shareId 等）。 */
    default ShareContext openShare(String shareUrl, String password, Account account) {
        throw new UnsupportedOperationException(type() + " 未实现 openShare");
    }

    /** 列出分享内某目录的直接子项（subDirId 为分享内目录 id，根用 ctx.rootDirId）。 */
    default List<PanFile> listShareDir(ShareContext ctx, String subDirId) {
        throw new UnsupportedOperationException(type() + " 未实现 listShareDir");
    }

    /**
     * 列出本账号某目录下的直接子项（folderId 为账号内目录 id）。
     *
     * @return 子项列表（可空列表=夹存在但为空）；夹已删/无权限/接口失败时返回 {@code null}
     */
    default List<PanFile> listFolder(Account account, String folderId) {
        throw new UnsupportedOperationException(type() + " 未实现 listFolder");
    }

    /** 在本账号 parentFolderId 下确保存在名为 name 的子目录，返回其 id（存在则复用）。 */
    default String ensureFolder(Account account, String parentFolderId, String name) {
        throw new UnsupportedOperationException(type() + " 未实现 ensureFolder");
    }

    /** 把分享内的这批文件转存到本账号 targetFolderId 下，返回成功转存数。 */
    default int saveFiles(ShareContext ctx, List<PanFile> files, Account account, String targetFolderId) {
        throw new UnsupportedOperationException(type() + " 未实现 saveFiles");
    }

    // ==================== 凭据探活（定时体检用） ====================

    /** 是否支持凭据探活。默认 false（HealthChecker 跳过，不误判）。 */
    default boolean supportsAlive() {
        return false;
    }

    /**
     * 探活：账号凭据是否仍有效。
     * true=有效（或网络/接口抖动等不确定，不误杀）；false=<b>确认</b>失效，需置 unhealthy 重扫。
     */
    default boolean checkAlive(Account account) {
        throw new UnsupportedOperationException(type() + " 未实现 checkAlive");
    }

    // ==================== 账号信息（昵称/空间，后台展示用） ====================

    /** 是否支持拉取账号信息。默认 false。 */
    default boolean supportsAccountInfo() {
        return false;
    }

    /**
     * 拉取账号信息（昵称/uid/总空间/已用空间）。低频调用（定时 + 落号后），失败返回 null。
     */
    default AccountInfo getAccountInfo(Account account) {
        throw new UnsupportedOperationException(type() + " 未实现 getAccountInfo");
    }

    // ==================== 删除（清理/下架用） ====================

    /**
     * 删除本账号下的文件/文件夹，返回成功删除数。
     * ids 语义随网盘：夸克/迅雷是文件 id，百度是文件「路径」。
     */
    default int delete(Account account, List<String> ids) {
        throw new UnsupportedOperationException(type() + " 未实现 delete");
    }

    // ==================== 凭据更新钩子 ====================

    /**
     * 账号凭据被后台更新（换号 / 重新授权）后回调。默认无操作。
     * 迅雷用它清掉旧 access/refresh token 的内存缓存与落盘文件，
     * 确保这次新授权的 refresh_token 立即生效（否则会继续读旧 token 文件导致刷新失败）。
     */
    default void onCredentialUpdated(Account account) {
    }
}
