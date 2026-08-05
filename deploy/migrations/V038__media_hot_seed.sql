-- 热度回写：拆出 hot_seed 作为「基线热度」（TMDB popularity 种子 / 后台手工设定），
-- 行为热度回写 job 计算 hot = hot_seed + 近 N 天行为分（带衰减），
-- 无近期行为的片会衰减回 hot_seed，既有种子/手工值不会被冲掉。
ALTER TABLE `media`
  ADD COLUMN `hot_seed` INT NOT NULL DEFAULT 0 COMMENT '基线热度：TMDB 种子/手工设定，行为分在其上叠加' AFTER `hot`;

-- 现网存量：把当前 hot 作为基线迁入 hot_seed。
UPDATE `media` SET `hot_seed` = `hot` WHERE `hot_seed` = 0 AND `hot` <> 0;
