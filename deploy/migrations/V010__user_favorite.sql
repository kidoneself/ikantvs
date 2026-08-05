-- 用户收藏（identity 域）：仅收藏，不含追剧
CREATE TABLE IF NOT EXISTS `user_favorite` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL                COMMENT '用户 id',
  `media_id`   BIGINT   NOT NULL                COMMENT 'media.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_media` (`user_id`, `media_id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏';
