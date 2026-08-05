-- 文档资源发现（search 域）：FlowUs / 金山文档等分享页监控。
-- 抓取器按 source 插拔；聚合格式按任务 parse_rules（JSON）配置，改规则无需发版。
CREATE TABLE IF NOT EXISTS `doc_monitor_task` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `source`           VARCHAR(32)   NOT NULL DEFAULT 'flowus' COMMENT 'flowus / kdocs / …（对应 DocFetcher.source）',
  `task_name`        VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '任务名；kdocs 可空，检查时用文档标题回填',
  `share_url`        VARCHAR(1000) NOT NULL COMMENT '分享链接',
  `access_code`      VARCHAR(64)   DEFAULT NULL COMMENT '访问码（如有）',
  `category`         VARCHAR(100)  DEFAULT NULL COMMENT '运营分类',
  `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '0 禁用 1 启用',
  `parse_rules`      JSON          DEFAULT NULL COMMENT '剧名/夸克/百度行聚合规则；空则用 source 默认模板',
  `content_hash`     VARCHAR(64)   DEFAULT NULL COMMENT '内容指纹（kdocs=fver，flowus=md5）',
  `links_count`      INT           NOT NULL DEFAULT 0,
  `text_length`      INT           NOT NULL DEFAULT 0,
  `drama_count`      INT           NOT NULL DEFAULT 0,
  `entries_json`     MEDIUMTEXT    DEFAULT NULL COMMENT '最近一次成功解析的 DramaEntry 列表（供搜索）',
  `last_check_time`  DATETIME      DEFAULT NULL,
  `last_update_time` DATETIME      DEFAULT NULL COMMENT '内容有变化的时间',
  `remark`           VARCHAR(500)  DEFAULT NULL,
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_source` (`status`, `source`),
  KEY `idx_last_update` (`last_update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档监控任务（资源发现）';

CREATE TABLE IF NOT EXISTS `doc_monitor_history` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`             BIGINT       NOT NULL,
  `source`              VARCHAR(32)  NOT NULL DEFAULT 'flowus',
  `task_name`           VARCHAR(255) NOT NULL DEFAULT '',
  `old_links_count`     INT          NOT NULL DEFAULT 0,
  `new_links_count`     INT          NOT NULL DEFAULT 0,
  `links_count_diff`    INT          NOT NULL DEFAULT 0,
  `old_text_length`     INT          NOT NULL DEFAULT 0,
  `new_text_length`     INT          NOT NULL DEFAULT 0,
  `text_length_diff`    INT          NOT NULL DEFAULT 0,
  `content_hash`        VARCHAR(64)  DEFAULT NULL,
  `check_type`          VARCHAR(20)  NOT NULL DEFAULT 'auto' COMMENT 'manual / auto / preview',
  `has_update`          TINYINT      NOT NULL DEFAULT 0,
  `change_description`  VARCHAR(500) DEFAULT NULL,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档监控检查历史';
