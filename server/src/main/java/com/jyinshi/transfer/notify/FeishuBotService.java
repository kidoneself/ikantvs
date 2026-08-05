package com.jyinshi.transfer.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 飞书口令机器人：用户发「今日」→ 回复今日更新列表（✔ 剧名 → 集数）。
 */
@Slf4j
@Service
public class FeishuBotService {

    private final TodayUpdateQuery todayUpdateQuery;
    private final WebhookNotifyPort notifyPort;

    public FeishuBotService(TodayUpdateQuery todayUpdateQuery, WebhookNotifyPort notifyPort) {
        this.todayUpdateQuery = todayUpdateQuery;
        this.notifyPort = notifyPort;
    }

    public void handleUserMessage(String senderId, String chatId, String messageId, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String cmd = text.trim();
        // 去掉可能残留的 @ 机器人显示名
        cmd = cmd.replaceAll("@\\S+\\s*", "").trim();
        String reply;
        if ("今日".equals(cmd) || "today".equalsIgnoreCase(cmd) || "更新".equals(cmd)) {
            reply = handleToday();
        } else if ("帮助".equals(cmd) || "help".equalsIgnoreCase(cmd) || "？".equals(cmd) || "?".equals(cmd)) {
            reply = handleHelp();
        } else {
            // 未知口令：给简短提示，避免误触刷屏长文
            reply = "可用口令：今日 / 帮助";
        }
        try {
            if (StringUtils.hasText(messageId)) {
                notifyPort.replyMessage(messageId, reply);
            } else {
                notifyPort.sendToDefaultUser(reply);
            }
        } catch (Exception e) {
            log.warn("[飞书机器人] 回复失败 sender={} cmd={}: {}", senderId, cmd, e.getMessage());
        }
    }

    private String handleToday() {
        List<UpdateNotifyTexts.Item> items = todayUpdateQuery.listToday();
        if (items.isEmpty()) {
            return "📭 今天暂无更新";
        }
        return UpdateNotifyTexts.broadcast(items);
    }

    private static String handleHelp() {
        return "📖 可用口令\n"
                + "• 今日 - 查看今日更新（最新在上）\n"
                + "• 帮助 - 查看本说明\n\n"
                + "剧有新更时会自动推送同样格式的通知。";
    }
}
