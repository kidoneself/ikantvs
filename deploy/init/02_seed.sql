-- 演示数据：空库首次初始化后即可浏览前台、登录后台。
-- 后台默认账号 admin / admin123（上线务必改密或删掉本账号）

SET NAMES utf8mb4;

INSERT INTO `user` (`username`, `password_hash`, `nickname`, `role`, `status`)
VALUES (
  'admin',
  '$2a$10$OnaPanYBDmiHvIh6CdpbnOI1XQ.cKVuRfNlUEzzZuqvxHazrZiVNG',
  '管理员',
  'admin',
  0
) ON DUPLICATE KEY UPDATE `username` = `username`;

INSERT INTO `media` (
  `id`, `type`, `title`, `original_title`, `year`, `overview`, `genres`,
  `rating`, `hot`, `hot_seed`, `meta_source`, `pub_status`, `search_hidden`
) VALUES
  (1, 'movie', '演示电影', 'Demo Movie', 2024,
   '这是开箱演示条目。对接云端数据源后会显示真实片库。',
   '剧情', 8.0, 100, 100, 'manual', 1, 0),
  (2, 'tv', '演示剧集', 'Demo Series', 2025,
   '演示用剧集。本地模式只有示例链；填写云端 API 链接后可同步完整数据。',
   '剧情,悬疑', 8.5, 200, 200, 'manual', 1, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `media_link` (
  `media_id`, `pan_type`, `url`, `share_id`, `note`, `source`, `status`
) VALUES
  (1, 'magnet', 'magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=DemoMovie',
   '0123456789ABCDEF0123456789ABCDEF01234567', '演示磁力（不可下载）', 'manual', 'approved'),
  (2, 'xunlei', 'https://pan.xunlei.com/s/demo-share-id',
   'demo-share-id', '演示迅雷链（占位）', 'manual', 'approved')
ON DUPLICATE KEY UPDATE `note` = VALUES(`note`);

INSERT INTO `ranking` (`id`, `name`, `slug`, `description`, `sort`, `enabled`)
VALUES (1, '演示热门', 'demo-hot', '开箱演示榜单', 100, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `ranking_item` (`ranking_id`, `media_id`, `rank_no`)
VALUES (1, 2, 1), (1, 1, 2)
ON DUPLICATE KEY UPDATE `rank_no` = VALUES(`rank_no`);

INSERT INTO `live_qrcode_config` (`id`, `title`, `tip_text`, `status`)
VALUES (1, '防止失联', '长按识别二维码，加入交流群', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;
