-- 每日更新（content 域 · 策展看板）。
-- 薄策展层：一条 = 一部剧(media_id) + 排序/置顶/上架开关。
-- 链接不落这张表：上游源链与追更状态挂 transfer 域(transfer_monitor)，
-- 我方稳定分享链、最新集数由 transfer 回写、查询时聚合展示。一部剧至多一条。
CREATE TABLE IF NOT EXISTS `daily_update` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `media_id`   BIGINT   NOT NULL                COMMENT '绑定的影视条目',
  `pinned`     TINYINT  NOT NULL DEFAULT 0      COMMENT '1 置顶',
  `sort`       INT      NOT NULL DEFAULT 0      COMMENT '展示顺序，大在前',
  `enabled`    TINYINT  NOT NULL DEFAULT 1      COMMENT '1 上架(前台可见) 0 下架',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media` (`media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日更新策展看板';
