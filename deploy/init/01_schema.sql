-- jyinshi-next 最终基线（空库一键初始化用）
-- 对齐 migrations V001~V055 之后的当前表结构；不含会员/卡密/收藏等已废弃表。
-- 新装请用本目录；已有库继续在 ../migrations/ 追加 V056+，勿改历史文件。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(32)  NOT NULL                COMMENT '登录用户名',
  `password_hash` VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码',
  `nickname`      VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
  `avatar`        VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
  `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '0 正常 1 封禁',
  `role`          VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT 'contributor/reviewer/admin',
  `last_login_at` DATETIME     DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营账号';

CREATE TABLE IF NOT EXISTS `media` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
  `tmdb_id`              INT          DEFAULT NULL,
  `douban_id`            VARCHAR(20)  DEFAULT NULL,
  `douban_backfill_skip` TINYINT      NOT NULL DEFAULT 0,
  `bangumi_id`           INT          DEFAULT NULL,
  `type`                 VARCHAR(16)  NOT NULL DEFAULT 'movie',
  `title`                VARCHAR(255) NOT NULL,
  `original_title`       VARCHAR(255) DEFAULT NULL,
  `year`                 SMALLINT     DEFAULT NULL,
  `poster`               VARCHAR(512) DEFAULT NULL,
  `poster_thumb`         VARCHAR(512) DEFAULT NULL,
  `backdrop`             VARCHAR(512) DEFAULT NULL,
  `rating`               DECIMAL(3,1) DEFAULT NULL,
  `overview`             TEXT,
  `genres`               VARCHAR(255) DEFAULT NULL,
  `country`              VARCHAR(128) DEFAULT NULL,
  `actors`               VARCHAR(512) DEFAULT NULL,
  `directors`            VARCHAR(255) DEFAULT NULL,
  `release_date`         VARCHAR(20)  DEFAULT NULL,
  `episode_count`        INT          DEFAULT NULL,
  `season_count`         INT          DEFAULT NULL,
  `series_status`        VARCHAR(32)  DEFAULT NULL,
  `in_production`        TINYINT(1)   DEFAULT NULL,
  `last_air_date`        VARCHAR(20)  DEFAULT NULL,
  `last_season_number`   INT          DEFAULT NULL,
  `last_episode_number`  INT          DEFAULT NULL,
  `hot`                  INT          NOT NULL DEFAULT 0,
  `hot_seed`             INT          NOT NULL DEFAULT 0,
  `tier`                 TINYINT      NOT NULL DEFAULT 0,
  `meta_source`          VARCHAR(16)  NOT NULL DEFAULT 'manual',
  `pub_status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2下架',
  `search_hidden`        TINYINT      NOT NULL DEFAULT 0,
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`              TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tmdb` (`tmdb_id`, `type`),
  UNIQUE KEY `uk_douban` (`douban_id`),
  KEY `idx_type_status` (`type`, `pub_status`),
  KEY `idx_hot` (`hot`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体信息';

CREATE TABLE IF NOT EXISTS `media_season` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `media_id`       BIGINT       NOT NULL,
  `season_number`  INT          NOT NULL,
  `tmdb_season_id` INT          DEFAULT NULL,
  `name`           VARCHAR(255) DEFAULT NULL,
  `episode_count`  INT          DEFAULT NULL,
  `air_date`       VARCHAR(20)  DEFAULT NULL,
  `poster`         VARCHAR(512) DEFAULT NULL,
  `overview`       TEXT,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_season` (`media_id`, `season_number`),
  KEY `idx_media_id` (`media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体季摘要';

CREATE TABLE IF NOT EXISTS `media_link` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `media_id`       BIGINT        NOT NULL,
  `pan_type`       VARCHAR(16)   NOT NULL,
  `url`            VARCHAR(1024) NOT NULL,
  `share_id`       VARCHAR(64) CHARACTER SET ascii NOT NULL,
  `note`           VARCHAR(255)  DEFAULT NULL,
  `source`         VARCHAR(16)   NOT NULL DEFAULT 'manual',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'approved',
  `contributor_id` BIGINT        DEFAULT NULL,
  `invalid`        TINYINT       NOT NULL DEFAULT 0,
  `check_state`    VARCHAR(12)   DEFAULT NULL,
  `checked_at`     DATETIME      DEFAULT NULL,
  `check_summary`  VARCHAR(255)  DEFAULT NULL,
  `last_seen_at`   DATETIME      DEFAULT NULL,
  `report_count`   INT           NOT NULL DEFAULT 0,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_link` (`media_id`, `pan_type`, `share_id`),
  KEY `idx_media_status` (`media_id`, `status`),
  KEY `idx_pan` (`pan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘链接';

CREATE TABLE IF NOT EXISTS `ranking` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)  NOT NULL,
  `slug`        VARCHAR(64)  NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `enabled`     TINYINT      NOT NULL DEFAULT 1,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='榜单';

