-- 公告 HTML 等长文本超出 VARCHAR(512)；改为 MEDIUMTEXT
ALTER TABLE `sys_config`
  MODIFY COLUMN `config_value` MEDIUMTEXT COMMENT '配置值（统一存字符串；公告等可为长 HTML）';
