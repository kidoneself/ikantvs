-- 百度开放平台删除令牌（隐式授权 access_token，约 30 天有效、不可刷新，到期重新授权）。
-- 与 credential(cookie) 分开存：cookie 走网页接口做转存/巡检；access_token 走 xpan 官方接口专做删除，
-- 避开网页删除天天要短信验证码的风控。每个百度号各存各的令牌，支持多号。
ALTER TABLE `transfer_account`
  ADD COLUMN `baidu_access_token` VARCHAR(600) NULL
    COMMENT '百度开放平台 access_token(隐式授权,删除用),约30天到期需重授权' AFTER `credential`,
  ADD COLUMN `baidu_token_expire_at` DATETIME NULL
    COMMENT '百度 access_token 到期时间,便于后台提示重新授权' AFTER `baidu_access_token`;
