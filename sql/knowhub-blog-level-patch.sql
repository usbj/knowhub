-- ============================================================================
-- 文件作用：博客管理加 L1~L3 等级查询权限 patch（对齐文章模块范式）
--   背景：博客原只有按钮权限(quarry/info/add/edit/delete/publish/revoke/review)，
--         任意有 info 权限者能看全部博客，无等级概念。新文章模块已上轻量等级权限
--         (系统级 view/edit:l1-l3 + 作者归属，无成员表)，本次把博客也改造为同款：
--         blog 表加 level(1/2/3)，列表按 level<=userViewLevel OR author_id=userId 过滤，
--         操作按 canOp = userLvl(op)>=level OR author_id==userId 判定(作者全权不看等级)。
--   review 保持按钮权限(非等级)，与文章一致。
--   作者归属对齐文章(用户拍板)：作者全权＝能改/发/撤/审自己的博客，不看等级。
-- ============================================================================
-- 续编前已查实际数据库 MAX(menu_id)=158、MAX(dict_id)=33、MAX(dict_data_id)=139。
-- (吸取 knowhub-sql-id-numbering-pitfall 教训：续编前先查 DB MAX，rookie 上游可能新增模块占 ID。)
--   menu_id 续编 159-164(6条：3 view:lN + 3 edit:lN)
--   dict_id 续编 34(blog_level)
--   dict_data_id 续编 140-142(3 等级值)
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. blog 表加 level 列（tinyint 1/2/3，对标系统 view/edit:lN 权限等级）
--    存量博客一律默认 L1(保持可见性不变)；加索引供列表等级过滤。
--    幂等：用 information_schema 判断列是否存在，避免重复执行报错。
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND COLUMN_NAME = 'level'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `blog` ADD COLUMN `level` tinyint NOT NULL DEFAULT 1 COMMENT ''博客等级1公开/2内部/3机密(对标系统view/edit:lN权限等级)'' AFTER `author_id`',
  'SELECT ''blog.level 列已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量博客回填 L1（NOT NULL DEFAULT 1 已自动给值，这里显式兜底确保无 NULL）
UPDATE `blog` SET `level` = 1 WHERE `level` IS NULL OR `level` < 1;

-- 等级索引（幂等：先删后建，避免重复建索引报错；blog 可能数据量小无妨）
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND INDEX_NAME = 'idx_blog_level'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `blog` ADD KEY `idx_blog_level` (`level`)',
  'SELECT ''idx_blog_level 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. sys_menu 续编：博客等级权限(view/edit:l1-l3)，menu_type=3 挂博客菜单 menu_id=64 下
--    对齐文章模块 menu 145-150 范式(隐形按钮节，不渲染为菜单项，仅承载权限键供角色勾选)。
--    BlogPermissionResolver 一次扫描 perms 取 view/edit 各自最高等级(admin 零特判)。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES (159,'查看L1','knowhub:blog:view:l1',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (160,'查看L2','knowhub:blog:view:l2',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (161,'查看L3','knowhub:blog:view:l3',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (162,'编辑L1','knowhub:blog:edit:l1',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (163,'编辑L2','knowhub:blog:edit:l2',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (164,'编辑L3','knowhub:blog:edit:l3',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ----------------------------------------------------------------------------
-- 3. sys_dict / sys_dict_data：blog_level 字典（对标 article_level=31）
--    dict_data_value=1/2/3 与 BlogLevel 枚举 code + blog.level tinyint 对齐。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict` VALUES (34,'博客等级','blog_level',1,'博客等级枚举(BlogLevel,1公开/2内部/3机密,对标系统view/edit:lN权限等级)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (140,34,'blog_level','公开','1','L1 公开,拥有 view:l1 及以上即可查看',1,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (141,34,'blog_level','内部','2','L2 内部,需 view:l2 及以上',2,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (142,34,'blog_level','机密','3','L3 机密,需 view:l3 及以上(最高)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW COLUMNS FROM blog;                                     -- 应含 level tinyint NOT NULL DEFAULT 1
--    SELECT menu_id,menu_name,perm_key,parent_id FROM sys_menu
--    WHERE perm_key LIKE 'knowhub:blog:%' ORDER BY menu_id;      -- 应 15 行:64+65-72(9旧)+159-164(6新等级)
--    SELECT dict_id,dict_key FROM sys_dict WHERE dict_id = 34;   -- 34 blog_level
--    SELECT dict_data_value,dict_data_label FROM sys_dict_data
--    WHERE dict_id = 34 ORDER BY dict_data_sort;                 -- 1公开/2内部/3机密
--  ----------------------------------------------------------------------------
