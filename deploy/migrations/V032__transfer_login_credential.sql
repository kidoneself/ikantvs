-- 登录会话改造：放弃扫码，改为「主站拿到凭据 → worker 拉取落号」。
--   mode=cookie：夸克/百度，凭据是后台粘贴的整段 cookie；
--   mode=oauth ：迅雷，凭据是回调换来的 refresh_token（授权成功前 status=pending_auth）。
-- credential 由 worker 领走即用即弃，主站不做长期凭据存储。
ALTER TABLE `transfer_login_session`
  ADD COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT 'cookie'
      COMMENT '凭据获取方式：cookie(夸克/百度粘贴) / oauth(迅雷授权)' AFTER `pan_type`,
  ADD COLUMN `credential` TEXT
      COMMENT 'worker 应落库的凭据：cookie 整段 或 迅雷 refresh_token（用完即弃）' AFTER `account_name`;
