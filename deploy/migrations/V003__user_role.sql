-- 用户角色：user / contributor / reviewer / admin
ALTER TABLE `user`
  ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'user'
    COMMENT 'user=普通 contributor=录入员 reviewer=审核员 admin=管理员'
    AFTER `status`;

-- 已有账号默认升为 admin，便于本地/早期运营；新注册用户仍为 user
UPDATE `user` SET `role` = 'admin' WHERE `role` = 'user';
