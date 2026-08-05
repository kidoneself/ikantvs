-- transfer 域：追更监控状态表。
-- 追更是 transfer 域自身职责，状态自存（铁律：不跨域读写 content 的 media_link 表）。
-- 启用监控时把 transfer 需要的字段(pan_type/share_url/pwd)冗余进来，运行期不回读 content。

CREATE TABLE IF NOT EXISTS `transfer_monitor` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `media_link_id`    BIGINT        NOT NULL                COMMENT '关联 content 的 media_link.id(松引用,不建外键)',
  `pan_type`         VARCHAR(16)   NOT NULL                COMMENT 'quark/baidu/xunlei',
  `share_url`        VARCHAR(1024) NOT NULL                COMMENT '源分享链接',
  `share_pwd`        VARCHAR(64)   DEFAULT NULL,
  `enabled`          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用追更',
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/invalid(死链)/paused',
  `target_folder_id` VARCHAR(128)  DEFAULT NULL            COMMENT '首转落地的固定夹id(追更复用)',
  `my_share_url`     VARCHAR(1024) DEFAULT NULL            COMMENT '我方分享链(首转生成,追更不变)',
  `last_updated_at`  BIGINT        DEFAULT NULL            COMMENT '源分享上次记录的更新时间戳(ms),追更核心',
  `last_file_count`  INT           DEFAULT NULL            COMMENT '源分享上次记录的文件数(辅助判断)',
  `last_title`       VARCHAR(512)  DEFAULT NULL            COMMENT '源分享上次标题',
  `latest_episode`   VARCHAR(64)   DEFAULT NULL            COMMENT '最新集数/文件(展示用)',
  `last_probe_at`    DATETIME      DEFAULT NULL            COMMENT '上次巡检时间(算间隔用)',
  `fail_count`       INT           NOT NULL DEFAULT 0      COMMENT '连续死链次数',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_link` (`media_link_id`),
  KEY `idx_active` (`enabled`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追更监控状态';
