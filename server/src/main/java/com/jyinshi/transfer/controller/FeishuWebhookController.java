package com.jyinshi.transfer.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyinshi.transfer.notify.FeishuBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 飞书事件回调：用户发口令「今日」→ 回复更新列表。
 *
 * <p>飞书开放平台事件订阅 URL 配：{@code https://api.naspt.vip/api/feishu/webhook}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/feishu")
public class FeishuWebhookController {

    private final FeishuBotService botService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "feishu-webhook");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public FeishuWebhookController(FeishuBotService botService, ObjectMapper objectMapper) {
        this.botService = botService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public Map<String, Object> handleWebhook(@RequestBody String requestBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(requestBody, new TypeReference<>() {
            });

            // URL 验证（配置事件订阅时）
            if ("url_verification".equals(body.get("type"))) {
                Object challenge = body.get("challenge");
                log.info("[飞书Webhook] URL验证 challenge={}", challenge);
                return Map.of("challenge", challenge == null ? "" : challenge);
            }

            // Schema 2.0
            if ("2.0".equals(String.valueOf(body.get("schema")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> header = (Map<String, Object>) body.get("header");
                String eventId = header != null ? String.valueOf(header.get("event_id")) : null;
                if (eventId != null && !"null".equals(eventId) && !processedEventIds.add(eventId)) {
                    log.info("[飞书Webhook] 重复事件跳过 eventId={}", eventId);
                    return Map.of();
                }
                if (processedEventIds.size() > 1000) {
                    processedEventIds.clear();
                }
                executor.execute(() -> handleEventSafe(body));
                return Map.of();
            }

            // Schema 1.0
            if ("event_callback".equals(body.get("type"))) {
                executor.execute(() -> handleEventSafe(body));
                return Map.of("code", 0, "msg", "success");
            }

            log.warn("[飞书Webhook] 未知格式 keys={}", body.keySet());
            return Map.of();
        } catch (Exception e) {
            log.error("[飞书Webhook] 处理失败: {}", e.getMessage());
            return Map.of("code", -1, "msg", e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "feishu-webhook");
    }

    @SuppressWarnings("unchecked")
    private void handleEventSafe(Map<String, Object> body) {
        try {
            Map<String, Object> header = (Map<String, Object>) body.get("header");
            String eventType = header != null ? String.valueOf(header.get("event_type")) : null;
            if (!"im.message.receive_v1".equals(eventType)) {
                log.debug("[飞书Webhook] 忽略事件类型 {}", eventType);
                return;
            }
            Map<String, Object> event = (Map<String, Object>) body.get("event");
            if (event == null) {
                return;
            }
            Map<String, Object> sender = (Map<String, Object>) event.get("sender");
            Map<String, Object> message = (Map<String, Object>) event.get("message");
            if (message == null) {
                return;
            }
            String messageType = String.valueOf(message.get("message_type"));
            if (!"text".equals(messageType)) {
                return;
            }
            String messageId = (String) message.get("message_id");
            String chatId = (String) message.get("chat_id");
            String contentJson = (String) message.get("content");
            Map<String, Object> contentMap = objectMapper.readValue(contentJson, new TypeReference<>() {
            });
            String text = contentMap.get("text") == null ? "" : String.valueOf(contentMap.get("text"));
            text = text.replaceAll("@_user_\\d+\\s*", "").trim();

            String senderId = null;
            if (sender != null) {
                Map<String, Object> senderIdMap = (Map<String, Object>) sender.get("sender_id");
                if (senderIdMap != null) {
                    senderId = (String) senderIdMap.get("open_id");
                    if (senderId == null || senderId.isEmpty()) {
                        senderId = (String) senderIdMap.get("user_id");
                    }
                }
            }
            log.info("[飞书Webhook] 收到口令 sender={} text={}", senderId, text);
            botService.handleUserMessage(senderId, chatId, messageId, text);
        } catch (Exception e) {
            log.error("[飞书Webhook] 事件处理失败: {}", e.getMessage());
        }
    }
}
