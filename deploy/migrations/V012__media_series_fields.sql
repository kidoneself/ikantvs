-- 剧集连载字段（整部剧维度；季明细在 media_season）
ALTER TABLE `media`
  ADD COLUMN `season_count`         INT          DEFAULT NULL COMMENT '季数(不含 Specials)' AFTER `episode_count`,
  ADD COLUMN `series_status`        VARCHAR(32)  DEFAULT NULL COMMENT 'Ended/Returning Series 等' AFTER `season_count`,
  ADD COLUMN `in_production`        TINYINT(1)   DEFAULT NULL COMMENT '是否仍在制作/播出' AFTER `series_status`,
  ADD COLUMN `last_air_date`        VARCHAR(20)  DEFAULT NULL COMMENT '最近一集播出日' AFTER `in_production`,
  ADD COLUMN `last_season_number`   INT          DEFAULT NULL COMMENT '最近一集所在季' AFTER `last_air_date`,
  ADD COLUMN `last_episode_number`  INT          DEFAULT NULL COMMENT '最近一集集号' AFTER `last_season_number`;
