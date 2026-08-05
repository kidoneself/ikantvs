-- transfer 域：批量清理对账。
-- 清理改为「每 N 分钟攒一批、同网盘合并成一个 delete 任务一起删」，
-- 用 delete_job_id 把这批记录和那个 delete 任务绑定；任务回报后按 job 批量回写状态。

ALTER TABLE `transfer_record`
  ADD COLUMN `delete_job_id` BIGINT DEFAULT NULL
    COMMENT '批量清理任务 id（对账用：delete 任务回报后按此回写 deleted/delete_failed）'
    AFTER `folder_id`;

ALTER TABLE `transfer_record`
  ADD KEY `idx_delete_job` (`delete_job_id`);
