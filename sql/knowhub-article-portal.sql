-- ============================================================================
-- 文件作用：文章前台 portal 对接的建表补丁。
--   背景（详见 doc 实现：文档学习前台对接，照博客 portal 范式 2026-07-15）：
--     1) 文章新增收藏/点赞能力（现状仅 blog/resource 有）：article_collect + article_like
--        事实表，主表冗余 like_count/collect_count（照 blog 范式），供推荐多维打分与标签热度榜升级。
--     2) 章节正文可检索：chapter.content 加 FULLTEXT(content) ngram 索引；article 加 FULLTEXT(title,summary) ngram。
--        复用博客 ngram 先例（需 MySQL ngram_token_size=2，博客环境已具备）。
--   设计决策（用户拍板）：
--     - 新建 article_collect + article_like + 主表冗余 like_count/collect_count（照 blog_collect/blog_like）。
--     - 章节内容搜索加上并标出命中章节。
--     - 标签热度复用既有 /portal/tag/hot（其 article 分支维度由 BlogPortalMapper.xml 同步升级，不在本脚本）。
--   幂等：information_schema 判表/列/索引 + PREPARE，重复执行不报错。
--   无新菜单/字典/config（分级推荐开关复用博客 knowhub.portal.hierarchical.enabled，本脚本不占 ID）。
--   续编前已查：本脚本不涉及 sys_menu/sys_dict/sys_config，无需查 MAX。记 [[knowhub-sql-id-numbering-pitfall]]。
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. article_collect（文章收藏明细，照搬 blog_collect 结构）
--    PK(article_id,user_id) 去重；idx_ac_user 供"我的收藏"查询，idx_ac_article 供计数。
-- ----------------------------------------------------------------------------
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_collect'
);
SET @sql := IF(@tbl_exists = 0,
  'CREATE TABLE `article_collect` (\
    `article_id` bigint NOT NULL COMMENT ''文章ID'',\
    `user_id`    bigint NOT NULL COMMENT ''收藏用户ID'',\
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\
    PRIMARY KEY (`article_id`, `user_id`),\
    KEY `idx_ac_user` (`user_id`),\
    KEY `idx_ac_article` (`article_id`)\
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''文章收藏明细''',
  'SELECT ''article_collect 表已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. article_like（文章点赞明细，照搬 blog_like 结构）
-- ----------------------------------------------------------------------------
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_like'
);
SET @sql := IF(@tbl_exists = 0,
  'CREATE TABLE `article_like` (\
    `article_id` bigint NOT NULL COMMENT ''文章ID'',\
    `user_id`    bigint NOT NULL COMMENT ''点赞用户ID'',\
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\
    PRIMARY KEY (`article_id`, `user_id`),\
    KEY `idx_al_user` (`user_id`),\
    KEY `idx_al_article` (`article_id`)\
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''文章点赞明细''',
  'SELECT ''article_like 表已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. article 主表加 like_count / collect_count 列（照 blog 主表冗余列范式）
--    冗余列以 article_like/article_collect 为准，toggle 接口事务内同步递增/递减。
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'like_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `like_count` bigint NOT NULL DEFAULT 0 COMMENT ''点赞量(冗余,以 article_like 为准)''',
  'SELECT ''article.like_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'collect_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `collect_count` bigint NOT NULL DEFAULT 0 COMMENT ''收藏量(冗余,以 article_collect 为准)''',
  'SELECT ''article.collect_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND INDEX_NAME = 'idx_article_like_count'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `article` ADD KEY `idx_article_like_count` (`like_count`)',
  'SELECT ''idx_article_like_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND INDEX_NAME = 'idx_article_collect_count'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `article` ADD KEY `idx_article_collect_count` (`collect_count`)',
  'SELECT ''idx_article_collect_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. article 全文索引（标题+简介检索，照博客 ft_blog_title_content ngram 范式）
--    仅标题/简介（MEDIUMTEXT）入文章级检索；章节正文单独走 chapter 的 ft 索引。
-- ----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND INDEX_NAME = 'ft_article_title_summary'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `article` ADD FULLTEXT INDEX `ft_article_title_summary` (`title`, `summary`) WITH PARSER ngram',
  'SELECT ''ft_article_title_summary 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 5. chapter 全文索引（章节正文检索，content MEDIUMTEXT）
--    搜索章节内容时 MATCH(content) AGAINST(BOOLEAN MODE)，按文章聚合命中章节标出。
-- ----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chapter' AND INDEX_NAME = 'ft_chapter_content'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `chapter` ADD FULLTEXT INDEX `ft_chapter_content` (`content`) WITH PARSER ngram',
  'SELECT ''ft_chapter_content 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW COLUMNS FROM article_collect;   -- PK(article_id,user_id)+idx_ac_user+idx_ac_article
--    SHOW COLUMNS FROM article_like;      -- PK(article_id,user_id)+idx_al_user+idx_al_article
--    SHOW COLUMNS FROM article LIKE 'like_count';     -- like_count bigint NOT NULL DEFAULT 0
--    SHOW COLUMNS FROM article LIKE 'collect_count';  -- collect_count bigint NOT NULL DEFAULT 0
--    SHOW INDEX FROM article WHERE Index_type='FULLTEXT';  -- ft_article_title_summary
--    SHOW INDEX FROM chapter WHERE Index_type='FULLTEXT';  -- ft_chapter_content
-- ----------------------------------------------------------------------------