-- 账号信息（昵称/uid/空间）镜像列：由 worker 心跳低频上报，仅展示用。
-- 空间单位字节，-1/NULL 表示未知；剩余空间 = total - used 由前端算。
ALTER TABLE `transfer_account`
  ADD COLUMN `nickname` VARCHAR(128) NULL COMMENT '网盘昵称' AFTER `account_name`,
  ADD COLUMN `uid` VARCHAR(64) NULL COMMENT '网盘用户 id' AFTER `nickname`,
  ADD COLUMN `total_space` BIGINT NULL COMMENT '总空间(字节)' AFTER `uid`,
  ADD COLUMN `used_space` BIGINT NULL COMMENT '已用空间(字节)' AFTER `total_space`;
