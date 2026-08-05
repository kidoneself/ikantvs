-- 失效分享黑名单（content 域）：按 pan_type + share_id 记住"确定失效"的分享。
-- 用途：① 采集入库前过滤，命中直接不入库（等价老系统 filterInvalidLinks）；
--       ② 转存首转因链接失效失败时回写，越用越准。
-- 只收"链接本身失效"（分享删除/取消/无权限等），不收账号侧失败（cookie 过期等）。
-- pan_type/share_id 用 ascii 字符集，与 media_link 对齐，避免跨表 JOIN 时 collation 冲突。
CREATE TABLE IF NOT EXISTS `invalid_share` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `pan_type`   VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'quark/baidu/xunlei/...',
  `share_id`   VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '规范化分享 id（与 media_link.share_id 同一套规则）',
  `error_code` VARCHAR(50)  NULL COMMENT '失效错误码（可空）',
  `reason`     VARCHAR(255) NULL COMMENT '失效原因摘要（可空）',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pan_share` (`pan_type`, `share_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失效分享黑名单：入库前过滤 + 转存失败回写';
