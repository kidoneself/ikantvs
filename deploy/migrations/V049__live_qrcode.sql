-- 活码 / 站内加群：群码可换图；公众号单独上传（对齐老站 live_qrcode，并补公众号字段）

CREATE TABLE IF NOT EXISTS `live_qrcode_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `qrcode_image` VARCHAR(500) DEFAULT NULL COMMENT '微信群二维码图片 URL',
    `mp_qrcode_image` VARCHAR(500) DEFAULT NULL COMMENT '公众号二维码图片 URL',
    `title` VARCHAR(100) DEFAULT '防止失联' COMMENT '页面/弹窗标题',
    `tip_text` VARCHAR(200) DEFAULT '长按识别二维码，加入交流群' COMMENT '引导文案',
    `scan_count` INT DEFAULT 0 COMMENT '活码页访问次数',
    `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用（站内弹窗与 /qr 共用）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活码/联系配置';

INSERT INTO `live_qrcode_config` (`id`, `title`, `tip_text`, `scan_count`, `status`)
VALUES (1, '防止失联', '长按识别二维码，加入交流群', 0, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;

CREATE TABLE IF NOT EXISTS `live_qrcode_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `source` VARCHAR(50) DEFAULT '' COMMENT '来源 from 参数',
    `ip` VARCHAR(50) DEFAULT NULL,
    `user_agent` VARCHAR(500) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_log_source` (`source`),
    KEY `idx_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活码访问日志';
