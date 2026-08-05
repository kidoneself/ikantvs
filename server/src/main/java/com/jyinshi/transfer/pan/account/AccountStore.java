package com.jyinshi.transfer.pan.account;

import com.jyinshi.transfer.pan.driver.PanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本机账号的内存持有者（方案A：凭据集中存主站，worker 不落磁盘）。
 *
 * <p>账号与凭据来自主站：启动/定时由 {@code AccountSyncRunner} 拉取 → {@link #syncFromMain}
 * 合并进内存；后台粘贴 cookie / 迅雷授权也走加号流即时 {@link #upsert}。运行期健康/信息字段
 * 只在内存，随心跳回报主站。</p>
 */
@Slf4j
@Component
public class AccountStore {

    /** 运行期账号列表；读写在本类内 synchronized。 */
    private final List<Account> accounts = new ArrayList<>();

    /** 当前账号快照（副本，外部遍历安全）。 */
    public synchronized List<Account> list() {
        return new ArrayList<>(accounts);
    }

    /** 新增或更新一个账号（按 type+name 唯一，存在则覆盖凭据）。加号落号后调用。 */
    public synchronized Account upsert(Account acc) {
        accounts.removeIf(a -> a.getType() == acc.getType() && a.getName().equals(acc.getName()));
        accounts.add(acc);
        log.info("[账号] upsert {}/{}", acc.getType(), acc.getName());
        return acc;
    }

    /** 生成一个不重名的账号名（如 quark-1、quark-2）。加号未指定名时用。 */
    public synchronized String nextName(PanType type) {
        int n = 1;
        while (true) {
            String candidate = type.name().toLowerCase() + "-" + n;
            boolean exists = accounts.stream()
                    .anyMatch(a -> a.getType() == type && candidate.equals(a.getName()));
            if (!exists) {
                return candidate;
            }
            n++;
        }
    }

    /** 删除一个账号（主站下发的「待移除」指令执行）。 */
    public synchronized boolean remove(PanType type, String name) {
        boolean removed = accounts.removeIf(a -> a.getType() == type && a.getName().equals(name));
        if (removed) {
            log.info("[账号] 删除 {}/{}", type, name);
        }
        return removed;
    }

    /**
     * 从主站拉取的账号合并进内存（upsert-only，不删）：
     * <ul>
     *   <li>新号 → 直接加入（含凭据），用于重启后从主站恢复。</li>
     *   <li>已有号 → 更新 enabled/权重/目标目录。</li>
     *   <li>迅雷 refresh_token：库里与内存不同则采用库值（千云滚动后回写库，主站需跟上），
     *       并通过返回列表让驱动清缓存；库空不覆盖内存。</li>
     *   <li>其它盘 cookie：仅在内存为空时采用，避免覆盖刚粘贴的值。</li>
     * </ul>
     *
     * @return 凭据被库覆盖的账号（调用方应通知对应 driver 清 token 缓存）
     */
    public synchronized List<Account> syncFromMain(List<Account> fromMain) {
        List<Account> credChanged = new ArrayList<>();
        if (fromMain == null || fromMain.isEmpty()) {
            return credChanged;
        }
        int added = 0;
        for (Account in : fromMain) {
            if (in.getType() == null || in.getName() == null) {
                continue;
            }
            Account cur = accounts.stream()
                    .filter(a -> a.getType() == in.getType() && in.getName().equals(a.getName()))
                    .findFirst().orElse(null);
            if (cur == null) {
                accounts.add(in);
                added++;
            } else {
                cur.setEnabled(in.isEnabled());
                cur.setWeight(in.getWeight());
                // role 以主站为准（后台改分工后随下次同步生效）；空值兜底 transfer
                if (in.getRole() != null && !in.getRole().isBlank()) {
                    cur.setRole(in.getRole());
                }
                if (in.getTargetDirFid() != null && !in.getTargetDirFid().isBlank()) {
                    cur.setTargetDirFid(in.getTargetDirFid());
                }
                if (in.getType() == PanType.XUNLEI) {
                    if (!blank(in.getRefreshToken())
                            && (blank(cur.getRefreshToken())
                            || !in.getRefreshToken().equals(cur.getRefreshToken()))) {
                        boolean changed = !blank(cur.getRefreshToken())
                                && !in.getRefreshToken().equals(cur.getRefreshToken());
                        cur.setRefreshToken(in.getRefreshToken());
                        if (changed) {
                            credChanged.add(cur);
                        }
                    }
                } else if (blank(cur.getCookie())) {
                    cur.setCookie(in.getCookie());
                }
                // 百度删除令牌只由后台设置、worker 不滚动：主站有新值就覆盖，令重新授权能同步下来
                if (in.getType() == PanType.BAIDU && !blank(in.getBaiduAccessToken())) {
                    cur.setBaiduAccessToken(in.getBaiduAccessToken());
                }
            }
        }
        if (added > 0) {
            log.info("[账号] 从主站同步：新增 {} 个（当前共 {} 个）", added, accounts.size());
        }
        if (!credChanged.isEmpty()) {
            log.info("[账号] 迅雷凭据已随库更新 {} 个（千云/外部滚动）", credChanged.size());
        }
        return credChanged;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
