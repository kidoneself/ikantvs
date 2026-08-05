-- 用户邮箱：卡密激活后可绑定，支持「邮箱直接登录」
ALTER TABLE `user`
  ADD COLUMN `email` VARCHAR(128) DEFAULT NULL COMMENT '绑定邮箱，用于卡密/邮箱登录' AFTER `avatar`,
  ADD UNIQUE KEY `uk_email` (`email`);
