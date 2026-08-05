-- 扩展行为事件，支持搜索词/标签/数值，media_id 改为可空（搜索词事件无 media）。
ALTER TABLE `content_event`
  MODIFY COLUMN `media_id` BIGINT NULL COMMENT '关联 media.id（view/favorite/link_click）',
  ADD COLUMN `keyword` VARCHAR(128) NULL COMMENT '搜索词（search）' AFTER `event_type`,
  ADD COLUMN `tag`     VARCHAR(32)  NULL COMMENT '附加标签，如网盘类型（link_click）' AFTER `keyword`,
  ADD COLUMN `num`     INT          NULL COMMENT '数值，如搜索结果数（search）' AFTER `tag`,
  ADD KEY `idx_event_keyword` (`event_type`, `keyword`);
