-- 短剧资源表（TGForwarder 经 /api/drama/import 灌入；封面与老站可共用 drama-covers 目录）
CREATE TABLE IF NOT EXISTS `drama` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '短剧标题（已清洗前缀）',
    `episode_count` INT DEFAULT NULL COMMENT '集数',
    `quark_link` VARCHAR(500) NOT NULL COMMENT '夸克网盘链接',
    `baidu_link` VARCHAR(500) DEFAULT NULL COMMENT '百度网盘链接',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面路径（如 /drama-covers/xxx.jpg）',
    `source_channel` VARCHAR(100) DEFAULT NULL COMMENT 'TG来源频道',
    `message_time` DATETIME DEFAULT NULL COMMENT 'TG消息时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0下架 1上架',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_quark_link` (`quark_link`(191)),
    INDEX `idx_status_message_time` (`status`, `message_time`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧资源表';
