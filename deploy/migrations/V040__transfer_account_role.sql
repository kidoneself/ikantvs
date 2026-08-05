-- 账号分工：区分「用户转存号」与「每日更新监控号」，同一 worker 内隔离两类账号。
-- transfer=用户点击转存用的号池；monitor=每日更新追更专用号(运营在录入时按名指定)。
-- 旧数据默认 transfer，不影响存量用户转存。
ALTER TABLE `transfer_account`
  ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'transfer'
  COMMENT 'transfer=用户转存号 monitor=每日更新监控号' AFTER `account_name`;
