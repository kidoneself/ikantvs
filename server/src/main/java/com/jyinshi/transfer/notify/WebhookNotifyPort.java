package com.jyinshi.transfer.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.content.entity.Media;
import com.jyinshi.content.entity.MediaLink;
import com.jyinshi.content.mapper.MediaLinkMapper;
import com.jyinshi.content.mapper.MediaMapper;
import com.jyinshi.content.service.EpisodeExtractor;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.transfer.entity.TransferMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运维通知：默认飞书应用私聊（对齐老站）；也可飞书/企微群机器人 webhook。
 *
 * <p>追更更新按剧攒批，文案对齐老站 {@code MonitorScheduleTask}：
 * {@code ✔ 剧名 → 集数}，最新更新的剧排最上面；账号失效等告警仍即时推送（1h 去重）。</p>
 */
@Slf4j
@Primary
@Component
public class WebhookNotifyPort implements NotifyPort {

    private static final long DEDUP_MS = 60 * 60 * 1000L;
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MSG_URL = "https://open.feishu.cn/open-apis/im/v1/messages";

    private final SysConfigService config;
    private final ObjectMapper objectMapper;
    private final MediaLinkMapper mediaLinkMapper;
    private final MediaMapper mediaMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ConcurrentHashMap<String, Long> lastSent = new ConcurrentHashMap<>();

    /** 追更更新缓冲：key=mediaId 或 linkId；LinkedHashMap 保序，新触达的挪到末尾。 */
    private final Object updateLock = new Object();
    private final LinkedHashMap<String, PendingUpdate> updateBuffer = new LinkedHashMap<>();

    private volatile String cachedToken;
    private volatile long tokenExpireAtMs;

    public WebhookNotifyPort(SysConfigService config, ObjectMapper objectMapper,
                             MediaLinkMapper mediaLinkMapper, MediaMapper mediaMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.mediaLinkMapper = mediaLinkMapper;
        this.mediaMapper = mediaMapper;
    }

    @Override
    public void syncUpdated(TransferMonitor monitor, String latestFileName) {
        if (!config.getBool(SysConfigService.NOTIFY_ENABLED, false)) {
            return;
        }
        String ep = EpisodeExtractor.extractDisplay(latestFileName);
        if (!StringUtils.hasText(ep)) {
            ep = StringUtils.hasText(latestFileName) ? trimName(latestFileName) : null;
        }
        if (!StringUtils.hasText(ep)) {
            return;
        }
        ResolvedDrama drama = resolveDrama(monitor);
        String key = drama.key();
        synchronized (updateLock) {
            PendingUpdate prev = updateBuffer.remove(key);
            String keepEp = prev == null ? ep : EpisodeExtractor.pickLatest(prev.episode, ep);
            if (!StringUtils.hasText(keepEp)) {
                keepEp = ep;
            }
            updateBuffer.put(key, new PendingUpdate(drama.title(), keepEp));
        }
    }

    /**
     * 攒批播报：窗口内多剧合成一条（最新触达的排最前），避免百度/夸克/迅雷各推一条刷屏。
     */
    @Scheduled(fixedDelayString = "${jyinshi.notify.update-flush-ms:45000}")
    public void flushUpdateBroadcast() {
        List<PendingUpdate> list;
        synchronized (updateLock) {
            if (updateBuffer.isEmpty()) {
                return;
            }
            list = new ArrayList<>(updateBuffer.values());
            updateBuffer.clear();
        }
        Collections.reverse(list);
        List<UpdateNotifyTexts.Item> items = new ArrayList<>(list.size());
        for (PendingUpdate p : list) {
            items.add(new UpdateNotifyTexts.Item(p.title, p.episode));
        }
        String text = UpdateNotifyTexts.broadcast(items);
        try {
            dispatch(text);
            log.info("[通知] 追更更新汇总已推送，共 {} 部", list.size());
        } catch (Exception e) {
            log.warn("[通知] 追更更新汇总失败: {}", e.getMessage());
        }
    }

