package com.jyinshi.transfer.pan;

import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.account.AccountStore;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.xunlei.XunleiDriver;
import com.jyinshi.transfer.service.TransferAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 账号加载器：把主站 {@code transfer_account} 表里的账号+凭据装进内存 {@link AccountStore}，
 * 供驱动执行。替代了原 worker 的 {@code AccountSyncRunner}（HTTP 拉取）。
 *
 * <p>合并后单机单节点，不按 workerId 过滤，加载全部可用账号。凭据在 XUNLEI 用 refresh_token，
 * 其余用 cookie。启动后短延迟首刷，之后每 30 秒对账一次（新增/停用随后台改动生效）。</p>
 */
@Slf4j
@Component
public class PanAccountLoader {

    private final TransferAccountService accountService;
    private final AccountStore store;
    private final XunleiDriver xunleiDriver;

    public PanAccountLoader(TransferAccountService accountService, AccountStore store,
                            @Lazy XunleiDriver xunleiDriver) {
        this.accountService = accountService;
        this.store = store;
        this.xunleiDriver = xunleiDriver;
    }

    @Scheduled(initialDelay = 3_000, fixedDelay = 30_000)
    public void reload() {
        try {
            List<TransferAccount> rows = accountService.listUsableAll();
            List<Account> accts = new ArrayList<>(rows.size());
            Set<String> live = new HashSet<>();
            for (TransferAccount r : rows) {
                Account a = toAccount(r);
                if (a != null) {
                    accts.add(a);
                    live.add(a.getType() + "/" + a.getName());
                }
            }
            List<Account> credChanged = store.syncFromMain(accts);
            for (Account a : credChanged) {
                xunleiDriver.onCredentialUpdated(a);
            }
            // 对账剔除：库里已删（或清了凭据）的账号，从内存池移除，令后台删号 30s 内生效
            for (Account a : store.list()) {
                if (a.getType() != null && !live.contains(a.getType() + "/" + a.getName())) {
                    store.remove(a.getType(), a.getName());
                }
            }
        } catch (Exception e) {
            log.warn("[账号] 从库加载账号失败: {}", e.getMessage());
        }
    }

    private Account toAccount(TransferAccount r) {
        PanType type = PanType.of(r.getPanType());
        if (type == null || r.getAccountName() == null) {
            return null;
        }
        Account a = new Account();
        a.setType(type);
        a.setName(r.getAccountName());
        a.setRole(r.getRole() != null && !r.getRole().isBlank() ? r.getRole() : "transfer");
        a.setEnabled(r.getEnabled() == null || r.getEnabled());
        a.setTargetDirFid(r.getTargetDirFid());
        a.setBaiduAccessToken(r.getBaiduAccessToken());
        if (type == PanType.XUNLEI) {
            a.setRefreshToken(r.getCredential());
        } else {
            a.setCookie(r.getCredential());
        }
        return a;
    }
}
