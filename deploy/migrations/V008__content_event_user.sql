-- 行为事件关联用户：匿名为空，登录则带 user_id，用于人均行为/付费分析。
ALTER TABLE `content_event`
  ADD COLUMN `user_id` BIGINT NULL COMMENT '关联 user.id，匿名为空' AFTER `media_id`,
  ADD KEY `idx_user_time` (`user_id`, `created_at`);
