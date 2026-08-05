-- billing 域：套餐 + 卡密（站外发卡平台收款，主站只核销）
CREATE TABLE IF NOT EXISTS `plan` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(32)  NOT NULL                COMMENT '展示名，如月卡',
  `slug`       VARCHAR(16)  NOT NULL                COMMENT 'month/quarter/year',
  `days`       INT          NOT NULL                COMMENT '兑换后顺延天数',
  `enabled`    TINYINT      NOT NULL DEFAULT 1,
  `sort`       INT          NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员套餐（仅时长，不含支付）';

CREATE TABLE IF NOT EXISTS `redeem_code` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32)  NOT NULL                COMMENT '卡密（大写字母数字，无横线存储）',
  `plan_id`     BIGINT       NOT NULL,
  `used_by`     BIGINT       DEFAULT NULL            COMMENT '核销用户 id',
  `used_at`     DATETIME     DEFAULT NULL,
  `expire_at`   DATETIME     DEFAULT NULL            COMMENT '卡密过期时间（可选）',
  `batch_note`  VARCHAR(128) DEFAULT NULL            COMMENT '批次备注，如聚发货上架批次',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_used` (`used_by`, `used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密池';

INSERT INTO `plan` (`name`, `slug`, `days`, `enabled`, `sort`) VALUES
  ('月卡', 'month', 30, 1, 1),
  ('季卡', 'quarter', 90, 1, 2),
  ('年卡', 'year', 365, 1, 3);
