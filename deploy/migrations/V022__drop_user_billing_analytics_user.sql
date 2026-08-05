-- 产品决策（2026-06-30）：无前台用户、无会员计费；埋点不再关联用户。

DROP TABLE IF EXISTS `user_favorite`;
DROP TABLE IF EXISTS `redeem_code`;
DROP TABLE IF EXISTS `plan`;

-- user_id / idx_user_time 可能未建过，忽略错误由运维手动确认
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'content_event' AND index_name = 'idx_user_time');
SET @sql := IF(@exist > 0, 'ALTER TABLE `content_event` DROP INDEX `idx_user_time`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'content_event' AND column_name = 'user_id');
SET @sql := IF(@exist > 0, 'ALTER TABLE `content_event` DROP COLUMN `user_id`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_email');
SET @sql := IF(@exist > 0, 'ALTER TABLE `user` DROP INDEX `uk_email`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'email');
SET @sql := IF(@exist > 0, 'ALTER TABLE `user` DROP COLUMN `email`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'member_expire_at');
SET @sql := IF(@exist > 0, 'ALTER TABLE `user` DROP COLUMN `member_expire_at`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
