-- transfer 域：用户转存记录 + 缓存/去重 + 清理依据。
-- 用户点「转存」时按 (pan_type, share_id) 命中复用；首转成功写一条；
-- 定时清理任务删除过期非永久记录对应的网盘文件（迅雷永久保留）。

CREATE TABLE IF NOT EXISTS `transfer_record` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `pan_type`       VARCHAR(16)   NOT NULL                COMMENT 'quark/baidu/xunlei',
  `share_id`       VARCHAR(191)  NOT NULL                COMMENT '源分享去重键(从 url 提取)',
  `share_url`      VARCHAR(1024) NOT NULL                COMMENT '源分享链接',
  `share_pwd`      VARCHAR(64)   DEFAULT NULL,
  `my_share_url`   VARCHAR(1024) DEFAULT NULL            COMMENT '转存后我方分享链(返回给用户)',
  `my_share_pwd`   VARCHAR(64)   DEFAULT NULL            COMMENT '我方分享提取码(百度有,夸克通常无)',
  `folder_id`      VARCHAR(128)  DEFAULT NULL            COMMENT '落地夹 id(清理时删它;百度为路径)',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/deleted/failed',
  `is_permanent`   TINYINT       NOT NULL DEFAULT 0      COMMENT '1=永久保留,不参与清理(迅雷/精选)',
  `transfer_time`  DATETIME      DEFAULT NULL            COMMENT '转存完成时间',
  `expire_time`    DATETIME      DEFAULT NULL            COMMENT '到期时间(=transfer_time+保留时长);到期清理',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pan_share` (`pan_type`, `share_id`),
  KEY `idx_expire` (`status`, `is_permanent`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户转存记录/缓存';
