-- 季摘要（TMDB /tv/{id} seasons[]；一部剧多季）
CREATE TABLE IF NOT EXISTS `media_season` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `media_id`        BIGINT       NOT NULL                COMMENT '所属 media',
  `season_number`   INT          NOT NULL                COMMENT '季号，从 1 起',
  `tmdb_season_id`  INT          DEFAULT NULL            COMMENT 'TMDB 季 id',
  `name`            VARCHAR(255) DEFAULT NULL            COMMENT '季名称',
  `episode_count`   INT          DEFAULT NULL            COMMENT '该季集数',
  `air_date`        VARCHAR(20)  DEFAULT NULL            COMMENT '该季首播日',
  `poster`          VARCHAR(512) DEFAULT NULL            COMMENT '季海报 URL',
  `overview`        TEXT                                 COMMENT '季简介',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_season` (`media_id`, `season_number`),
  KEY `idx_media_id` (`media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体季摘要';
