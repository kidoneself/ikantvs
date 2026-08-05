package com.jyinshi.transfer.pan.exec;

import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.account.AccountPool;
import com.jyinshi.transfer.pan.driver.DriverRegistry;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.SaveResult;
import com.jyinshi.transfer.pan.driver.ShareInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 任务执行核心：挑账号 + 调驱动 + 失效标记。HTTP 被调（TransferController）与
 * agent 拉任务（AgentRunner）共用同一条执行路径，避免逻辑分叉。
 */
@Slf4j
@Component
public class JobExecutor {

    private final DriverRegistry registry;
    private final AccountPool accountPool;
    private final IncrementalSyncService syncService;

    public JobExecutor(DriverRegistry registry, AccountPool accountPool,
                       IncrementalSyncService syncService) {
        this.registry = registry;
        this.accountPool = accountPool;
        this.syncService = syncService;
    }

    /** 读分享信息（追更巡检 + 死活检测）。夸克/百度免账号，迅雷取账号。 */
    public ShareInfo probe(PanType type, String shareUrl, String password) {
        PanDriver driver = requireDriver(type);
        if (driver == null) {
            return ShareInfo.uncertain("本机不支持网盘类型: " + type);
        }
        Account account = accountPool.pick(type); // 夸克/百度可为 null
        return driver.getShareInfo(shareUrl, password, account);
    }

    /** 转存到本机账号（用户点转存路径，不额外列夹，保证低延迟；不指定号，走账号池）。 */
    public SaveResult save(PanType type, String shareUrl, String password, String toFolderId) {
        return save(type, shareUrl, password, toFolderId, null, null, false);
    }

    /**
     * 转存到本机账号。
     *
     * @param accountName   指定执行账号名（追更首转用回主站选定的「监控号」，落进它的专属夹）；
     *                      为空则走账号池按类型选号（用户点转存路径）。
     * @param landingDir    顶层落地目录名（追更=追更资源 / 用户转存=临时转存）；toFolderId 为空时，
     *                      在账号根下建/复用该夹作父目录，把「剧名」夹落进去，实现追更/临时物理隔离。
     * @param collectLatest 成功后是否列落地夹挑"最新文件名"回填（追更首转用；用户点转存传 false 免延迟）
     */
    public SaveResult save(PanType type, String shareUrl, String password, String toFolderId,
                           String accountName, String landingDir, boolean collectLatest) {
        long t0 = System.nanoTime();
        PanDriver driver = requireDriver(type);
        if (driver == null) {
            return SaveResult.error("UNSUPPORTED", "本机不支持网盘类型: " + type);
        }
        // 指定号名（追更/清理用回首转的号，含日更号）→ 精确匹配；
        // 未指定（用户临时转存）→ 只在转存号里选，绝不占用日更号（各司其职）。
        boolean named = accountName != null && !accountName.isBlank();
        Account account = named ? accountPool.pickByName(type, accountName)
                                : accountPool.pickForTransfer(type);
        long pickMs = TransferTiming.msSince(t0);
        if (account == null) {
            log.info("[转存耗时] executor pan={} 指定={} pick={}ms ok=false err=NO_ACCOUNT",
                    type, accountName, pickMs);
            String hint = named ? "（指定=" + accountName + "，可能失效/停用）"
                                : "（无可用转存号）";
            return SaveResult.error("NO_ACCOUNT", "本机无可用 " + type + " 账号" + hint);
        }
        // 落地父夹：显式 toFolderId 优先；否则按 landingDir 在账号根下建/复用顶层夹（追更资源/临时转存）
        String parent = toFolderId;
        if ((parent == null || parent.isBlank()) && landingDir != null && !landingDir.isBlank()) {
            parent = driver.ensureFolder(account, null, landingDir);
            if (parent == null || parent.isBlank()) {
                return SaveResult.error("CREATE_FOLDER_FAILED", "创建落地目录失败: " + landingDir);
            }
        }
        long t1 = System.nanoTime();
        SaveResult result = driver.save(shareUrl, password, account, parent);
        long driverMs = TransferTiming.msSince(t1);
        log.info("[转存耗时] executor pan={} account={} pick={}ms driver={}ms ok={} code={}",
                type, account.getName(), pickMs, driverMs, result.isSuccess(),
                result.isSuccess() ? "ok" : result.getErrorCode());
        // 首转成功后回填"最新文件名"：列落地夹挑最新，主站据此回填集数（追更 sync 已自带该值）。
        if (collectLatest && result.isSuccess() && result.getLatestFileName() == null) {
            result.setLatestFileName(syncService.latestInFolder(driver, account, result.getSavedFolderId()));
        }
        // 只在「账号级」硬失败时停用账号；链接级失败（TOKEN_FAILED/SHARE_INVALID/NO_FILES 等）
        // 是单条分享失效，不能连累整个账号——否则一条死链就把全盘转存打成 NO_ACCOUNT。
        // 真·凭据失效交给 AccountHealthChecker 定时体检兜底（会自动恢复）。
        if (!result.isSuccess() && "NO_COOKIE".equals(result.getErrorCode())) {
            accountPool.markBad(account);
        }
        return result;
    }

    /**
     * 删除本机账号下的文件/文件夹（清理/下架）。ids：夸克/迅雷为 id，百度为路径。
     * accountName 指定用回首转的号（避免用别的号删、删不掉）；为空则池选。
     */
    public int delete(PanType type, java.util.List<String> ids, String accountName) {
        PanDriver driver = requireDriver(type);
        if (driver == null) {
            return 0;
        }
        Account account = accountPool.pickByName(type, accountName);
        if (account == null) {
            log.warn("[执行] delete 无可用账号 type={}, 指定={}", type, accountName);
            return 0;
        }
        return driver.delete(account, ids);
    }

    /**
     * 追更增量同步：只转新增、进固定夹、不建新分享。
     * accountName 用回首转的号，进对固定夹；为空则池选。
     */
    public SyncResult sync(PanType type, String shareUrl, String password,
                           String targetFolderId, String accountName) {
        PanDriver driver = requireDriver(type);
        if (driver == null) {
            return SyncResult.fail("本机不支持网盘类型: " + type);
        }
        if (!driver.supportsSync()) {
            return SyncResult.fail(type + " 暂未实现增量同步");
        }
        Account account = accountPool.pickByName(type, accountName);
        if (account == null) {
            return SyncResult.fail("本机无可用 " + type + " 账号（指定=" + accountName + "）");
        }
        return syncService.sync(driver, shareUrl, password, account, targetFolderId);
    }

    private PanDriver requireDriver(PanType type) {
        return registry.supports(type) ? registry.get(type) : null;
    }
}
