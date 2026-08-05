-- media_link：新增去重键 share_id + 检测状态列，并建唯一键。
-- 注意：本表存量约 74 万行，去重不用自连接（会卡死），改为「先加辅助索引 → GROUP BY 保留最小 id」。
-- share_id 提取规则与 Java ShareIdExtractor 保持一致（常见 /s/{id}、百度 surl、磁力 btih，取不到用 md5）。

-- 1) 加列（MySQL 8 对可空列为 INSTANT，秒级）
ALTER TABLE `media_link`
  ADD COLUMN `share_id`      VARCHAR(64) CHARACTER SET ascii DEFAULT NULL COMMENT '规范化分享id(去重键)' AFTER `url`,
  ADD COLUMN `check_state`   VARCHAR(12)  DEFAULT NULL COMMENT 'ok/bad/locked/unsupported/uncertain，null=未检' AFTER `invalid`,
  ADD COLUMN `checked_at`    DATETIME     DEFAULT NULL COMMENT '上次检测时间' AFTER `check_state`,
  ADD COLUMN `check_summary` VARCHAR(255) DEFAULT NULL COMMENT '检测说明' AFTER `checked_at`,
  ADD COLUMN `last_seen_at`  DATETIME     DEFAULT NULL COMMENT '来源最近一次出现时间(新鲜度)' AFTER `check_summary`;

-- 2) 回填 share_id（存量一次性）。
-- 提取值只有通过 ascii 合法校验才采用，否则退回 md5(url)——避免非 ascii 字符写入 ascii 列。
UPDATE `media_link`
SET `share_id` = IF(
  (CASE
     WHEN `pan_type` = 'magnet'
       THEN LOWER(REGEXP_REPLACE(`url`, '^.*[Bb][Tt][Ii][Hh]:([A-Za-z0-9]+).*$', '$1'))
     WHEN `pan_type` = 'baidu' AND `url` LIKE '%surl=%'
       THEN REGEXP_REPLACE(`url`, '^.*surl=([^&#\r\n]+).*$', '$1')
     ELSE
       SUBSTRING_INDEX(
         TRIM(TRAILING '/' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(`url`, CHAR(10), 1), '#', 1), '?', 1)),
         '/', -1)
   END) REGEXP '^[A-Za-z0-9._~-]{1,64}$',
  (CASE
     WHEN `pan_type` = 'magnet'
       THEN LOWER(REGEXP_REPLACE(`url`, '^.*[Bb][Tt][Ii][Hh]:([A-Za-z0-9]+).*$', '$1'))
     WHEN `pan_type` = 'baidu' AND `url` LIKE '%surl=%'
       THEN REGEXP_REPLACE(`url`, '^.*surl=([^&#\r\n]+).*$', '$1')
     ELSE
       SUBSTRING_INDEX(
         TRIM(TRAILING '/' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(`url`, CHAR(10), 1), '#', 1), '?', 1)),
         '/', -1)
   END),
  MD5(`url`)
)
WHERE `share_id` IS NULL;

-- 百度 /s/1xxxx 去前导 1（仅 /s/ 形式；排除 surl 形式与 md5 兜底值，保证与 Java 规则一致）
UPDATE `media_link`
SET `share_id` = SUBSTRING(`share_id`, 2)
WHERE `pan_type` = 'baidu' AND `share_id` LIKE '1%' AND CHAR_LENGTH(`share_id`) > 1
  AND `url` NOT LIKE '%surl=%'
  AND `share_id` NOT REGEXP '^[a-f0-9]{32}$';

-- 兜底：仍为空的用 md5(url)（保证非空，避免唯一键放行多个 NULL）
UPDATE `media_link`
SET `share_id` = MD5(`url`)
WHERE `share_id` IS NULL OR `share_id` = '';

-- 3) 加辅助索引，让去重的 GROUP BY 走索引（74 万行可控）
ALTER TABLE `media_link`
  ADD INDEX `idx_dedup` (`media_id`, `pan_type`, `share_id`);

-- 4) 去重：同 (media_id, pan_type, share_id) 只保留最小 id
DELETE t FROM `media_link` t
JOIN (
  SELECT `media_id`, `pan_type`, `share_id`, MIN(`id`) AS keep_id
  FROM `media_link`
  GROUP BY `media_id`, `pan_type`, `share_id`
  HAVING COUNT(*) > 1
) d
  ON t.`media_id` = d.`media_id`
 AND t.`pan_type` = d.`pan_type`
 AND t.`share_id` = d.`share_id`
WHERE t.`id` <> d.keep_id;

-- 5) 去掉辅助索引，改置非空 + 唯一键（合并为一次 ALTER，少一次重建）
ALTER TABLE `media_link`
  DROP INDEX `idx_dedup`,
  MODIFY COLUMN `share_id` VARCHAR(64) CHARACTER SET ascii NOT NULL COMMENT '规范化分享id(去重键)',
  ADD UNIQUE KEY `uk_link` (`media_id`, `pan_type`, `share_id`);
