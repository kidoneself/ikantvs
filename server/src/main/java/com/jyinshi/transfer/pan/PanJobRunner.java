package com.jyinshi.transfer.pan;

import cn.hutool.json.JSONUtil;
import com.jyinshi.transfer.dto.WorkerJobView;
import com.jyinshi.transfer.dto.WorkerReportRequest;
import com.jyinshi.transfer.event.JobEnqueuedEvent;
import com.jyinshi.transfer.pan.driver.DriverRegistry;
import com.jyinshi.transfer.pan.driver.PanType;
import com.jyinshi.transfer.pan.driver.SaveResult;
import com.jyinshi.transfer.pan.driver.ShareInfo;
import com.jyinshi.transfer.pan.exec.JobExecutor;
import com.jyinshi.transfer.pan.exec.SyncResult;
import com.jyinshi.transfer.pan.exec.TransferTiming;
import com.jyinshi.transfer.service.TransferJobService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 进程内任务执行循环：直接从 {@link TransferJobService} 领任务 → 调 {@link JobExecutor} 执行
 * → 回报结果。替代了原独立 worker 的 agent 拉模式（HTTP claim/report）。
 *
 * <p>合并后主站与执行同进程，无网络往返；仍走「领一个执行一个」的并发限流（execParallelism），
 * 队列 lease 机制保留：进程崩溃后未完成任务由 {@code reapExpiredLeases} 回队重试。</p>
 *
 * <p><b>入队即唤醒</b>：不再靠定时轮询发现新任务——{@link JobEnqueuedEvent} 一到（enqueue 提交后）
 * 立刻 {@link #drain()} 领取执行，去掉最长一个轮询周期的启动延迟；执行完释放槽位也顺手再 drain 一次，
 * 让排队任务无缝接上。{@link #poll()} 定时轮询降级为安全网：只兜底「退避重试到点」和「租约回收回到
 * pending」这类没有入队事件的任务。</p>
 */
@Slf4j
@Component
public class PanJobRunner {

    private final PanWorkerProperties props;
    private final TransferJobService jobService;
    private final JobExecutor executor;
    private final DriverRegistry drivers;

    /** 并行执行池 + 空闲槽计数：只按空闲槽领任务，不过量 claim，天然限流。 */
    private ExecutorService pool;
    private Semaphore slots;

    public PanJobRunner(PanWorkerProperties props, TransferJobService jobService,
                        JobExecutor executor, DriverRegistry drivers) {
        this.props = props;
        this.jobService = jobService;
        this.executor = executor;
        this.drivers = drivers;
    }

    @PostConstruct
    void init() {
        int n = Math.max(1, props.getExecParallelism());
        this.pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "pan-job-exec");
            t.setDaemon(true);
            return t;
        });
        this.slots = new Semaphore(n);
        log.info("[执行器] 进程内执行已启用: 并行度={}, 支持网盘={}", n, supportedTypes());
    }

    @PreDestroy
    void shutdown() {
        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 入队即唤醒：任务入队提交后触发，立刻领取执行，免去等下一个轮询周期。
     * {@code AFTER_COMMIT} 确保在 enqueue 事务提交后才领（否则 claim 看不到新行）；
     * {@code fallbackExecution=true} 兼容无事务场景（此时 enqueue 已自动提交）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onJobEnqueued(JobEnqueuedEvent evt) {
        drain();
    }

    /** 安全网轮询：只兜底退避重试到点、租约回收回 pending 这类无入队事件的任务。 */
    @Scheduled(fixedDelayString = "${jyinshi.transfer.pan.poll-interval-ms:1500}")
    public void poll() {
        drain();
    }

    /**
     * 领取并派发：有几个空闲槽就领几个任务丢进池里并发执行；领不到或满负荷即停。
     * 唤醒事件、安全网轮询、执行完释放槽位后都调它，多线程并发调用安全（tryAcquire 原子、
     * claim 走 {@code FOR UPDATE SKIP LOCKED}）。
     */
    private void drain() {
        List<String> types = supportedTypes();
        if (types.isEmpty()) {
            return;
        }
        while (slots.tryAcquire()) {
            WorkerJobView job;
            try {
                job = jobService.claim(props.getWorkerId(), types);
            } catch (Exception e) {
                slots.release();
                log.warn("[执行器] 领取任务异常: {}", e.getMessage());
                break;
            }
            if (job == null) {
                slots.release();
                break;
            }
            pool.submit(() -> {
                try {
                    handle(job);
                } finally {
                    slots.release();
                    // 释放槽位后顺手再领：排队任务无缝接上，不必等下一次唤醒/轮询
                    drain();
                }
            });
        }
    }

    // ==================== 执行 ====================

    private void handle(WorkerJobView job) {
        PanType type = PanType.of(job.getPanType());
        String jobType = job.getJobType() != null ? job.getJobType() : "transfer";
        if (type == null) {
            timedReport(job.getId(), jobType, job.getPanType(), null, System.nanoTime(), false, null, null,
                    "未知网盘类型: " + job.getPanType(), null);
            return;
        }
        long execStart = System.nanoTime();
        try {
            switch (jobType) {
                case "check" -> {
                    ShareInfo si = executor.probe(type, job.getShareUrl(), job.getSharePwd());
                    boolean success = si != null && si.isOk();
                    timedReport(job.getId(), jobType, job.getPanType(), job.getAccountName(),
                            execStart, success, JSONUtil.toJsonStr(si), null,
                            success ? null : (si != null ? si.getMessage() : "check 无结果"), null);
                }
                case "update" -> {
                    SyncResult sr = executor.sync(type, job.getShareUrl(), job.getSharePwd(),
                            job.getTargetFolderId(), job.getAccountName());
                    timedReport(job.getId(), jobType, job.getPanType(), job.getAccountName(),
                            execStart, sr.isSuccess(), JSONUtil.toJsonStr(sr), job.getTargetFolderId(),
                            sr.isSuccess() ? null : sr.getMessage(), null);
                }
                case "delete" -> {
                    List<String> ids = parseDeleteIds(job);
                    int deleted = ids.isEmpty() ? 0 : executor.delete(type, ids, job.getAccountName());
                    boolean ok = deleted >= ids.size() && deleted > 0;
                    timedReport(job.getId(), jobType, job.getPanType(), job.getAccountName(),
                            execStart, ok,
                            "{\"deleted\":" + deleted + ",\"total\":" + ids.size() + "}", null,
                            ok ? null : "删除失败/部分失败: " + deleted + "/" + ids.size(), null);
                }
                case "create", "transfer" -> {
                    // create=监控创建（回填最新集数）；transfer=用户转存（不额外列夹，保低延迟）
                    boolean collectLatest = "create".equals(jobType);
                    SaveResult sr = executor.save(type, job.getShareUrl(), job.getSharePwd(),
                            job.getTargetFolderId(), job.getAccountName(), job.getLandingDir(), collectLatest);
                    timedReport(job.getId(), jobType, job.getPanType(), sr.getAccountName(),
                            execStart, sr.isSuccess(), JSONUtil.toJsonStr(sr), sr.getSavedFolderId(),
                            sr.isSuccess() ? null : sr.getErrorMessage(),
                            sr.isSuccess() ? sr.getMyShareUrl() : null);
                }
                default -> timedReport(job.getId(), jobType, job.getPanType(), job.getAccountName(),
                        execStart, false, null, null, "未知任务类型: " + jobType, null);
            }
        } catch (Exception e) {
            log.error("[执行器] 执行任务 {} 异常", job.getId(), e);
            timedReport(job.getId(), jobType, job.getPanType(), job.getAccountName(),
                    execStart, false, null, null, "执行异常: " + e.getMessage(), null);
        }
    }

    private void timedReport(Long jobId, String jobType, String panType, String account,
                             long execStart, boolean success, String resultJson,
                             String resultFolderId, String errorMsg, String resultShareUrl) {
        long execMs = TransferTiming.msSince(execStart);
        long reportStart = System.nanoTime();
        WorkerReportRequest req = new WorkerReportRequest();
        req.setJobId(jobId);
        req.setWorkerId(props.getWorkerId());
        req.setSuccess(success);
        req.setResultJson(resultJson);
        req.setResultShareUrl(resultShareUrl);
        req.setResultFolderId(resultFolderId);
        req.setErrorMsg(errorMsg);
        try {
            jobService.report(req);
        } catch (Exception e) {
            log.warn("[执行器] 回报任务 {} 失败: {}", jobId, e.getMessage());
        }
        TransferTiming.logJob(jobId, jobType, panType, account, execMs,
                TransferTiming.msSince(reportStart), success, errorMsg);
    }

    // ==================== 辅助 ====================

    /** delete 任务的待删 id 列表：优先 resultJson.ids（批量），否则回退 targetFolderId 单个。 */
    private List<String> parseDeleteIds(WorkerJobView job) {
        List<String> ids = new ArrayList<>();
        String rj = job.getResultJson();
        if (rj != null && !rj.isBlank()) {
            try {
                cn.hutool.json.JSONArray arr = JSONUtil.parseObj(rj).getJSONArray("ids");
                if (arr != null) {
                    for (Object o : arr) {
                        if (o != null && !o.toString().isBlank()) {
                            ids.add(o.toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[执行器] 解析 delete ids 失败 job={}: {}", job.getId(), e.getMessage());
            }
        }
        if (ids.isEmpty() && job.getTargetFolderId() != null && !job.getTargetFolderId().isBlank()) {
            ids.add(job.getTargetFolderId());
        }
        return ids;
    }

    /** 本机已装载的网盘类型（小写），据此向队列领任务。 */
    private List<String> supportedTypes() {
        List<String> types = new ArrayList<>();
        for (PanType t : drivers.types()) {
            types.add(t.name().toLowerCase());
        }
        return types;
    }
}
