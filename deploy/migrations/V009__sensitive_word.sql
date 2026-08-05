-- 敏感词（ops 域）：用于「搜索词拦截」与「内容展示发布门槛」。
-- action 分级：block 拦截 / review 转人工 / warn 仅标记 / replace 打码。
-- category 分类：politics/porn/ad/violence/legacy/other，便于批量管理与按类配动作。
-- 老库迁入的词默认 category=legacy、action=warn（先当观察名单，避免误杀展示/搜索）。
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64)  NOT NULL                COMMENT '敏感词（归一化存：去首尾空白、转小写）',
  `category`   VARCHAR(16)  NOT NULL DEFAULT 'other' COMMENT 'politics/porn/ad/violence/legacy/other',
  `action`     VARCHAR(16)  NOT NULL DEFAULT 'block' COMMENT 'block拦截 / review转审 / warn仅标记 / replace打码',
  `enabled`    TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 0 停用',
  `remark`     VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_category` (`category`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词';
