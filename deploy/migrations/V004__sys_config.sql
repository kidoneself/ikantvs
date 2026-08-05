-- 系统配置表（ops 域）：注册开关 / 付费墙 / 套餐价等运行时可改的键值
-- 默认值由后端首次启动时按 application.yml/env 种子写入（仅当键不存在时），之后以本表为准。
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key`   VARCHAR(64)  NOT NULL                COMMENT '配置键，如 registration.mode',
  `config_value` MEDIUMTEXT DEFAULT NULL              COMMENT '配置值（统一存字符串；公告等可为长 HTML）',
  `description`  VARCHAR(255) DEFAULT NULL            COMMENT '说明',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';
