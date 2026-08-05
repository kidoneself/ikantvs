-- transfer 域：worker 账号镜像（仅元数据，不含 cookie/token）。
-- cookie/refreshToken 只留 worker 本地文件（凭据不外传主站）；主站这张表用于后台展示/管理账号
-- （在线情况、启用/失效、发起扫码换号）。数据由 worker 心跳全量上报后同步维护。

CREATE TABLE IF NOT EXISTS `transfer_account` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `worker_id`     VARCHAR(64)  NOT NULL                COMMENT '账号所在 worker(账号绑死本机出口IP)',
  `pan_type`      VARCHAR(16)  NOT NULL                COMMENT 'quark/baidu/xunlei',
  `account_name`  VARCHAR(64)  NOT NULL                COMMENT 'worker 侧账号名(转存绑定用)',
  `enabled`       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用',
  `healthy`       TINYINT      NOT NULL DEFAULT 1      COMMENT '凭据是否有效(worker 标记，0=失效需重扫)',
  `note`          VARCHAR(255) DEFAULT NULL,
  `last_seen_at`  DATETIME     DEFAULT NULL            COMMENT '最近一次心跳见到该账号的时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_pan_name` (`worker_id`, `pan_type`, `account_name`),
  KEY `idx_pan` (`pan_type`, `enabled`, `healthy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='worker 账号镜像(元数据,不含凭据)';
