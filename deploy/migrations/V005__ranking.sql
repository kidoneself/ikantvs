-- 榜单（content 域）：策划榜单 + 榜单条目
CREATE TABLE IF NOT EXISTS `ranking` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)  NOT NULL                COMMENT '榜单名，如 本周热门',
  `slug`        VARCHAR(64)  NOT NULL                COMMENT '前台标识',
  `description` VARCHAR(255) DEFAULT NULL            COMMENT '副标题/说明',
  `sort`        INT          NOT NULL DEFAULT 0      COMMENT '榜单间展示顺序，大在前',
  `enabled`     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 上架 0 下架',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='榜单';

CREATE TABLE IF NOT EXISTS `ranking_item` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `ranking_id` BIGINT   NOT NULL                COMMENT '所属榜单',
  `media_id`   BIGINT   NOT NULL                COMMENT '影视条目',
  `rank_no`    INT      NOT NULL DEFAULT 0      COMMENT '榜内名次，小在前',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_media` (`ranking_id`, `media_id`),
  KEY `idx_ranking_rank` (`ranking_id`, `rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='榜单条目';
