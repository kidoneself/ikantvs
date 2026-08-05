-- transfer 域：账号绑定。
-- 多账号下必须记住「用哪个号转的」，追更(sync)/删除(delete)才能用回同一个号——
-- 否则会拿 B 号去操作 A 号的落地夹，导致追更进错夹、删除删不掉。
-- account_name 语义：worker 侧账号名(worker.accounts[].name)；为空表示由账号池按权重选(首转/新任务)。

ALTER TABLE `transfer_job`
  ADD COLUMN `account_name` VARCHAR(64) DEFAULT NULL
    COMMENT '指定执行账号(sync/delete 用回首转的号;first_save/probe 为空=池选)' AFTER `pan_type`;

ALTER TABLE `transfer_record`
  ADD COLUMN `account_name` VARCHAR(64) DEFAULT NULL
    COMMENT '首转用的账号名(清理删除时用回同一个号)' AFTER `pan_type`;

ALTER TABLE `transfer_monitor`
  ADD COLUMN `account_name` VARCHAR(64) DEFAULT NULL
    COMMENT '追更固定用的账号名(首转成功后回填)' AFTER `pan_type`;
