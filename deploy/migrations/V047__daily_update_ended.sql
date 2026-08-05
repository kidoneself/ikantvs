-- 每日更新：剧级「完结」标记（一部剧一次，不按盘拆）。
-- 完结后停追更巡检，保留我方链；换号/号满时完结剧不迁，只迁未完结。
ALTER TABLE `daily_update`
  ADD COLUMN `ended` TINYINT NOT NULL DEFAULT 0 COMMENT '1=已完结(停追更,换号不迁) 0=追更中' AFTER `manual_episode`;
