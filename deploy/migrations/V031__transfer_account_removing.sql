-- 账号封禁/弃用后的「待移除」标记：
-- 后台点删除时置 1，主站心跳把待移除清单下发给对应 worker，
-- worker 从 accounts.json 删掉后，下次心跳不再上报该账号，主站据此清除镜像行。
ALTER TABLE `transfer_account`
  ADD COLUMN `removing` TINYINT(1) NOT NULL DEFAULT 0
      COMMENT '待移除：后台已请求删除，等 worker 落地后清行' AFTER `healthy`;
