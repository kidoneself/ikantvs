package com.jyinshi.transfer.pan.account;

import com.jyinshi.transfer.pan.PanWorkerProperties;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.DriverRegistry;
import com.jyinshi.transfer.service.TransferAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 账号凭据定时体检：逐个账号调用其 driver 的 {@link PanDriver#checkAlive} 探活。
 *
 * <p>确认失效 → 就地把账号标记 {@link Account#setUnhealthy}=true（账号池随即跳过它，
 * 心跳把 unhealthy 上报主站，镜像置 healthy=0，后台可提示重扫）。探活对未登录敏感、
 * 对网络抖动宽容（driver 里不确定一律当活着，不误杀）。凭据本身不出本机。</p>
 */
@Slf4j
@Component
public class AccountHealthChecker {

    private final PanWorkerProperties props;
    private final AccountStore store;
    private final DriverRegistry drivers;
    private final TransferAccountService accountService;

    public AccountHealthChecker(PanWorkerProperties props, AccountStore store, DriverRegistry drivers,
                                TransferAccountService accountService) {
        this.props = props;
        this.store = store;
        this.drivers = drivers;
        this.accountService = accountService;
    }

    @Scheduled(fixedDelayString = "${jyinshi.transfer.pan.health-check-interval-ms:1800000}",
            initialDelayString = "${jyinshi.transfer.pan.health-check-initial-delay-ms:120000}")
    public void check() {
        if (props.getHealthCheckIntervalMs() <= 0) {
            return;
        }
        for (Account a : store.list()) {
            if (a.getType() == null || !a.isEnabled()) {
                continue;
            }
            PanDriver driver = drivers.get(a.getType());
            if (driver == null || !driver.supportsAlive()) {
                continue;
            }
            try {
                boolean alive = driver.checkAlive(a);
                if (!alive && !a.isUnhealthy()) {
                    a.setUnhealthy(true);
                    log.warn("[体检] 账号失效: {}/{}（已停用，需重新扫码）", a.getType(), a.getName());
                } else if (alive && a.isUnhealthy()) {
                    a.setUnhealthy(false);
                    log.info("[体检] 账号恢复: {}/{}", a.getType(), a.getName());
                }
                // 回写主站库供后台展示（healthy 由 true→false 时 service 内发失效通知）
                accountService.markHealthByName(a.getType().name().toLowerCase(), a.getName(), alive);
            } catch (Exception e) {
                log.warn("[体检] 账号 {}/{} 探活异常（忽略）: {}", a.getType(), a.getName(), e.getMessage());
            }
        }
    }
}
