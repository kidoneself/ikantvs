package com.jyinshi.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jyinshi.common.exception.BizException;
import com.jyinshi.transfer.entity.TransferLoginSession;
import com.jyinshi.transfer.mapper.TransferLoginSessionMapper;
import com.jyinshi.transfer.pan.PanLoginBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 加号/换号会话编排。合并后落号在主站进程内直接完成（{@link PanLoginBridge}），不再经 worker 中转：
 *
 * <ul>
 *   <li>cookie 模式（夸克/百度）：后台粘贴 cookie → 直接落号入库 + 注册进内存账号池，秒级完成。</li>
 *   <li>oauth 模式（迅雷）：先建 pending_auth 会话 → 浏览器授权回调换到 refresh_token →
 *       {@link #attachXunleiToken} 就地落号。</li>
 * </ul>
 *
 * <p>加号 = accountName 留空（自增 quark-2…）；换号/失效恢复 = 指定已有账号名（覆盖凭据，追更绑定不变）。
 * 会话记录只为前端轮询展示结果，落号成功/失败即刻置终态。</p>
 */
@Slf4j
@Service
public class TransferLoginService {

    /** cookie 模式支持的网盘（迅雷走 oauth，不在此列）。 */
    private static final Set<String> COOKIE_PANS = Set.of("quark", "baidu");

    /** 会话最长存活（分钟）：超过判过期，避免长挂。 */
    private static final long TTL_MINUTES = 10;

    private final TransferLoginSessionMapper mapper;
    private final PanLoginBridge loginBridge;

    public TransferLoginService(TransferLoginSessionMapper mapper, PanLoginBridge loginBridge) {
        this.mapper = mapper;
        this.loginBridge = loginBridge;
    }

    // ==================== 后台侧 ====================

    /**
     * 夸克/百度：后台粘贴 cookie 加号/换号。就地落号（入库 + 注册内存池），无需 worker。
     *
     * @param accountName 目标账号名：填已有号=覆盖其 cookie（换号/续期）；留空=新增号。
     */
    public TransferLoginSession startCookie(String panType, String accountName, String cookie) {
        String pt = panType == null ? "" : panType.trim().toLowerCase();
        if (!COOKIE_PANS.contains(pt)) {
            throw new BizException("该网盘不支持 cookie 加号：" + panType);
        }
        if (!StringUtils.hasText(cookie)) {
            throw new BizException("cookie 不能为空");
        }
        TransferLoginSession s = newSession(pt, "cookie", accountName);
        s.setStatus("pending");
        mapper.insert(s);
        try {
            String name = loginBridge.land(pt, "cookie", cookie.trim(), accountName);
            markFinal(s.getSessionId(), "success", name, null);
            s.setStatus("success");
            s.setAccountName(name);
            log.info("[加号] cookie 落号成功 会话={} pan={} 账号={}", s.getSessionId(), pt, name);
        } catch (RuntimeException e) {
            markFinal(s.getSessionId(), "failed", null, e.getMessage());
            throw e;
        }
        return s;
    }

    /**
     * 迅雷：建授权会话（pending_auth，待回调）。返回会话，控制器据其 sessionId 拼授权 URL。
     *
     * @param accountName 目标账号名：填已有号=覆盖 refresh_token（重新授权）；留空=新增号。
     */
    public TransferLoginSession startXunlei(String accountName) {
        TransferLoginSession s = newSession("xunlei", "oauth", accountName);
        s.setStatus("pending_auth");
        mapper.insert(s);
        log.info("[加号] 迅雷授权会话 {} 目标账号={}", s.getSessionId(), s.getAccountName());
        return s;
    }

    /** 迅雷回调换到 refresh_token 后调用：就地落号并置终态。 */
    public void attachXunleiToken(String sessionId, String refreshToken) {
        TransferLoginSession s = requireSession(sessionId);
        if (!"pending_auth".equals(s.getStatus())) {
            throw new BizException("授权会话状态异常或已处理：" + s.getStatus());
        }
        try {
            String name = loginBridge.land("xunlei", "oauth", refreshToken, s.getAccountName());
            markFinal(sessionId, "success", name, null);
            log.info("[加号] 迅雷会话 {} 落号成功，账号 {}", sessionId, name);
        } catch (RuntimeException e) {
            markFinal(sessionId, "failed", null, e.getMessage());
            throw e;
        }
    }

    /** 前端轮询：按 sessionId 取会话状态（含惰性过期）。凭据字段不外泄给前端由 View 负责。 */
    public TransferLoginSession get(String sessionId) {
        TransferLoginSession s = findBySessionId(sessionId);
        if (s == null) {
            throw new BizException("会话不存在或已过期");
        }
        if (isPendingLike(s.getStatus())
                && s.getCreatedAt() != null
                && s.getCreatedAt().plusMinutes(TTL_MINUTES).isBefore(LocalDateTime.now())) {
            markFinal(s.getSessionId(), "expired", null, "会话已过期");
            s.setStatus("expired");
            s.setMessage("会话已过期");
        }
        return s;
    }

    // ==================== 内部 ====================

    /** 每小时清掉 1 小时前的会话（终态记录 + 卡住的僵尸 + 未授权的 pending_auth），避免积垃圾/凭据滞留。 */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupStale() {
        LambdaQueryWrapper<TransferLoginSession> q = Wrappers.lambdaQuery();
        q.lt(TransferLoginSession::getCreatedAt, LocalDateTime.now().minusHours(1));
        int n = mapper.delete(q);
        if (n > 0) {
            log.info("[加号] 清理过期会话 {} 条", n);
        }
    }

    private TransferLoginSession newSession(String panType, String mode, String accountName) {
        TransferLoginSession s = new TransferLoginSession();
        s.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        // 合并后单机；旧库 worker_id 可能仍 NOT NULL，显式写 local 兜底
        s.setWorkerId("local");
        s.setPanType(panType);
        s.setMode(mode);
        if (StringUtils.hasText(accountName)) {
            s.setAccountName(accountName.trim());
        }
        return s;
    }

    private boolean isPendingLike(String status) {
        return "pending".equals(status) || "pending_auth".equals(status) || "claimed".equals(status);
    }

    private void markFinal(String sessionId, String status, String accountName, String message) {
        LambdaUpdateWrapper<TransferLoginSession> u = Wrappers.lambdaUpdate();
        u.eq(TransferLoginSession::getSessionId, sessionId)
                .set(TransferLoginSession::getStatus, status)
                .set(TransferLoginSession::getAccountName, accountName)
                .set(TransferLoginSession::getMessage, message)
                // 落号完成即清凭据，不在库里滞留
                .set(TransferLoginSession::getCredential, null);
        mapper.update(null, u);
    }

    private TransferLoginSession requireSession(String sessionId) {
        TransferLoginSession s = findBySessionId(sessionId);
        if (s == null) {
            throw new BizException("会话不存在：" + sessionId);
        }
        return s;
    }

    private TransferLoginSession findBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        LambdaQueryWrapper<TransferLoginSession> q = Wrappers.lambdaQuery();
        q.eq(TransferLoginSession::getSessionId, sessionId).last("limit 1");
        return mapper.selectOne(q);
    }
}
