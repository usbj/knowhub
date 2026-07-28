-- ============================================================================
-- 文件作用：浏览历史统一事实表 + 文章标签关联表 + 三主表加 view_count 列。
--   背景（详见 doc/blog-portal-and-view-history-plan.md 第二条链路）：
--     1) user_view_history 统三模块(BLOG/ARTICLE/CHAPTER/RESOURCE)浏览明细，UNIQUE(user_id,biz_type,biz_id)
--        去重防刷——同用户同内容只一行，view_count 累加，主表 view_count 只在首次 INSERT+1=独立访客数。
--     2) article_tag 照搬 blog_tag 结构（文章复用既有 tag 表，不新建 tag）。
--     3) article/chapter/resource 主表各加 view_count bigint 冗余列（读快），blog 已有该列不动。
--   幂等：information_schema 判列/判表/判索引 + PREPARE，重复执行不报错。
--   无新菜单/字典/config，不占 menu_id/dict_id/config_id（ALTER 与建表均不续编这些 ID）。
--   续编前已查：本脚本不涉及 sys_menu/sys_dict/sys_config 续编，无需查 MAX。记 [[knowhub-sql-id-numbering-pitfall]]。
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. user_view_history（统一浏览明细事实表，三模块共用，防刷核心）
--    同用户同内容只一行 = 去重防刷；view_count 累计访问次数；biz_type 用 varchar 枚举
--    （BLOG/ARTICLE/CHAPTER/RESOURCE），biz_id 用 bigint 统三模块主键。
-- ----------------------------------------------------------------------------
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_view_history'
);
SET @sql := IF(@tbl_exists = 0,
  'CREATE TABLE `user_view_history` (\
    `view_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT ''主键'',\
    `user_id`         bigint       NOT NULL COMMENT ''用户ID'',\
    `biz_type`        varchar(16)  NOT NULL COMMENT ''业务类型 BLOG/ARTICLE/CHAPTER/RESOURCE'',\
    `biz_id`          bigint       NOT NULL COMMENT ''业务ID(各模块主键)'',\
    `view_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''首次浏览时间'',\
    `last_view_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最近浏览时间'',\
    `view_count`       int          NOT NULL DEFAULT 1 COMMENT ''同一内容累计浏览次数'',\
    PRIMARY KEY (`view_id`),\
    UNIQUE KEY `uk_uvh_user_biz` (`user_id`, `biz_type`, `biz_id`),\
    KEY `idx_uvh_user_time` (`user_id`, `view_time`),\
    KEY `idx_uvh_biz` (`biz_type`, `biz_id`)\
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''统一浏览明细事实表(防刷去重)''',
  'SELECT ''user_view_history 表已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. article_tag（文章-标签关联表，照搬 blog_tag 结构）
--    文章复用既有 tag 表（不新建 tag），仅建文章侧关联。PK(article_id,tag_id)+双索引。
-- ----------------------------------------------------------------------------
SET @tbl_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_tag'
);
SET @sql := IF(@tbl_exists = 0,
  'CREATE TABLE `article_tag` (\
    `article_id` bigint NOT NULL COMMENT ''文章ID'',\
    `tag_id`     bigint NOT NULL COMMENT ''标签ID'',\
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\
    PRIMARY KEY (`article_id`, `tag_id`),\
    KEY `idx_at_tag` (`tag_id`),\
    KEY `idx_at_article` (`article_id`)\
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''文章-标签关联表(多对多)''',
  'SELECT ''article_tag 表已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. article 主表加 view_count 列（bigint 冗余列读快，存量 0）
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'view_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `view_count` bigint NOT NULL DEFAULT 0 COMMENT ''浏览量(独立访客数,user_view_history 首次INSERT+1)''',
  'SELECT ''article.view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND INDEX_NAME = 'idx_article_view_count'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `article` ADD KEY `idx_article_view_count` (`view_count`)',
  'SELECT ''idx_article_view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. chapter 主表加 view_count 列
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chapter' AND COLUMN_NAME = 'view_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `chapter` ADD COLUMN `view_count` bigint NOT NULL DEFAULT 0 COMMENT ''浏览量(独立访客数,user_view_history 首次INSERT+1)''',
  'SELECT ''chapter.view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chapter' AND INDEX_NAME = 'idx_chapter_view_count'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `chapter` ADD KEY `idx_chapter_view_count` (`view_count`)',
  'SELECT ''idx_chapter_view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 5. resource 主表加 view_count 列
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'view_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `resource` ADD COLUMN `view_count` bigint NOT NULL DEFAULT 0 COMMENT ''浏览量(独立访客数,user_view_history 首次INSERT+1;与 download_count 正交)''',
  'SELECT ''resource.view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND INDEX_NAME = 'idx_resource_view_count'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `resource` ADD KEY `idx_resource_view_count` (`view_count`)',
  'SELECT ''idx_resource_view_count 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW COLUMNS FROM user_view_history;   -- 应含 view_count int + 三索引(uk_uvh_user_biz/idx_uvh_user_time/idx_uvh_biz)
--    SHOW COLUMNS FROM article_tag;         -- PK(article_id,tag_id)+双索引
--    SHOW COLUMNS FROM article LIKE 'view_count';
--    SHOW COLUMNS FROM chapter  LIKE 'view_count';
--    SHOW COLUMNS FROM resource LIKE 'view_count';  -- 三表均应有 view_count bigint NOT NULL DEFAULT 0
-- ----------------------------------------------------------------------------