-- 豆瓣 id 批补：IMDb 链路仍无法解析时打标跳过，避免同一批空转
ALTER TABLE `media`
  ADD COLUMN `douban_backfill_skip` TINYINT NOT NULL DEFAULT 0
    COMMENT '1=已尝试 TMDB→IMDb→豆瓣 仍无法补 douban_id'
    AFTER `douban_id`;
