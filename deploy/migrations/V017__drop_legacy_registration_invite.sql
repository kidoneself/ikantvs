-- 清理旧注册/邀请体系；套餐参考价并入 plan 表

ALTER TABLE `plan`
  ADD COLUMN `price_hint` VARCHAR(16) DEFAULT NULL COMMENT '前台展示参考价(元)' AFTER `days`;

UPDATE `plan` SET `price_hint` = '15' WHERE `slug` = 'month';
UPDATE `plan` SET `price_hint` = '40' WHERE `slug` = 'quarter';
UPDATE `plan` SET `price_hint` = '128' WHERE `slug` = 'year';

DELETE FROM `sys_config` WHERE `config_key` IN (
  'registration.mode',
  'plan.price.month',
  'plan.price.quarter',
  'plan.price.year'
);

ALTER TABLE `user` DROP INDEX `uk_invite_code`;
ALTER TABLE `user` DROP INDEX `idx_invited_by`;
ALTER TABLE `user`
  DROP COLUMN `invite_code`,
  DROP COLUMN `invited_by`;
