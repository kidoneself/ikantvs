-- 每日更新：运营可手动指定/纠正"更新至第 X 集/日期"。
-- 展示值 = 手动值与自动聚合值取更靠后者（只增不减、保护手动纠正）。
ALTER TABLE `daily_update`
  ADD COLUMN `manual_episode` VARCHAR(32) NULL COMMENT '运营手动填写的最新集数/日期，展示时与自动值取较新' AFTER `enabled`;
