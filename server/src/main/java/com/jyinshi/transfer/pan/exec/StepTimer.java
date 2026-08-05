package com.jyinshi.transfer.pan.exec;

import lombok.extern.slf4j.Slf4j;

/**
 * 转存步骤耗时打点：每步 {@link #step(String)} 记录距上一步毫秒数，最后 {@link #logDone(boolean, String)} 一行输出。
 * 日志前缀统一 {@code [转存耗时]}，便于 grep 分析。
 */
@Slf4j
public final class StepTimer {

    private final String tag;
    private final long start = System.nanoTime();
    private long mark = start;
    private final StringBuilder steps = new StringBuilder();

    private StepTimer(String tag) {
        this.tag = tag;
    }

    public static StepTimer of(String tag) {
        return new StepTimer(tag);
    }

    /** 记录上一步到当前的耗时。 */
    public void step(String name) {
        long now = System.nanoTime();
        long ms = (now - mark) / 1_000_000;
        if (!steps.isEmpty()) {
            steps.append(' ');
        }
        steps.append(name).append('=').append(ms).append("ms");
        mark = now;
    }

    public void logDone(boolean ok, String extra) {
        long total = (System.nanoTime() - start) / 1_000_000;
        if (extra != null && !extra.isBlank()) {
            log.info("[转存耗时] {} total={}ms ok={} steps=[{}] {}", tag, total, ok, steps, extra);
        } else {
            log.info("[转存耗时] {} total={}ms ok={} steps=[{}]", tag, total, ok, steps);
        }
    }
}
