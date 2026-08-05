-- 方案A：凭据集中存主站（不再落 worker 磁盘）。cookie(夸克/百度) 或 refresh_token(迅雷)
-- 直接进这张镜像表，worker 内存持有、启动/定时从主站拉取；迅雷滚动的 refresh_token 由 worker 回写。
-- 本项目规模小，暂不加密（按需可后续加）。
ALTER TABLE `transfer_account`
  ADD COLUMN `credential`     TEXT         NULL COMMENT '凭据：cookie(夸克/百度)或refresh_token(迅雷)' AFTER `account_name`,
  ADD COLUMN `target_dir_fid` VARCHAR(191) NULL COMMENT '转存目标目录 fid/path(可空,用默认根目录)' AFTER `credential`;
