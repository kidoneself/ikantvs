package com.jyinshi.transfer.pan.account;

import com.jyinshi.transfer.pan.driver.AccountInfo;
import com.jyinshi.transfer.pan.driver.DriverRegistry;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.service.TransferAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 账号信息（昵称/空间）刷新器。低频拉取，规避网盘接口频控：
 * 启动后延迟一次 + 每 60 分钟一轮；落号成功后由 LoginRunner 单独触发一次。
 *
 * <p>拉到的信息写回 {@link Account} 的运行期字段（不持久化），心跳时随账号摘要上报主站。</p>
 */
@Slf4j
@Component
public class AccountInfoService {

    private final AccountStore store;
    private final DriverRegistry drivers;
    private final TransferAccountService accountService;

    public AccountInfoService(AccountStore store, DriverRegistry drivers,
                              TransferAccountService accountService) {
        this.store = store;
        this.drivers = drivers;
        this.accountService = accountService;
    }

    /** 定时刷新全部账号（启动 25s 后首刷，之后每 60 分钟）。 */
    @Scheduled(initialDelay = 25_000, fixedDelay = 3_600_000)
    public void refreshAll() {
        for (Account a : store.list()) {
            refresh(a);
        }
    }

    /** 刷新单个账号信息，best-effort：拿到才覆盖，失败保留旧值不清空。 */
    public void refresh(Account account) {
        if (account == null || account.getType() == null || !account.available()) {
            return;
        }
        PanDriver d = drivers.get(account.getType());
        if (d == null || !d.supportsAccountInfo()) {
            return;
        }
        try {
            AccountInfo info = d.getAccountInfo(account);
            if (info == null) {
                return;
            }
            account.setNickname(info.getNickname());
            account.setUid(info.getUid());
            account.setTotalSpace(info.getTotalSpace());
            account.setUsedSpace(info.getUsedSpace());
            account.setInfoAt(System.currentTimeMillis());
            // 回写主站库供后台展示昵称/容量
            accountService.updateInfoByName(account.getType().name().toLowerCase(), account.getName(),
                    info.getNickname(), info.getUid(), info.getTotalSpace(), info.getUsedSpace());
        } catch (Exception e) {
            log.warn("[账号信息] 刷新 {}/{} 失败: {}", account.getType(), account.getName(), e.getMessage());
        }
    }
}
