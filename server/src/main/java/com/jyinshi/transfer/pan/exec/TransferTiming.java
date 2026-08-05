package com.jyinshi.transfer.pan.exec;

import lombok.extern.slf4j.Slf4j;

/** 转存链路耗时日志工具。 */
@Slf4j
public final class TransferTiming {

    private TransferTiming() {
    }

    public static long msSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    /** AgentRunner 任务级汇总（含 report 往返）。 */
    public static void logJob(long jobId, String jobType, String panType, String account,
                              long execMs, long reportMs, boolean ok, String err) {
        long total = execMs + reportMs;
        if (err != null && !err.isBlank()) {
            log.info("[转存耗时] job={} jobType={} pan={} account={} exec={}ms report={}ms total={}ms ok={} err={}",
                    jobId, jobType, panType, account, execMs, reportMs, total, ok, err);
        } else {
            log.info("[转存耗时] job={} jobType={} pan={} account={} exec={}ms report={}ms total={}ms ok={}",
                    jobId, jobType, panType, account, execMs, reportMs, total, ok);
        }
    }
}
