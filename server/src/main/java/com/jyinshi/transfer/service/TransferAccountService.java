package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.entity.TransferAccount;
import com.jyinshi.transfer.mapper.TransferAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网盘账号 + 凭据存储（合并后单机，凭据即存本表 credential 列）+ 后台查询。
 * 进程内执行器（PanAccountLoader）据此加载账号入内存。
 */
@Slf4j
@Service
public class TransferAccountService {

    private final TransferAccountMapper accountMapper;
    private final TransferRecordService recordService;
    private final com.jyinshi.transfer.notify.NotifyPort notify;

    public TransferAccountService(TransferAccountMapper accountMapper,
                                  TransferRecordService recordService,
                                  com.jyinshi.transfer.notify.NotifyPort notify) {
        this.accountMapper = accountMapper;
        this.recordService = recordService;
        this.notify = notify;
    }

    /**
     * 进程内探活结果回写健康态（供后台展示，取代原 worker 心跳）。healthy 由 true→false 时发失效通知。
     * 同时刷新 last_seen_at 作「最近可见/检查」时间。
     */
    public void markHealthByName(String panType, String accountName, boolean healthy) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(accountName)) {
            return;
        }
        String pan = panType.toLowerCase();
        TransferAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getAccountName, accountName)
                .last("limit 1"));
        if (acc == null) {
            return;
        }
        boolean wasHealthy = acc.getHealthy() == null || acc.getHealthy();
        acc.setHealthy(healthy);
        acc.setLastSeenAt(LocalDateTime.now());
        accountMapper.updateById(acc);
        if (wasHealthy && !healthy) {
            notify.accountInvalid(pan, accountName);
        }
    }

    /**
     * 进程内账号信息回写（昵称/uid/空间，供后台展示）。只在拿到有效值时覆盖，避免清掉旧值；
     * 同时刷新 last_seen_at。
     */
    public void updateInfoByName(String panType, String accountName, String nickname, String uid,
                                 long totalSpace, long usedSpace) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(accountName)) {
            return;
        }
        TransferAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, panType.toLowerCase())
                .eq(TransferAccount::getAccountName, accountName)
                .last("limit 1"));
        if (acc == null) {
            return;
        }
        if (StringUtils.hasText(nickname)) {
            acc.setNickname(nickname);
        }
        if (StringUtils.hasText(uid)) {
            acc.setUid(uid);
        }
        if (totalSpace >= 0) {
            acc.setTotalSpace(totalSpace);
        }
        if (usedSpace >= 0) {
            acc.setUsedSpace(usedSpace);
        }
        acc.setLastSeenAt(LocalDateTime.now());
        accountMapper.updateById(acc);
    }

    /**
     * 后台保存某百度号的开放平台删除令牌（隐式授权 access_token）。接受三种粘贴形式：
     * ① 授权成功页整条 URL（含 {@code #access_token=...&expires_in=...}）；② 仅 fragment；③ 纯 token。
     * 解析出 access_token 与有效期，按号存进 {@code baidu_access_token}，供 worker 走 xpan 官方接口删除。
     */
    public void setBaiduToken(Long id, String rawValue) {
        TransferAccount a = accountMapper.selectById(id);
        if (a == null) {
            throw new BizException("账号不存在");
        }
        if (!"baidu".equalsIgnoreCase(a.getPanType())) {
            throw new BizException("仅百度号需要开放平台删除令牌");
        }
        ParsedToken pt = parseBaiduToken(rawValue);
        a.setBaiduAccessToken(pt.accessToken);
        a.setBaiduTokenExpireAt(pt.expiresInSeconds > 0
                ? LocalDateTime.now().plusSeconds(pt.expiresInSeconds) : null);
        accountMapper.updateById(a);
        log.info("[账号] 百度删除令牌已更新 {}/{}/{}，有效期至 {}",
                a.getWorkerId(), a.getPanType(), a.getAccountName(), a.getBaiduTokenExpireAt());
    }

    private record ParsedToken(String accessToken, long expiresInSeconds) {
    }

    /** 解析粘贴的百度授权结果，抽出 access_token / expires_in（对齐老项目 qianyun parse_baidu_token）。 */
    private static ParsedToken parseBaiduToken(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BizException("请粘贴授权链接或 access_token");
        }
        String frag = "";
        int hash = value.indexOf('#');
        if (hash >= 0) {
            frag = value.substring(hash + 1);
        } else if (value.contains("access_token=")) {
            int q = value.indexOf('?');
            frag = q >= 0 ? value.substring(q + 1) : value;
        }
        if (!frag.isEmpty()) {
            String token = "";
            long expires = 0;
            for (String kv : frag.split("&")) {
                int eq = kv.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String k = kv.substring(0, eq);
                String v = java.net.URLDecoder.decode(kv.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                if ("access_token".equals(k)) {
                    token = v;
                } else if ("expires_in".equals(k)) {
                    try {
                        expires = Long.parseLong(v.trim());
                    } catch (NumberFormatException ignore) {
                        expires = 0;
                    }
                }
            }
            if (token.isEmpty()) {
                throw new BizException("链接里没有 access_token，请确认已完成授权");
            }
            return new ParsedToken(token, expires);
        }
        // 纯 token（无有效期信息）
        return new ParsedToken(value, 0);
    }

    /**
     * 进程内执行器加载：全部可用账号（含凭据），不按 workerId 过滤（合并后单机单节点）。
     * 排除待移除 / 无凭据的行。
     */
    public List<TransferAccount> listUsableAll() {
        return accountMapper.selectList(new LambdaQueryWrapper<TransferAccount>()
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "")
                .and(w -> w.isNull(TransferAccount::getRemoving).or().eq(TransferAccount::getRemoving, false)));
    }

    /**
     * 落号入库（进程内版，不依赖 workerId）：按 网盘+账号名 找行，存在则覆盖凭据、否则新建。
     * 覆盖凭据即视为「重新有效」，healthy 置回 true，等探活再定生死。新建行归属内嵌节点 workerId。
     */
    public void persistCredentialByName(String panType, String accountName, String credential,
                                        String targetDirFid, String workerId) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(accountName)
                || !StringUtils.hasText(credential)) {
            return;
        }
        String pan = panType.toLowerCase();
        TransferAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getAccountName, accountName)
                .last("limit 1"));
        if (acc == null) {
            acc = new TransferAccount();
            acc.setWorkerId(StringUtils.hasText(workerId) ? workerId : "local");
            acc.setPanType(pan);
            acc.setAccountName(accountName);
            acc.setEnabled(true);
            acc.setHealthy(true);
            acc.setRemoving(false);
            acc.setCredential(credential);
            if (StringUtils.hasText(targetDirFid)) {
                acc.setTargetDirFid(targetDirFid);
            }
            accountMapper.insert(acc);
            log.info("[账号] 落号入库 {}/{}（凭据已存主站）", pan, accountName);
        } else {
            acc.setCredential(credential);
            acc.setHealthy(true);
            acc.setRemoving(false);
            if (StringUtils.hasText(targetDirFid)) {
                acc.setTargetDirFid(targetDirFid);
            }
            accountMapper.updateById(acc);
            log.info("[账号] 更新凭据入库 {}/{}", pan, accountName);
        }
    }

    /**
     * 按 网盘+账号名 回写凭据（迅雷滚动 refresh_token 用），不依赖 workerId（合并后单机）。
     * 仅更新已有行。
     */
    public void updateCredentialByName(String panType, String accountName, String credential) {
        if (!StringUtils.hasText(panType) || !StringUtils.hasText(accountName)
                || !StringUtils.hasText(credential)) {
            return;
        }
        TransferAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, panType.toLowerCase())
                .eq(TransferAccount::getAccountName, accountName)
                .last("limit 1"));
        if (acc == null) {
            return;
        }
        acc.setCredential(credential);
        accountMapper.updateById(acc);
    }

    /**
     * 取某网盘的监控转存专用号（role=monitor、启用、有凭据、健康优先）。
     * 每盘只应有一套；监控转存创建/更新都固定走它，与用户转存号池分离。
     */
    public String monitorAccountName(String panType) {
        TransferAccount a = findMonitorAccount(panType);
        return a != null ? a.getAccountName() : null;
    }

    /**
     * 取某网盘的监控转存专用号整行（含 id / credential），供 NAS 灌盘等需要账号 id 的场景。
     * <p>优先 role=monitor。迅雷一号两用：没有 monitor 时回退到该盘任意启用且有凭据的号。</p>
     */
    public TransferAccount findMonitorAccount(String panType) {
        if (!StringUtils.hasText(panType)) {
            return null;
        }
        String pan = panType.toLowerCase();
        TransferAccount monitor = accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getRole, "monitor")
                .eq(TransferAccount::getEnabled, true)
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "")
                .orderByDesc(TransferAccount::getHealthy)
                .last("limit 1"));
        if (monitor != null || !"xunlei".equals(pan)) {
            return monitor;
        }
        return accountMapper.selectOne(new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getEnabled, true)
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "")
                .orderByDesc(TransferAccount::getHealthy)
                .last("limit 1"));
    }

    /**
     * 该网盘是否有「可用的用户转存号」（role=transfer 或未设分工=默认转存，且启用、有凭据）。
     * 用户点转存前置校验：没有就即时给友好提示，不入队、不占用日更号（各司其职）。
     * <p>迅雷一号两用：monitor 号也算可用转存号。</p>
     * <p>只看启用+有凭据，不卡 healthy（镜像 healthy 可能滞后 worker 实时体检），
     * 真失效由 worker 执行时兜底回 NO_ACCOUNT。</p>
     */
    public boolean hasUsableTransferAccount(String panType) {
        if (!StringUtils.hasText(panType)) {
            return false;
        }
        String pan = panType.toLowerCase();
        LambdaQueryWrapper<TransferAccount> qw = new LambdaQueryWrapper<TransferAccount>()
                .eq(TransferAccount::getPanType, pan)
                .eq(TransferAccount::getEnabled, true)
                .isNotNull(TransferAccount::getCredential)
                .ne(TransferAccount::getCredential, "");
        if (!"xunlei".equals(pan)) {
            qw.and(w -> w.ne(TransferAccount::getRole, "monitor")
                    .or().isNull(TransferAccount::getRole));
        }
        Long n = accountMapper.selectCount(qw);
        return n != null && n > 0;
    }

    /** 后台查全部账号（按网盘、账号名排序）。 */
    public List<TransferAccount> listAll() {
        LambdaQueryWrapper<TransferAccount> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(TransferAccount::getPanType)
                .orderByAsc(TransferAccount::getAccountName);
        List<TransferAccount> list = accountMapper.selectList(qw);
        // 凭据不外泄给后台前端：先据实标记「是否有凭据」，再抹掉 credential 本身。
        // 这样后台能如实显示「未登录/有效/失效」，不再用 healthy 把空壳号冒充成有效。
        for (TransferAccount a : list) {
            a.setHasCredential(StringUtils.hasText(a.getCredential()));
            a.setCredential(null);
            // 百度删除令牌同样脱敏：只暴露「是否已授权 + 是否过期 + 剩余天数」，令牌本身不外泄
            boolean hasToken = StringUtils.hasText(a.getBaiduAccessToken());
            a.setHasBaiduToken(hasToken);
            if (hasToken && a.getBaiduTokenExpireAt() != null) {
                long secs = java.time.Duration.between(LocalDateTime.now(), a.getBaiduTokenExpireAt()).getSeconds();
                a.setBaiduTokenExpired(secs <= 0);
                a.setBaiduTokenDaysLeft(secs / 86400);
            }
            a.setBaiduAccessToken(null);
        }
        return list;
    }

    /** 设置账号分工：transfer=用户转存 / monitor=监控转存。 */
    public void setRole(Long id, String role) {
        if (!"transfer".equals(role) && !"monitor".equals(role)) {
            throw new BizException("role 仅支持 transfer / monitor");
        }
        TransferAccount a = accountMapper.selectById(id);
        if (a == null) {
            throw new BizException("账号不存在");
        }
        a.setRole(role);
        accountMapper.updateById(a);
        log.info("[账号] 设置分工 {}/{} → {}", a.getPanType(), a.getAccountName(), role);
    }

    /**
     * 后台删除某账号（通常因封号）：直接删行（凭据随之清除，进程内账号池 30s 内对账剔除）
     * + 放弃其名下未删资源记录。返回放弃的资源记录条数。
     */
    public int requestRemove(Long id) {
        TransferAccount a = accountMapper.selectById(id);
        if (a == null) {
            throw new BizException("账号不存在");
        }
        int abandoned = recordService.abandonByAccount(a.getPanType(), a.getAccountName());
        accountMapper.deleteById(a.getId());
        log.info("[账号] 删除账号 {}/{}，放弃资源记录 {} 条", a.getPanType(), a.getAccountName(), abandoned);
        return abandoned;
    }
}