CREATE TABLE IF NOT EXISTS `ranking_item` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `ranking_id` BIGINT   NOT NULL,
  `media_id`   BIGINT   NOT NULL,
  `rank_no`    INT      NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_media` (`ranking_id`, `media_id`),
  KEY `idx_ranking_rank` (`ranking_id`, `rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='榜单条目';

CREATE TABLE IF NOT EXISTS `invalid_share` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `pan_type`   VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `share_id`   VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `error_code` VARCHAR(50)  DEFAULT NULL,
  `reason`     VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pan_share` (`pan_type`, `share_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失效分享黑名单';

CREATE TABLE IF NOT EXISTS `daily_update` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `media_id`       BIGINT      NOT NULL,
  `pinned`         TINYINT     NOT NULL DEFAULT 0,
  `sort`           INT         NOT NULL DEFAULT 0,
  `enabled`        TINYINT     NOT NULL DEFAULT 1,
  `manual_episode` VARCHAR(32) DEFAULT NULL,
  `ended`          TINYINT     NOT NULL DEFAULT 0,
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media` (`media_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日更新策展';

CREATE TABLE IF NOT EXISTS `drama` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `title`          VARCHAR(200) NOT NULL,
  `episode_count`  INT          DEFAULT NULL,
  `quark_link`     VARCHAR(500) NOT NULL,
  `baidu_link`     VARCHAR(500) DEFAULT NULL,
  `cover_image`    VARCHAR(500) DEFAULT NULL,
  `source_channel` VARCHAR(100) DEFAULT NULL,
  `message_time`   DATETIME     DEFAULT NULL,
  `status`         TINYINT      DEFAULT 1,
  `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quark_link` (`quark_link`(191)),
  KEY `idx_status_message_time` (`status`, `message_time`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧';

CREATE TABLE IF NOT EXISTS `content_event` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `media_id`   BIGINT      DEFAULT NULL,
  `event_type` VARCHAR(16) NOT NULL,
  `visitor_id` VARCHAR(36) DEFAULT NULL,
  `keyword`    VARCHAR(128) DEFAULT NULL,
  `tag`        VARCHAR(32)  DEFAULT NULL,
  `num`        INT          DEFAULT NULL,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_time` (`media_id`, `created_at`),
  KEY `idx_type_time` (`event_type`, `created_at`),
  KEY `idx_event_keyword` (`event_type`, `keyword`),
  KEY `idx_visitor_time` (`visitor_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名埋点';

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64)  NOT NULL,
  `category`   VARCHAR(16)  NOT NULL DEFAULT 'other',
  `action`     VARCHAR(16)  NOT NULL DEFAULT 'block',
  `enabled`    TINYINT      NOT NULL DEFAULT 1,
  `remark`     VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_category` (`category`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key`   VARCHAR(64)  NOT NULL,
  `config_value` MEDIUMTEXT   DEFAULT NULL,
  `description`  VARCHAR(255) DEFAULT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

CREATE TABLE IF NOT EXISTS `live_qrcode_config` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `qrcode_image`    VARCHAR(500) DEFAULT NULL,
  `mp_qrcode_image` VARCHAR(500) DEFAULT NULL,
  `title`           VARCHAR(100) DEFAULT '防止失联',
  `tip_text`        VARCHAR(200) DEFAULT '长按识别二维码，加入交流群',
  `scan_count`      INT          DEFAULT 0,
  `status`          TINYINT      DEFAULT 1,
  `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活码配置';

CREATE TABLE IF NOT EXISTS `live_qrcode_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `source`     VARCHAR(50)  DEFAULT '',
  `ip`         VARCHAR(50)  DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_source` (`source`),
  KEY `idx_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活码访问日志';

