package com.jyinshi.transfer.controller;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.ops.service.SysConfigService;
import com.jyinshi.transfer.notify.TodayUpdateQuery;
import com.jyinshi.transfer.notify.UpdateNotifyTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机机口令数据源：微信机器人「今日」等拉取今日更新文案。
 *
 * <p>鉴权：{@code Authorization: Bearer <notify.wechat.token>} 或 {@code X-Api-Token}。</p>
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotifyTodayController {

    private final TodayUpdateQuery todayUpdateQuery;
    private final SysConfigService config;

    @GetMapping("/today")
    public Result<Map<String, Object>> today(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Api-Token", required = false) String apiToken) {
        if (!tokenOk(authorization, apiToken)) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        List<UpdateNotifyTexts.Item> items = todayUpdateQuery.listToday();
        String text = items.isEmpty() ? "📭 今天暂无更新" : UpdateNotifyTexts.broadcast(items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("count", items.size());
        return Result.success(data);
    }

    private boolean tokenOk(String authorization, String apiToken) {
        String expected = config.getOrDefault(SysConfigService.NOTIFY_WECHAT_TOKEN, "").trim();
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        if (StringUtils.hasText(apiToken) && expected.equals(apiToken.trim())) {
            return true;
        }
        if (StringUtils.hasText(authorization)) {
            String auth = authorization.trim();
            if (auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return expected.equals(auth.substring(7).trim());
            }
        }
        return false;
    }
}
