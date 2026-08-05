-- content 域：网盘链接（与 media 分离；付费墙在接口层控制是否返回 url）
CREATE TABLE IF NOT EXISTS `media_link` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `media_id`        BIGINT       NOT NULL,
  `pan_type`        VARCHAR(16)  NOT NULL                COMMENT 'quark/baidu/aliyun/xunlei/uc/magnet',
  `url`             VARCHAR(1024) NOT NULL                COMMENT '完整分享链接（含提取码）',
  `note`            VARCHAR(255) DEFAULT NULL              COMMENT '展示标题/备注',
  `source`          VARCHAR(16)  NOT NULL DEFAULT 'manual' COMMENT 'manual/pansou/crawl',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'approved' COMMENT 'pending/approved/rejected',
  `contributor_id`  BIGINT       DEFAULT NULL,
  `invalid`         TINYINT      NOT NULL DEFAULT 0,
  `report_count`    INT          NOT NULL DEFAULT 0,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_status` (`media_id`, `status`),
  KEY `idx_pan` (`pan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘链接';
