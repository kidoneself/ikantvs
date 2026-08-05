-- 用户表（identity 域）
CREATE TABLE IF NOT EXISTS `user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(32)  NOT NULL                COMMENT '登录用户名',
  `password_hash`   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码',
  `nickname`        VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
  `avatar`          VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
  `member_expire_at` DATETIME    DEFAULT NULL            COMMENT '会员到期时间；空/过期=免费',
  `invite_code`     VARCHAR(16)  DEFAULT NULL            COMMENT '本人邀请码',
  `invited_by`      BIGINT       DEFAULT NULL            COMMENT '邀请人 user_id',
  `status`          TINYINT      NOT NULL DEFAULT 0      COMMENT '0 正常 1 封禁',
  `last_login_at`   DATETIME     DEFAULT NULL,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_invited_by` (`invited_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';
