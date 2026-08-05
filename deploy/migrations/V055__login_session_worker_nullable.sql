-- 合并 worker 后加号会话不再指定 worker；代码 insert 不写 worker_id。
-- 旧表仍是 NOT NULL，会导致 POST /accounts/cookie 报：
-- Field 'worker_id' doesn't have a default value
ALTER TABLE `transfer_login_session`
  MODIFY COLUMN `worker_id` VARCHAR(64) DEFAULT NULL
    COMMENT '历史字段，合并后可空，单机不再指定 worker';
