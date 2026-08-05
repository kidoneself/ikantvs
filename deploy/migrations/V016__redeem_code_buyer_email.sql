-- 卡密关联购买邮箱（聚发货下单时填写，由 webhook/订单同步写入，非用户手动绑定）
ALTER TABLE `redeem_code`
  ADD COLUMN `buyer_email` VARCHAR(128) DEFAULT NULL COMMENT '发卡平台订单邮箱' AFTER `batch_note`,
  ADD KEY `idx_buyer_email` (`buyer_email`);
