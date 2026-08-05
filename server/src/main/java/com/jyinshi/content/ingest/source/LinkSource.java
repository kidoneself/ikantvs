package com.jyinshi.content.ingest.source;

import java.util.List;

/**
 * 资源来源插件契约（可插拔）。
 *
 * <p>每个来源（pansou、观影、文档站、其它爬虫……）实现一个 {@code LinkSource}，
 * 由 Spring 自动装配成 bean 列表交给 {@code IngestService} 统一编排。
 * <b>新增来源 = 加一个实现类，不动其它任何代码。</b>
 *
 * <p>职责单一：给定关键词，产出一批规范化候选 {@link RawLink}。
 * 不做归属识别、不做去重、不入库、不判活死——那些是下游各阶段的事。
 */
public interface LinkSource {

    /** 来源短标识，落到 {@code media_link.source}（如 pansou/gying）。全局唯一。 */
    String sourceName();

    /** 是否启用（读各自开关）。关闭时编排层直接跳过，不调用 {@link #search}。 */
    boolean isEnabled();

    /**
     * 按关键词搜候选链接。实现须自吞异常、超时可控，失败返回空列表而非抛出，
     * 避免拖垮多源编排。
     *
     * @param keyword 搜索词（通常是剧名）
     * @return 规范化候选（可能为空，不为 null）
     */
    List<RawLink> search(String keyword);
}
