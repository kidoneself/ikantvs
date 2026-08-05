package com.jyinshi.transfer.pan;

import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.pan.account.Account;
import com.jyinshi.transfer.pan.account.AccountInfoService;
import com.jyinshi.transfer.pan.account.AccountStore;
import com.jyinshi.transfer.pan.driver.DriverRegistry;
import com.jyinshi.transfer.pan.driver.PanDriver;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.service.TransferAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 进程内落号：合并后主站已持有凭据（后台粘贴的 cookie / 迅雷 OAuth 回调换到的 refresh_token），
 * 无需再经 worker 中转。此处一步到位：凭据入库（{@code transfer_account}）+ 注册进内存账号池 +
 * 清驱动旧 token 缓存 + 拉一次账号信息。替代了原 worker 的 {@code LoginRunner}。
 */
@Slf4j
@Component
public class PanLoginBridge {

    private final TransferAccountService accountService;
    private final AccountStore store;
    private final DriverRegistry drivers;
    private final AccountInfoService accountInfoService;
    private final PanWorkerProperties props;

    public PanLoginBridge(TransferAccountService accountService, AccountStore store,
                          DriverRegistry drivers, AccountInfoService accountInfoService,
                          PanWorkerProperties props) {
        this.accountService = accountService;
        this.store = store;
        this.drivers = drivers;
        this.accountInfoService = accountInfoService;
        this.props = props;
    }

    /**
     * 落号：targetName 非空=覆盖同名号凭据（换号/续期，追更绑定不变）；空=新增号（自增名）。
     * 迅雷（mode=oauth）凭据存 refreshToken；夸克/百度存 cookie。返回最终账号名。
     */
    public String land(String panType, String mode, String credential, String targetName) {
        PanType type = PanType.of(panType);
        if (type == null) {
            throw new BizException("未知网盘类型：" + panType);
        }
        if (!StringUtils.hasText(credential)) {
            throw new BizException("凭据为空");
        }
        String name = StringUtils.hasText(targetName) ? targetName.trim() : store.nextName(type);

        // 1) 凭据入库（主站为唯一存储，重启后由 PanAccountLoader 恢复）
        accountService.persistCredentialByName(type.name().toLowerCase(), name, credential,
                null, props.getWorkerId());

        // 2) 立即注册进内存账号池（不必等 30s 定时对账，后台加号即时可用）
        Account acc = new Account();
        acc.setType(type);
        acc.setName(name);
        acc.setWeight(1);
        acc.setEnabled(true);
        if (type == PanType.XUNLEI || "oauth".equalsIgnoreCase(mode)) {
            acc.setRefreshToken(credential);
        } else {
            acc.setCookie(credential);
        }
        store.upsert(acc);

        // 3) 换号/重新授权：清驱动旧 token 缓存（迅雷尤为关键），再拉一次账号信息
        PanDriver driver = drivers.get(type);
        if (driver != null) {
            driver.onCredentialUpdated(acc);
        }
        accountInfoService.refresh(acc);
        log.info("[加号] 进程内落号成功：{}/{}", type, name);
        return name;
    }
}
