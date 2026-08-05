-- 站内行为事件（analytics 域）：详情浏览、搜索命中等，供热度聚合用。
-- 设计为只追加的事件流；热度由后续每日 job 按近 N 天聚合（带衰减）写回 media.hot。
CREATE TABLE IF NOT EXISTS `content_event` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `media_id`   BIGINT      NOT NULL                COMMENT '关联 media.id',
  `event_type` VARCHAR(16) NOT NULL                COMMENT 'view / search 等',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_time` (`media_id`, `created_at`),
  KEY `idx_type_time` (`event_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内行为事件';
