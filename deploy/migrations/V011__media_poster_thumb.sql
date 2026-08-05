-- 列表用小图（WebP ~256px），详情仍用 poster（w500）；旧数据 poster 不变，thumb 可后续回填。
ALTER TABLE `media`
  ADD COLUMN `poster_thumb` VARCHAR(512) DEFAULT NULL COMMENT '列表缩略图 URL（WebP）' AFTER `poster`;
