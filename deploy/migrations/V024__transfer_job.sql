-- transfer 域：worker 任务队列 + worker 节点心跳。
-- 通信模型：主站(香港)只入队/派发，广州 worker 出站长轮询领任务→执行→回报（agent 拉模式）。
-- worker 无需公网入口，出站 HTTPS 即可，跨境只走低频任务调度，网盘操作在广州本地完成。

CREATE TABLE IF NOT EXISTS `transfer_job` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `job_type`         VARCHAR(16)   NOT NULL                COMMENT 'probe(巡检)/first_save(首转)/sync(追更增量)',
  `pan_type`         VARCHAR(16)   NOT NULL                COMMENT 'quark/baidu/xunlei',
  `share_url`        VARCHAR(1024) NOT NULL                COMMENT '源分享链接',
  `share_pwd`        VARCHAR(64)   DEFAULT NULL            COMMENT '提取码(可空)',
  `media_link_id`    BIGINT        DEFAULT NULL            COMMENT '关联 media_link(可空)',
  `target_folder_id` VARCHAR(128)  DEFAULT NULL            COMMENT '追更复用的落地夹 id',
  `status`           VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/running/done/failed/canceled',
  `priority`         INT           NOT NULL DEFAULT 0      COMMENT '越大越先派发',
  `attempts`         INT           NOT NULL DEFAULT 0      COMMENT '已尝试次数',
  `max_attempts`     INT           NOT NULL DEFAULT 3      COMMENT '最大重试次数',
  `available_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最早可领取时间(重试退避/延迟执行)',
  `worker_id`        VARCHAR(64)   DEFAULT NULL            COMMENT '当前领取的 worker',
  `lease_until`      DATETIME      DEFAULT NULL            COMMENT '租约到期时间(超时未回报则回收重派)',
  `result_json`      TEXT                                  COMMENT 'probe: 时间戳/文件数快照; save: 结果快照',
  `result_share_url` VARCHAR(1024) DEFAULT NULL            COMMENT '转存后我方分享链',
  `result_folder_id` VARCHAR(128)  DEFAULT NULL            COMMENT '转存落地夹 id(供追更复用)',
  `error_msg`        VARCHAR(512)  DEFAULT NULL            COMMENT '最近一次失败原因',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_claim` (`status`, `pan_type`, `available_at`),
  KEY `idx_lease` (`status`, `lease_until`),
  KEY `idx_media_link` (`media_link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转存/追更任务队列';

CREATE TABLE IF NOT EXISTS `transfer_worker` (
  `worker_id`         VARCHAR(64)  NOT NULL               COMMENT 'worker 唯一标识(worker 自报)',
  `pan_types`         VARCHAR(64)  DEFAULT NULL           COMMENT '该 worker 可处理的网盘,逗号分隔',
  `account_count`     INT          NOT NULL DEFAULT 0     COMMENT '本机账号数',
  `remote_ip`         VARCHAR(64)  DEFAULT NULL           COMMENT '出站来源 IP',
  `note`              VARCHAR(255) DEFAULT NULL,
  `last_heartbeat_at` DATETIME     DEFAULT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转存 worker 节点心跳';
