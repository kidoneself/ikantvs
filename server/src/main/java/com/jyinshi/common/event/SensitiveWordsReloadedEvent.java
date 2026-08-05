package com.jyinshi.common.event;

/** 敏感词内存词库已重建（增删改/import 后）。content 域可据此清搜索缓存等。 */
public record SensitiveWordsReloadedEvent() {
}
