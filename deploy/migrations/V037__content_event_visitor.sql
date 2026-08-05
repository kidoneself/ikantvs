-- 埋点去重：为匿名访客补 visitor_id（前端 localStorage 生成的 UUID）。
-- view 事件按 (visitor_id, media_id, 天) 在服务端用 Redis 去重，刷新不再刷量；
-- 此列用于留存/独立访客等后续分析，索引按访客+时间。
ALTER TABLE `content_event`
  ADD COLUMN `visitor_id` VARCHAR(36) NULL COMMENT '匿名访客标识（前端 localStorage UUID）' AFTER `event_type`,
  ADD KEY `idx_visitor_time` (`visitor_id`, `created_at`);
