CREATE TABLE IF NOT EXISTS `transfer_login_session` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`    VARCHAR(64)  NOT NULL                COMMENT '会话id(UUID,前端轮询/worker回报都用它)',
  `worker_id`     VARCHAR(64)  NOT NULL                COMMENT '指定在哪台 worker 上扫(二维码/换cookie都在该机出口IP完成)',
  `pan_type`      VARCHAR(16)  NOT NULL                COMMENT 'quark/baidu',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending'
                                                       COMMENT 'pending待领/claimed已领取码/scanning码就绪待扫/success/failed/expired',
  `qr_content`    TEXT         DEFAULT NULL            COMMENT '二维码内容串(需前端渲染,夸克)',
  `qr_image_url`  TEXT         DEFAULT NULL            COMMENT '二维码图片地址(前端直接显示,百度)',
  `account_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '登录成功后 worker 落地的账号名',
  `message`       VARCHAR(255) DEFAULT NULL            COMMENT '失败原因/提示',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session` (`session_id`),
  KEY `idx_worker_status` (`worker_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫码登录会话(主站与worker中转,凭据不落主站)';
