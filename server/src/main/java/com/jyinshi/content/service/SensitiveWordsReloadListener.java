package com.jyinshi.content.service;

import com.jyinshi.common.event.SensitiveWordsReloadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 敏感词变更后清前台搜索缓存，避免旧结果继续展示已应隐藏的内容。 */
@Component
class SensitiveWordsReloadListener {

    private final MediaSearchCacheService searchCacheService;

    SensitiveWordsReloadListener(MediaSearchCacheService searchCacheService) {
        this.searchCacheService = searchCacheService;
    }

    @EventListener
    void onSensitiveWordsReloaded(SensitiveWordsReloadedEvent event) {
        searchCacheService.invalidateAll();
    }
}
