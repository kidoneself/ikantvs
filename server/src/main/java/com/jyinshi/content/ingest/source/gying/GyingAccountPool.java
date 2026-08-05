package com.jyinshi.content.ingest.source.gying;

import com.jyinshi.content.ingest.IngestProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gying 账号池：从配置读 {@code user:pass} 列表，懒加载登录、多账号轮询、失效自动剔除并限频重试。
 *
 * <p>登录/PoW 较慢，故首次搜索时才初始化（不阻塞应用启动）。无可用账号时 {@link #search} 抛异常，
 * 由上层 {@code GyingSource} 兜底返回空。
 */
@Slf4j
public class GyingAccountPool {

    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000;

    private final IngestProperties.Gying cfg;
    private final List<Account> accounts;
    private final List<GyingSearchClient> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger cursor = new AtomicInteger(0);

    private volatile boolean initialized = false;
    private volatile long lastRetryTime = 0;

    public GyingAccountPool(IngestProperties.Gying cfg) {
        this.cfg = cfg;
        this.accounts = parseAccounts(cfg.getAccounts());
    }

    /** 是否配了账号（没配则该来源不启用）。 */
    public boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    /** 用轮询到的账号搜索；返回该账号的原始命中列表。 */
    public List<GyingSearchClient.SearchResult> search(String keyword) throws Exception {
        ensureInitialized();
        GyingSearchClient client = nextClient();
        try {
            return client.search(keyword);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("403")) {
                log.warn("[gying] 账号失效（403），剔除并触发重试");
                clients.remove(client);
                lastRetryTime = 0;
                tryRetryLogin();
            }
            throw e;
        }
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        log.info("[gying] 初始化账号池，共 {} 个账号", accounts.size());
        for (Account acc : accounts) {
            loginAccount(acc);
        }
        if (clients.isEmpty()) {
            log.warn("[gying] 所有账号登录失败，搜索时将限频重试");
        } else {
            log.info("[gying] 账号池就绪，可用 {}/{}", clients.size(), accounts.size());
        }
    }

    private boolean loginAccount(Account acc) {
        try {
            GyingSearchClient client = new GyingSearchClient(
                    cfg.getBaseUrl(), cfg.getCookieDir(), cfg.getHttpProxy(), cfg.getDetailConcurrency());
            if (client.login(acc.username, acc.password)) {
                clients.add(client);
                return true;
            }
            log.warn("[gying] 账号 {} 登录失败", mask(acc.username));
        } catch (Exception e) {
            // getMessage() 常为 null（如部分 SSL/连接异常），打出类型方便排查
            log.warn("[gying] 账号 {} 登录异常: {}", mask(acc.username), summarize(e));
        }
        return false;
    }

    private static String summarize(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                sb.append(": ").append(t.getMessage());
            }
        }
        return sb.length() > 0 ? sb.toString() : e.getClass().getName();
    }

    private GyingSearchClient nextClient() {
        if (clients.isEmpty()) {
            tryRetryLogin();
            if (clients.isEmpty()) {
                throw new RuntimeException("Gying 账号均未登录成功（已配置 "
                        + accounts.size() + " 个，将限频重试）");
            }
        }
        int idx = Math.floorMod(cursor.getAndIncrement(), clients.size());
        return clients.get(idx);
    }

    private synchronized void tryRetryLogin() {
        if (clients.size() >= accounts.size()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRetryTime < RETRY_INTERVAL_MS) {
            return;
        }
        lastRetryTime = now;
        int recovered = 0;
        for (Account acc : accounts) {
            if (loginAccount(acc)) {
                recovered++;
            }
        }
        if (recovered > 0) {
            log.info("[gying] 重试恢复 {} 个账号，当前可用 {}", recovered, clients.size());
        }
    }

    private static List<Account> parseAccounts(String raw) {
        List<Account> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String entry : raw.split(",")) {
            String e = entry.trim();
            if (e.isEmpty()) {
                continue;
            }
            int sep = e.indexOf(':');
            if (sep <= 0 || sep >= e.length() - 1) {
                continue;
            }
            out.add(new Account(e.substring(0, sep).trim(), e.substring(sep + 1).trim()));
        }
        return out;
    }

    private static String mask(String username) {
        if (username == null || username.length() <= 3) {
            return username;
        }
        return username.substring(0, 2) + "***" + username.substring(username.length() - 2);
    }

    private record Account(String username, String password) {
    }
}
