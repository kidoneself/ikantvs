-- 按域名配置前台可见网盘（可增删域名；各盘 BOOL）
CREATE TABLE IF NOT EXISTS `site_domain_config` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `host`       VARCHAR(128) NOT NULL COMMENT '归一化域名，如 naspt.vip',
  `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用该行 / 1 启用',
  `pans_json`  JSON         NOT NULL COMMENT 'slug→bool，如 {"quark":true,"magnet":true}',
  `remark`     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_host` (`host`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点域名网盘开关';

-- 种子：naspt 仅迅雷+磁力；ik / ikantvs 全开（与现网一致）
INSERT INTO `site_domain_config` (`host`, `enabled`, `pans_json`, `remark`) VALUES
('naspt.vip', 1,
 '{"magnet":true,"baidu":false,"quark":false,"xunlei":true,"uc":false,"aliyun":false,"tianyi":false,"mobile":false,"pan115":false,"pan123":false,"other":false}',
 '主站：默认迅雷+磁力'),
('ik.naspt.vip', 1,
 '{"magnet":true,"baidu":true,"quark":true,"xunlei":true,"uc":true,"aliyun":true,"tianyi":true,"mobile":true,"pan115":true,"pan123":true,"other":true}',
 '预发/新站：默认全开'),
('ikantvs.com', 1,
 '{"magnet":true,"baidu":true,"quark":true,"xunlei":true,"uc":true,"aliyun":true,"tianyi":true,"mobile":true,"pan115":true,"pan123":true,"other":true}',
 'ikantvs：默认全开')
ON DUPLICATE KEY UPDATE `host` = `host`;
