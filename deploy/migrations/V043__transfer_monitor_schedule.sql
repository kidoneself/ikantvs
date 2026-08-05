-- 追更看板对齐运营诉求：区分「上次检查」与「上次真正更新」，并支持每部剧单独设更新节奏。
-- - last_content_at：只在真的补到新集数时刷新（区别于 last_probe_at 每次巡检都变的检查时间）。
-- - check_days/check_hours/check_interval：每剧自定义追更节奏（"到点了才查"），为空则沿用全局巡检时段/间隔。
ALTER TABLE `transfer_monitor`
  ADD COLUMN `last_content_at` DATETIME NULL
    COMMENT '最近一次补到新集数的时间(真正更新,区别于 last_probe_at 检查时间)' AFTER `last_probe_at`,
  ADD COLUMN `check_days` VARCHAR(20) NULL
    COMMENT '每剧检查日:0-6对应周日-周六,逗号分隔;空=每天' AFTER `last_content_at`,
  ADD COLUMN `check_hours` VARCHAR(20) NULL
    COMMENT '每剧检查时段:如 18-23(止不含);空=用全局巡检时段' AFTER `check_days`,
  ADD COLUMN `check_interval` INT NULL
    COMMENT '每剧检查间隔(分钟);空=用全局巡检间隔' AFTER `check_hours`;
