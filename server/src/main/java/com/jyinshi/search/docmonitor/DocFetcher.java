package com.jyinshi.search.docmonitor;

import com.jyinshi.search.entity.DocMonitorTask;

/**
 * 文档来源抓取器（可插拔）。每种平台（flowus / kdocs / …）一个实现，
 * Spring 自动注册进 {@link DocFetcherRegistry}；新增平台 = 加一个 {@code @Component}。
 *
 * <p>抓取只负责拉正文并拆成 {@link ContentLine}；剧目聚合一律走任务上的 {@link ParseRules}。
 */
public interface DocFetcher {

    String source();

    /**
     * 监控调度：允许指纹短路（未变则不下载正文）。
     */
    FetchResult fetch(DocMonitorTask task, ParseRules rules);

    /**
     * 预览 / 搜索刷新：始终拉完整内容。
     */
    default FetchResult fetchFull(DocMonitorTask task, ParseRules rules) {
        return fetch(task, rules);
    }
}