CREATE TABLE IF NOT EXISTS `doc_monitor_task` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `source`           VARCHAR(32)   NOT NULL DEFAULT 'flowus',
  `task_name`        VARCHAR(255)  NOT NULL DEFAULT '',
  `share_url`        VARCHAR(1000) NOT NULL,
  `access_code`      VARCHAR(64)   DEFAULT NULL,
  `category`         VARCHAR(100)  DEFAULT NULL,
  `status`           TINYINT       NOT NULL DEFAULT 1,
  `parse_rules`      JSON          DEFAULT NULL,
  `content_hash`     VARCHAR(64)   DEFAULT NULL,
  `links_count`      INT           NOT NULL DEFAULT 0,
  `text_length`      INT           NOT NULL DEFAULT 0,
  `drama_count`      INT           NOT NULL DEFAULT 0,
  `entries_json`     MEDIUMTEXT    DEFAULT NULL,
  `last_check_time`  DATETIME      DEFAULT NULL,
  `last_update_time` DATETIME      DEFAULT NULL,
  `remark`           VARCHAR(500)  DEFAULT NULL,
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_source` (`status`, `source`),
  KEY `idx_last_update` (`last_update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档监控任务';

CREATE TABLE IF NOT EXISTS `doc_monitor_history` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`            BIGINT       NOT NULL,
  `source`             VARCHAR(32)  NOT NULL DEFAULT 'flowus',
  `task_name`          VARCHAR(255) NOT NULL DEFAULT '',
  `old_links_count`    INT          NOT NULL DEFAULT 0,
  `new_links_count`    INT          NOT NULL DEFAULT 0,
  `links_count_diff`   INT          NOT NULL DEFAULT 0,
  `old_text_length`    INT          NOT NULL DEFAULT 0,
  `new_text_length`    INT          NOT NULL DEFAULT 0,
  `text_length_diff`   INT          NOT NULL DEFAULT 0,
  `content_hash`       VARCHAR(64)  DEFAULT NULL,
  `check_type`         VARCHAR(20)  NOT NULL DEFAULT 'auto',
  `has_update`         TINYINT      NOT NULL DEFAULT 0,
  `change_description` VARCHAR(500) DEFAULT NULL,
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档监控历史';

CREATE TABLE IF NOT EXISTS `transfer_account` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
  `worker_id`             VARCHAR(64)  NOT NULL,
  `pan_type`              VARCHAR(16)  NOT NULL,
  `account_name`          VARCHAR(64)  NOT NULL,
  `credential`            TEXT         DEFAULT NULL,
  `baidu_access_token`    VARCHAR(600) DEFAULT NULL,
  `baidu_token_expire_at` DATETIME     DEFAULT NULL,
  `target_dir_fid`        VARCHAR(191) DEFAULT NULL,
  `role`                  VARCHAR(16)  NOT NULL DEFAULT 'transfer',
  `nickname`              VARCHAR(128) DEFAULT NULL,
  `uid`                   VARCHAR(64)  DEFAULT NULL,
  `total_space`           BIGINT       DEFAULT NULL,
  `used_space`            BIGINT       DEFAULT NULL,
  `enabled`               TINYINT      NOT NULL DEFAULT 1,
  `healthy`               TINYINT      NOT NULL DEFAULT 1,
  `removing`              TINYINT(1)   NOT NULL DEFAULT 0,
  `note`                  VARCHAR(255) DEFAULT NULL,
  `last_seen_at`          DATETIME     DEFAULT NULL,
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_pan_name` (`worker_id`, `pan_type`, `account_name`),
  KEY `idx_pan` (`pan_type`, `enabled`, `healthy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘账号';

CREATE TABLE IF NOT EXISTS `transfer_job` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `job_type`         VARCHAR(16)   NOT NULL,
  `pan_type`         VARCHAR(16)   NOT NULL,
  `account_name`     VARCHAR(64)   DEFAULT NULL,
  `share_url`        VARCHAR(1024) NOT NULL,
  `share_pwd`        VARCHAR(64)   DEFAULT NULL,
  `media_link_id`    BIGINT        DEFAULT NULL,
  `target_folder_id` VARCHAR(128)  DEFAULT NULL,
  `landing_dir`      VARCHAR(128)  DEFAULT NULL,
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'pending',
  `priority`         INT           NOT NULL DEFAULT 0,
  `attempts`         INT           NOT NULL DEFAULT 0,
  `max_attempts`     INT           NOT NULL DEFAULT 3,
  `available_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `worker_id`        VARCHAR(64)   DEFAULT NULL,
  `lease_until`      DATETIME      DEFAULT NULL,
  `result_json`      TEXT,
  `result_share_url` VARCHAR(1024) DEFAULT NULL,
  `result_folder_id` VARCHAR(128)  DEFAULT NULL,
  `error_msg`        VARCHAR(512)  DEFAULT NULL,
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_claim` (`status`, `pan_type`, `available_at`),
  KEY `idx_lease` (`status`, `lease_until`),
  KEY `idx_media_link` (`media_link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转存任务队列';

CREATE TABLE IF NOT EXISTS `transfer_monitor` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `media_link_id`    BIGINT        NOT NULL,
  `pan_type`         VARCHAR(16)   NOT NULL,
  `account_name`     VARCHAR(64)   DEFAULT NULL,
  `share_url`        VARCHAR(1024) NOT NULL,
  `share_pwd`        VARCHAR(64)   DEFAULT NULL,
  `enabled`          TINYINT       NOT NULL DEFAULT 1,
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'active',
  `target_folder_id` VARCHAR(128)  DEFAULT NULL,
  `my_share_url`     VARCHAR(1024) DEFAULT NULL,
  `last_updated_at`  BIGINT        DEFAULT NULL,
  `last_file_count`  INT           DEFAULT NULL,
  `last_title`       VARCHAR(512)  DEFAULT NULL,
  `latest_episode`   VARCHAR(64)   DEFAULT NULL,
  `last_probe_at`    DATETIME      DEFAULT NULL,
  `last_content_at`  DATETIME      DEFAULT NULL,
  `check_days`       VARCHAR(20)   DEFAULT NULL,
  `check_hours`      VARCHAR(20)   DEFAULT NULL,
  `check_interval`   INT           DEFAULT NULL,
  `fail_count`       INT           NOT NULL DEFAULT 0,
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_link` (`media_link_id`),
  KEY `idx_active` (`enabled`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追更监控';

CREATE TABLE IF NOT EXISTS `transfer_record` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `pan_type`      VARCHAR(16)   NOT NULL,
  `account_name`  VARCHAR(64)   DEFAULT NULL,
  `share_id`      VARCHAR(191)  NOT NULL,
  `share_url`     VARCHAR(1024) NOT NULL,
  `share_pwd`     VARCHAR(64)   DEFAULT NULL,
  `my_share_url`  VARCHAR(1024) DEFAULT NULL,
  `my_share_pwd`  VARCHAR(64)   DEFAULT NULL,
  `folder_id`     VARCHAR(128)  DEFAULT NULL,
  `delete_job_id` BIGINT        DEFAULT NULL,
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'active',
  `is_permanent`  TINYINT       NOT NULL DEFAULT 0,
  `transfer_time` DATETIME      DEFAULT NULL,
  `expire_time`   DATETIME      DEFAULT NULL,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pan_share` (`pan_type`, `share_id`),
  KEY `idx_expire` (`status`, `is_permanent`, `expire_time`),
  KEY `idx_delete_job` (`delete_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转存记录';

CREATE TABLE IF NOT EXISTS `transfer_login_session` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`   VARCHAR(64)  NOT NULL,
  `worker_id`    VARCHAR(64)  DEFAULT NULL,
  `pan_type`     VARCHAR(16)  NOT NULL,
  `mode`         VARCHAR(16)  NOT NULL DEFAULT 'cookie',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'pending',
  `qr_content`   TEXT         DEFAULT NULL,
  `qr_image_url` TEXT         DEFAULT NULL,
  `account_name` VARCHAR(64)  DEFAULT NULL,
  `credential`   TEXT         DEFAULT NULL,
  `message`      VARCHAR(255) DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session` (`session_id`),
  KEY `idx_worker_status` (`worker_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加号会话';

CREATE TABLE IF NOT EXISTS `transfer_pan_pointer` (
  `pan_type` VARCHAR(16) NOT NULL,
  `follow_account_name` VARCHAR(64) DEFAULT NULL,
  `library_account_name` VARCHAR(64) DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`pan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每盘追更号/片库号指针';
