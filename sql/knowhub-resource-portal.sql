-- =============================================================================
-- knowhub 资源前台门户（/portal/resource/*）补充脚本
-- -----------------------------------------------------------------------------
-- 用途：资源前台搜索接口走 MySQL FULLTEXT 全文索引（同博客 ft_blog_title_content 范式），
--   对 resource(title, summary, description) 建 ngram 分词全文索引 ft_resource_title_summary_desc，
--   供 /portal/resource/search 的 keyword 命中 MATCH...AGAINST IN BOOLEAN MODE。
--
-- 前置条件：MySQL 8 已开启 ngram 分词（my.cnf [mysqld] ngram_token_size = 2 后重启，
--   或会话执行 SET GLOBAL ngram_token_size = 2 后建索引——同博客 ft_blog_title_content 的口径，
--   详见 knowhub-blog.sql 第 13-16 行注释）。中文全文搜索依赖 ngram 分词，未配置则 FULLTEXT 对中文无效。
--
-- 幂等：用 information_schema.STATISTICS 判 FULLTEXT 索引是否存在 + PREPARE，重复执行不报错。
-- 无新菜单/字典/config，不占 menu_id/dict_id/config_id（仅加索引，ALTER 与建表均不续编这些 ID）。
-- 续编前已查：本脚本不涉及 sys_menu/sys_dict/sys_config 续编，无需查 MAX。记 [[knowhub-sql-id-numbering-pitfall]]。
-- =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. resource 主表加 FULLTEXT 全文索引（title + summary + description）
--    ngram 分词器支持中文（同博客 ft_blog_title_content），搜索接口用
--      MATCH(r.title, r.summary, r.description) AGAINST(#{keyword} IN BOOLEAN MODE)
--    命中。三列合建一个复合全文索引，覆盖资源搜索的关键词匹配范围。
-- ----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND INDEX_NAME = 'ft_resource_title_summary_desc'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `resource` ADD FULLTEXT KEY `ft_resource_title_summary_desc` (`title`, `summary`, `description`) WITH PARSER ngram',
  'SELECT ''resource.ft_resource_title_summary_desc 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证：
--   SHOW INDEX FROM `resource` WHERE Index_type = 'FULLTEXT';
--   -- 应见 ft_resource_title_summary_desc，Index_type=FULLTEXT