    /** 飞书机器人回复用户消息（口令「今日」等）。 */
    public void replyMessage(String messageId, String text) throws Exception {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(text)) {
            return;
        }
        String appId = config.getOrDefault(SysConfigService.NOTIFY_FEISHU_APP_ID, "").trim();
        String appSecret = config.getOrDefault(SysConfigService.NOTIFY_FEISHU_APP_SECRET, "").trim();
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("飞书应用未配齐 app-id/secret");
        }
        String token = tenantAccessToken(appId, appSecret);
        Map<String, Object> content = Map.of("text", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", objectMapper.writeValueAsString(content));

        String url = MSG_URL + "/" + messageId + "/reply";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        int code = root.path("code").asInt(-1);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300 || code != 0) {
            throw new IllegalStateException("飞书回复失败: " + trunc(root.path("msg").asText(resp.body())));
        }
        log.info("[通知] 已回复飞书消息 messageId={}", messageId);
    }

    /** 主动私聊默认用户（无 messageId 时兜底）。 */
    public void sendToDefaultUser(String text) throws Exception {
        dispatch(text);
    }

    private ResolvedDrama resolveDrama(TransferMonitor monitor) {
        Long linkId = monitor != null ? monitor.getMediaLinkId() : null;
        if (linkId != null) {
            MediaLink link = mediaLinkMapper.selectById(linkId);
            if (link != null && link.getMediaId() != null) {
                Media media = mediaMapper.selectById(link.getMediaId());
                if (media != null && StringUtils.hasText(media.getTitle())) {
                    return new ResolvedDrama("m:" + media.getId(), media.getTitle().trim());
                }
                if (StringUtils.hasText(link.getNote())) {
                    return new ResolvedDrama("m:" + link.getMediaId(), link.getNote().trim());
                }
                return new ResolvedDrama("m:" + link.getMediaId(), "未命名");
            }
            if (link != null && StringUtils.hasText(link.getNote())) {
                return new ResolvedDrama("l:" + linkId, link.getNote().trim());
            }
        }
        if (monitor != null && StringUtils.hasText(monitor.getLastTitle())) {
            return new ResolvedDrama("t:" + monitor.getLastTitle().trim(), monitor.getLastTitle().trim());
        }
        return new ResolvedDrama("l:" + (linkId != null ? linkId : "unknown"), "未命名资源");
    }

    @Override
    public void monitorInvalid(TransferMonitor monitor) {
        String key = "monitor-invalid:" + monitor.getId();
        String title = resolveDrama(monitor).title();
        send(key, "追更源失效",
                "剧=" + title
                        + " 盘=" + monitor.getPanType()
                        + "\n源=" + nullToDash(monitor.getShareUrl()));
    }

    @Override
    public void accountInvalid(String panType, String accountName) {
        String key = "account-invalid:" + panType + ":" + accountName;
        send(key, "网盘账号失效", "盘=" + panType + " 账号=" + accountName + "（请后台重新扫码）");
    }

    private void send(String dedupKey, String title, String body) {
        if (!config.getBool(SysConfigService.NOTIFY_ENABLED, false)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastSent.get(dedupKey);
        if (prev != null && now - prev < DEDUP_MS) {
            return;
        }
        lastSent.put(dedupKey, now);
        try {
            dispatch("【爱看】" + title + "\n" + body);
        } catch (Exception e) {
            log.warn("[通知] 推送失败 title={}: {}", title, e.getMessage());
        }
    }

    private void dispatch(String text) throws Exception {
        String channel = config.getOrDefault(SysConfigService.NOTIFY_CHANNEL, "feishu").trim().toLowerCase();
        if ("feishu".equals(channel) || channel.isEmpty()) {
            sendFeishuApp(text);
        } else if ("feishu_bot".equals(channel)) {
            sendFeishuBot(text);
        } else if ("wecom".equals(channel)) {
            sendWecom(text);
        } else {
            log.warn("[通知] 未知通道 channel={}", channel);
        }
    }

    /** 飞书开放平台应用 → 私聊默认用户（老站 FeishuMessageService.sendToDefaultUser）。 */
    private void sendFeishuApp(String text) throws Exception {
        String appId = config.getOrDefault(SysConfigService.NOTIFY_FEISHU_APP_ID, "").trim();
        String appSecret = config.getOrDefault(SysConfigService.NOTIFY_FEISHU_APP_SECRET, "").trim();
        String userId = config.getOrDefault(SysConfigService.NOTIFY_FEISHU_USER_ID, "").trim();
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret) || !StringUtils.hasText(userId)) {
            log.warn("[通知] 飞书应用未配齐 app-id/secret/user-id，跳过");
            return;
        }
        String token = tenantAccessToken(appId, appSecret);
        Map<String, Object> content = Map.of("text", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", userId);
        body.put("msg_type", "text");
        body.put("content", objectMapper.writeValueAsString(content));

        String receiveType = userId.startsWith("ou_") ? "open_id" : "user_id";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MSG_URL + "?receive_id_type=" + receiveType))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        int code = root.path("code").asInt(-1);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300 || code != 0) {
            log.warn("[通知] 飞书应用发送失败 HTTP {} code={} msg={}",
                    resp.statusCode(), code, trunc(root.path("msg").asText(resp.body())));
        } else {
            log.info("[通知] 已推送 channel=feishu(app) user={}", userId);
        }
    }

    private String tenantAccessToken(String appId, String appSecret) throws Exception {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAtMs - 60_000L) {
            return cachedToken;
        }
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        int code = root.path("code").asInt(-1);
        String token = root.path("tenant_access_token").asText("");
        int expire = root.path("expire").asInt(7200);
        if (code != 0 || !StringUtils.hasText(token)) {
            throw new IllegalStateException("获取 tenant_access_token 失败: " + trunc(resp.body()));
        }
        cachedToken = token;
        tokenExpireAtMs = now + expire * 1000L;
        return token;
    }

    private void sendFeishuBot(String text) throws Exception {
        String webhook = config.getOrDefault(SysConfigService.NOTIFY_WEBHOOK_URL, "").trim();
        if (!StringUtils.hasText(webhook)) {
            log.warn("[通知] feishu_bot 未配置 webhook.url，跳过");
            return;
        }
        Map<String, Object> payload = feishuBotText(text);
        String secret = config.getOrDefault(SysConfigService.NOTIFY_WEBHOOK_SECRET, "").trim();
        if (StringUtils.hasText(secret)) {
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            payload.put("timestamp", ts);
            payload.put("sign", feishuSign(ts, secret));
        }
        postJson(webhook, payload, "feishu_bot");
    }

    private void sendWecom(String text) throws Exception {
        String webhook = config.getOrDefault(SysConfigService.NOTIFY_WEBHOOK_URL, "").trim();
        if (!StringUtils.hasText(webhook)) {
            log.warn("[通知] wecom 未配置 webhook.url，跳过");
            return;
        }
        postJson(webhook, wecomText(text), "wecom");
    }

    private void postJson(String url, Map<String, Object> payload, String channel) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.warn("[通知] webhook HTTP {} channel={} body={}", resp.statusCode(), channel, trunc(resp.body()));
        } else {
            log.info("[通知] 已推送 channel={}", channel);
        }
    }

    private static Map<String, Object> feishuBotText(String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", text);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msg_type", "text");
        root.put("content", content);
        return root;
    }

    private static Map<String, Object> wecomText(String text) {
        Map<String, Object> textObj = new LinkedHashMap<>();
        textObj.put("content", text);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msgtype", "text");
        root.put("text", textObj);
        return root;
    }

    private static String feishuSign(String timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[]{}));
    }

    private static String nullToDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }

    private static String trimName(String fileName) {
        String name = fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.length() > 20 ? name.substring(0, 20) + "..." : name;
    }

    private static String trunc(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private record ResolvedDrama(String key, String title) {
    }

    private record PendingUpdate(String title, String episode) {
    }
}
