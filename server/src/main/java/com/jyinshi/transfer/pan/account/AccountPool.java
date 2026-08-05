package com.jyinshi.transfer.pan.account;

import com.jyinshi.transfer.pan.driver.PanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号池：按网盘类型轮询挑一个健康账号。
 *
 * <p>策略故意做得简单：同类型账号里，选「可用 + 最久未用」的一个（加权：权重越高越易被选）。
 * 账号与本机绑定，轮询只在本机自己的账号之间进行，不跨机 → 不产生异地。</p>
 */
@Slf4j
@Component
public class AccountPool {

    private final AccountStore store;

    public AccountPool(AccountStore store) {
        this.store = store;
    }

    /**
     * 按账号名精确取号（追更/删除用回首转的号）。
     * 找到且可用则返回；名字为空或找不到/不可用则回退到 {@link #pick(PanType)} 按池选。
     */
    public Account pickByName(PanType type, String name) {
        if (name != null && !name.isBlank()) {
            for (Account a : store.list()) {
                if (a.getType() == type && name.equals(a.getName())) {
                    if (a.available()) {
                        a.touch();
                        return a;
                    }
                    // 指定号不可用（失效/停用）：不静默换号，返回 null 让上层报错，避免删错/追更进错夹
                    log.warn("[账号池] 指定账号 {} 不可用（失效/停用），不自动换号", name);
                    return null;
                }
            }
            log.warn("[账号池] 未找到指定账号 {}（type={}），回退池选", name, type);
        }
        return pick(type);
    }

    /** 挑一个可用账号（任意分工）；没有则返回 null。用于 probe 等只读、不写盘的场景。 */
    public Account pick(PanType type) {
        return pick(type, false);
    }

    /**
     * 用户临时转存专用选号：只在「转存号」里挑（role=transfer 或未设=默认转存），
     * 日更号(monitor)一律排除，保证各司其职。没有可用转存号则返回 null（上层报友好提示）。
     * <p>迅雷一号两用：不排除 monitor。</p>
     */
    public Account pickForTransfer(PanType type) {
        // 迅雷用户转存与监控/灌盘共用同一号，选号时不按 role 拆开
        return pick(type, type != PanType.XUNLEI);
    }

    /** 挑一个可用账号；transferOnly=true 时排除日更号。没有则返回 null。 */
    private Account pick(PanType type, boolean transferOnly) {
        Account best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Account a : store.list()) {
            if (a.getType() != type || !a.available()) {
                continue;
            }
            if (transferOnly && isMonitor(a)) {
                continue;
            }
            // 分数 = 权重 - 归一化的最近使用时间惩罚（越久没用分越高）
            double idleMinutes = (System.currentTimeMillis() - a.getLastUsedAt()) / 60000.0;
            double score = a.getWeight() + Math.min(idleMinutes, 60);
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        if (best != null) {
            best.touch();
        }
        return best;
    }

    /** 是否日更号（monitor）。role 空/未设视为转存号。 */
    private static boolean isMonitor(Account a) {
        return "monitor".equalsIgnoreCase(a.getRole());
    }

    /** 标记账号不健康（cookie/token 失效），后续暂时跳过。 */
    public void markBad(Account account) {
        if (account != null) {
            account.setUnhealthy(true);
            log.warn("[账号池] 账号 {} 标记为不健康（凭据可能失效），暂停使用", account.getName());
        }
    }

    public List<Account> all() {
        return store.list();
    }

    /** 删除本机账号（主站下发的「待移除」指令执行）。 */
    public boolean remove(PanType type, String name) {
        return store.remove(type, name);
    }
}
