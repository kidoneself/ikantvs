-- 合并 transfer-worker 进主站：放弃「海外主站 + 国内 worker 分离」，网盘操作改在主站进程内执行。
-- worker 节点心跳表不再需要（不再有独立 worker 上报心跳/被 push-to-wake）。
DROP TABLE IF EXISTS transfer_worker;

-- 说明：transfer_account.worker_id 列保留（历史行归属标记，合并后统一逻辑节点 'local'，
-- 代码已不按 worker_id 过滤）。transfer_job.worker_id 同样保留仅作执行记录，不再有跨机含义。
