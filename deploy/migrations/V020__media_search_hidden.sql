-- 单条内容「前台隐藏」：运营手动标记后，搜索/分类/首页/详情均不可见（后台仍可编辑）。
-- 与 pubStatus 下架（草稿/下架流程）互补；敏感词 block 仍自动拦截 junk。
ALTER TABLE `media`
  ADD COLUMN `search_hidden` TINYINT NOT NULL DEFAULT 0 COMMENT '1=前台隐藏 0=正常' AFTER `pub_status`;
