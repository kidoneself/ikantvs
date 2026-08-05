package com.jyinshi.transfer.pan;

import com.jyinshi.transfer.pan.driver.CredentialSink;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.service.TransferAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 凭据回写出口（进程内版）。迅雷 refresh_token 每次刷新会滚动，driver 刷新后经此直接写回
 * {@code transfer_account.credential}，保证重启后仍是最新可用凭据。替代了原 worker 的 HTTP 回写。
 */
@Slf4j
@Component
public class PanCredentialSink implements CredentialSink {

    private final TransferAccountService accountService;

    public PanCredentialSink(TransferAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void onRefreshTokenRolled(PanType type, String accountName, String newRefreshToken) {
        try {
            accountService.updateCredentialByName(type.name().toLowerCase(), accountName, newRefreshToken);
        } catch (Exception e) {
            log.warn("[迅雷] 回写滚动 refresh_token 失败 {}/{}: {}", type, accountName, e.getMessage());
        }
    }
}
