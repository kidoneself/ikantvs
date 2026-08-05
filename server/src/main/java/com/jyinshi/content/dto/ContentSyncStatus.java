package com.jyinshi.content.dto;

import lombok.Data;

/**
 * 内容同步进度快照（content 域）：供后台轮询展示当前拉新/刷新/重建榜单的实时进度。
 */
@Data
public class ContentSyncStatus {

    /** 当前/最近任务标识：discover / refresh / rankings / startup / idle。 */
    private String task;

    /** 任务中文名，直接展示用。 */
    private String taskLabel;

    /** 是否有任务正在执行。 */
    private boolean running;

    /** 当前阶段文案（如「采集入库」「刷新连载」）。 */
    private String phase;

    /** 本次待处理总数（拉新为候选数、刷新为连载数、榜单为 3）。0 表示尚未确定（如正在拉候选）。 */
    private int total;

    /** 已处理数。 */
    private int processed;

    /** 实际生效数：拉新为新增入库、刷新为成功刷新、榜单为已重建。 */
    private int affected;

    /** 上一次/本次结束的结果摘要。 */
    private String result;

    /** 出错信息（无错为空）。 */
    private String error;

    /** 开始时间（epoch millis），未开始为 null。 */
    private Long startedAt;

    /** 结束时间（epoch millis），运行中为 null。 */
    private Long finishedAt;
}
