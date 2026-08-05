-- NAS 灌盘：千云认领的任务队列 + 每剧一个迅雷落地夹（百度领先时灌入，迅雷上游复用）。
-- 与千云 backend/app/db.py 的 nas_job DDL 对齐（双方 CREATE IF NOT EXISTS 幂等）。

CREATE TABLE IF NOT EXISTS `nas_job` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `baidu_account_id`   BIGINT        NOT NULL DEFAULT 0      COMMENT '执行时千云会改写为百度 role=monitor 的 id',
  `xunlei_folder_id`   VARCHAR(128)  NOT NULL                COMMENT '迅雷固定落地夹 id（禁止新建根夹）',
  `files_json`         MEDIUMTEXT    NOT NULL                COMMENT '[{fs_id,name,size,rel_dir}]，差集已在 next 算好',
  `title`              VARCHAR(255)  DEFAULT NULL            COMMENT '展示名（剧名等）',
  `media_link_id`      BIGINT        DEFAULT NULL            COMMENT '关联 media_link（可空）',
  `status`             VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/running/done/failed',
  `priority`           INT           NOT NULL DEFAULT 0      COMMENT '越大越先派发',
  `attempts`           INT           NOT NULL DEFAULT 0      COMMENT '已尝试次数',
  `max_attempts`       INT           NOT NULL DEFAULT 3      COMMENT '最大重试次数',
  `available_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最早可领取时间',
  `worker_id`          VARCHAR(64)   DEFAULT NULL            COMMENT '当前领取的 worker',
  `lease_until`        DATETIME      DEFAULT NULL            COMMENT '租约到期（超时回收）',
  `total_files`        INT           NOT NULL DEFAULT 0,
  `done_files`         INT           NOT NULL DEFAULT 0,
  `failed_files`       INT           NOT NULL DEFAULT 0,
  `result_json`        MEDIUMTEXT                            COMMENT '每文件结果快照',
  `error_msg`          VARCHAR(512)  DEFAULT NULL            COMMENT '最近一次失败原因',
  `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_claim` (`status`, `available_at`, `priority`),
  KEY `idx_lease` (`status`, `lease_until`),
  KEY `idx_media_link` (`media_link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NAS 灌盘任务（百度→迅雷，千云执行）';

CREATE TABLE IF NOT EXISTS `nas_landing` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `media_id`              BIGINT        NOT NULL                COMMENT 'content.media.id，一部剧一个迅雷夹',
  `source_media_link_id`  BIGINT        DEFAULT NULL            COMMENT '触发建夹的百度 media_link.id',
  `xunlei_folder_id`      VARCHAR(128)  NOT NULL                COMMENT '迅雷固定落地夹 id',
  `xunlei_share_url`      VARCHAR(1024) DEFAULT NULL            COMMENT '该夹的永久分享链',
  `baseline_json`         MEDIUMTEXT    DEFAULT NULL            COMMENT '建夹时百度文件名基线 JSON 数组，NAS 只追基线外新集',
  `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media` (`media_id`),
  KEY `idx_source_link` (`source_media_link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧级迅雷落地夹（百度灌盘与迅雷上游共用）';
