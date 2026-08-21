-- =============================================================================
-- knowhub 权限系统大修迁移脚本（2026-08-18）
-- -----------------------------------------------------------------------------
-- 背景：四模块（blog/article/project/resource）权限 perm_key 原拆 view/edit/download
--   三操作维度（knowhub:xxx:view:lN / edit:lN / download:lN），在新的权限规划下已无意义
--   （编辑统一为作者 OR admin 不分等级、download 闸用单键等级即可）。本次合并为单键
--   knowhub:xxx:lN（小写 l1/l2/l3），一个等级一个键，去掉操作维度拆分。
--
-- 配套改造（代码侧，不在本脚本）：
--   - 四模块 PermissionResolver 正则改 ^knowhub:xxx:l([1-3])$，record 单 level 字段。
--   - 搜索范围 portal where 片段 level <= userViewLevel + 1（L1 搜 L1+L2 带 lock，L2 搜全部 L3 带 lock）。
--   - 详情锁态 VO：越级 locked=true + lockReason + 敏感字段置空（文章锁章节大纲、项目/资源 description 可见）。
--   - 创作闸 assertCanCreateLevel（userView >= targetLevel 才能建，L2 可建 L1/L2）。
--   - 编辑口径统一 = 作者 OR admin（博客/文章/资源）；项目 = LEADER OR 作者 OR 成员can_edit OR admin（去系统 edit 分支）。
--   - 资源模块从零引入 level 分级（原 PUBLISHED 即全公开，无等级）。
--   - 文章贡献者申请加申请人 level >= 文章 level 校验。
--
-- 本脚本做 6 件事：
--   1. resource 表加 level 列（tinyint 1/2/3，缺省 L1，资源原无等级）+ 等级索引。
--   2. 新建 resource_level 字典（dict_id=42，data 171-173，对齐 blog_level=34 范式）。
--   3. 四模块新单键菜单插入（knowhub:xxx:l1/l2/l3，共 12 条，menu_id 续编 214-225）。
--   4. 四模块旧操作键菜单删除（view/edit/download:lN，按 perm_key 删，幂等）+ sys_role_menu 孤儿清理。
--   5. knowhub:authoring:user-search 撞车修复：article 管理菜单占 menu_id=136（knowhub-article.sql:183）
--      与 user-search 菜单（knowhub-project-portal.sql:40 原也用 136）撞车，INSERT IGNORE 后者静默跳过
--      → AuthoringUserController:38 对非 admin 全 403。本脚本用续编新 ID（226）重插 user-search，
--      同时修源文件 knowhub-project-portal.sql:40 改用 226（防未来重跑再撞）。
--   6. blog edit:l1-3 孤儿 role_menu 清理（cleanup.sql 已删菜单未删关联，本轮补）。
--
-- 续编前已查实际数据库 MAX（照 [[knowhub-sql-id-numbering-pitfall]]，rookie 上游可能新增模块占 ID）：
--   - 静态固定 menu_id 最大到 213（knowhub-file-storage-extensions.sql 占 212/213）。
--   - 审计模块 knowhub-audit.sql 占 162-195（固定）；系统监控 sys_system_monitor.sql 用 NOT EXISTS+MAX+1 动态占 16 条（约 196-211）。
--   - sys_dict 最大 41（audit_period_type）；sys_dict_data 最大 170（audit_voucher）。
--   故本轮 menu_id 续编从 214 起（214-225 新单键 12 条 + 226 user-search 1 条），
--     dict_id 续编 42（resource_level），dict_data_id 续编 171-173。
--   注：本脚本用固定 menu_id 续编（与 article/project 等级键现有范式一致，便于 grep 核对），
--       若你的库 MAX(menu_id) 已 > 213（跑了其它后续模块），需手动把 214-226 顺延到 MAX+1 起。
--       INSERT IGNORE 保证重跑幂等（已存在不重复插）。
-- -----------------------------------------------------------------------------
SET NAMES utf8mb4;
USE `knowhub`;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. resource 表加 level 列（tinyint 1/2/3，对标系统 knowhub:resource:lN 权限等级）
--    存量资源一律默认 L1（保持可见性不变：原 PUBLISHED 即全公开，L1 公开等价）。
--    幂等：用 information_schema 判断列是否存在，避免重复执行报错。
-- ============================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'level'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `resource` ADD COLUMN `level` tinyint NOT NULL DEFAULT 1 COMMENT ''资源等级1公开/2内部/3机密(对标系统knowhub:resource:lN权限等级,2026-08-18权限大修引入,缺省L1)'' AFTER `status`',
  'SELECT ''resource.level 列已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量资源回填 L1（NOT NULL DEFAULT 1 已自动给值，这里显式兜底确保无 NULL）
UPDATE `resource` SET `level` = 1 WHERE `level` IS NULL OR `level` < 1;

-- 等级索引（幂等：先查后建，避免重复建索引报错）
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND INDEX_NAME = 'idx_resource_level'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `resource` ADD KEY `idx_resource_level` (`level`)',
  'SELECT ''idx_resource_level 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. sys_dict / sys_dict_data：resource_level 字典（对标 blog_level=34 / article_level=31 / project_level=28）
--    dict_data_value=1/2/3 与 ResourceLevel 枚举 code + resource.level tinyint 对齐。
--    dict_id 续编 42（MAX=41 audit_period_type +1），dict_data_id 续编 171-173（MAX=170 audit_voucher +1）。
-- ============================================================================
INSERT IGNORE INTO `sys_dict` (dict_id,dict_name,dict_key,status,remake,create_time,create_by,update_time,update_by) VALUES (42,'资源等级','resource_level',1,'资源等级枚举(ResourceLevel,1公开/2内部/3机密,对标系统knowhub:resource:lN权限等级)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` (dict_data_id,dict_id,dict_key,dict_data_label,dict_data_value,remark,dict_data_sort,tag_type,tag_effect,css_class,ext_json,is_default,status,create_time,create_by,update_time,update_by) VALUES (171,42,'resource_level','公开','1','L1 公开,拥有 knowhub:resource:l1 及以上即可查看/下载',1,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` (dict_data_id,dict_id,dict_key,dict_data_label,dict_data_value,remark,dict_data_sort,tag_type,tag_effect,css_class,ext_json,is_default,status,create_time,create_by,update_time,update_by) VALUES (172,42,'resource_level','内部','2','L2 内部,需 knowhub:resource:l2 及以上',2,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` (dict_data_id,dict_id,dict_key,dict_data_label,dict_data_value,remark,dict_data_sort,tag_type,tag_effect,css_class,ext_json,is_default,status,create_time,create_by,update_time,update_by) VALUES (173,42,'resource_level','机密','3','L3 机密,需 knowhub:resource:l3 及以上(最高)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ============================================================================
-- 3. 四模块新单键菜单插入（knowhub:xxx:l1/l2/l3，menu_type=3 隐形按钮节，仅承载权限键供角色勾选）
--    parent_id 对齐各模块管理菜单：blog=64 / article=136 / project=117 / resource=86。
--    menu_id 续编 214-225（MAX=213 文件扩展 +1 起，12 条）。
--    INSERT IGNORE 幂等：已存在不重复插。
--    用显式列名（不含 sort 列，sys_menu_sort.sql 新增的 sort 用默认 0）——避免依赖列序，
--    老 VALUES(...15 值) 范式在 sort 列已加入库后会因列数不匹配报 [21S01][1136]。
-- ============================================================================
-- blog（parent=64 博客"文章管理"菜单，knowhub-blog.sql:138）
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (214,'等级L1','knowhub:blog:l1',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (215,'等级L2','knowhub:blog:l2',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (216,'等级L3','knowhub:blog:l3',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- article（parent=136 文章管理菜单，knowhub-article.sql:183）
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (217,'等级L1','knowhub:article:l1',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (218,'等级L2','knowhub:article:l2',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (219,'等级L3','knowhub:article:l3',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- project（parent=117 项目管理菜单，knowhub-project.sql:189 上方）
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (220,'等级L1','knowhub:project:l1',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (221,'等级L2','knowhub:project:l2',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (222,'等级L3','knowhub:project:l3',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- resource（parent=86 资源管理菜单，knowhub-resource.sql:181）
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (223,'等级L1','knowhub:resource:l1',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (224,'等级L2','knowhub:resource:l2',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (225,'等级L3','knowhub:resource:l3',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 4. 四模块旧操作键菜单删除（view/edit/download:lN，按 perm_key 删，幂等）
--    旧菜单 menu_id：blog view:l1-3 原 159-161（但 knowhub-blog-view-perm-patch.sql 已改动态 MAX+1
--      兜底，实际 menu_id 不固定，故必须按 perm_key 删）；blog edit:l1-3 原 162-164（cleanup.sql 已删）；
--      article view:l1-3=145-147 + edit:l1-3=148-150；project view:l1-3=127-129 + download:l1-3=130-132 + edit:l1-3=133-135。
--    一律按 perm_key 删（不认死 menu_id，无视存量 patch 是否跑过、无视 stock DB 还是 mixed DB 都清干净）。
-- ============================================================================
DELETE FROM `sys_menu`
WHERE `perm_key` IN (
  -- blog 旧操作键（view 保留转单键、edit 早已 cleanup 删但兜底再删一次）
  'knowhub:blog:view:l1', 'knowhub:blog:view:l2', 'knowhub:blog:view:l3',
  'knowhub:blog:edit:l1', 'knowhub:blog:edit:l2', 'knowhub:blog:edit:l3',
  -- article 旧操作键（view+edit 合并到单键）
  'knowhub:article:view:l1', 'knowhub:article:view:l2', 'knowhub:article:view:l3',
  'knowhub:article:edit:l1', 'knowhub:article:edit:l2', 'knowhub:article:edit:l3',
  -- project 旧操作键（view+download+edit 合并到单键）
  'knowhub:project:view:l1', 'knowhub:project:view:l2', 'knowhub:project:view:l3',
  'knowhub:project:download:l1', 'knowhub:project:download:l2', 'knowhub:project:download:l3',
  'knowhub:project:edit:l1', 'knowhub:project:edit:l2', 'knowhub:project:edit:l3'
);

-- sys_role_menu 孤儿清理：上述菜单删除后，role_menu 关联行变孤儿（菜单没了查不到）。
-- 按 perm_key 反查 menu_id 再删 role_menu（菜单已删的也靠 NOT EXISTS/LEFT JOIN 兜底）。
--   先删 menu 还在的 role_menu（按 perm_key JOIN）；menu 已删的 role_menu 孤儿在下方 blog edit 段统一清。
DELETE rm FROM `sys_role_menu` rm
INNER JOIN `sys_menu` m ON rm.menu_id = m.menu_id
WHERE m.perm_key IN (
  'knowhub:blog:view:l1', 'knowhub:blog:view:l2', 'knowhub:blog:view:l3',
  'knowhub:blog:edit:l1', 'knowhub:blog:edit:l2', 'knowhub:blog:edit:l3',
  'knowhub:article:view:l1', 'knowhub:article:view:l2', 'knowhub:article:view:l3',
  'knowhub:article:edit:l1', 'knowhub:article:edit:l2', 'knowhub:article:edit:l3',
  'knowhub:project:view:l1', 'knowhub:project:view:l2', 'knowhub:project:view:l3',
  'knowhub:project:download:l1', 'knowhub:project:download:l2', 'knowhub:project:download:l3',
  'knowhub:project:edit:l1', 'knowhub:project:edit:l2', 'knowhub:project:edit:l3'
);

-- ============================================================================
-- 5. knowhub:authoring:user-search 撞车修复
--    根因：knowhub-project-portal.sql:40 原写 INSERT IGNORE sys_menu VALUES (136,'前台选人','knowhub:authoring:user-search',...)
--    但 knowhub-article.sql:183 已用 menu_id=136 插文章管理菜单（INSERT IGNORE，先执行者占 136）。
--    两个脚本执行顺序不可控：article.sql 先跑则占 136 → project-portal.sql 的 user-search 静默跳过
--    → 该权限键在 sys_menu 不存在 → AuthoringUserController:38 对非 admin 全 403。
--    修复：用续编新 menu_id=226 重插 user-search（article 的 136 保留不动）。
-- ============================================================================
INSERT IGNORE INTO `sys_menu` (menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,create_by,create_time,update_by,update_time,`delete`) VALUES (226,'前台选人','knowhub:authoring:user-search',63,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- 给前台游客角色（visitor, role_id=5，注册默认绑定）挂 user-search 菜单，否则普通前台创作者调
--   /authoring/user/search 仍 403（admin 走运行时 isAdmin 直通兜底拿全菜单，无需挂）。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (5, 226);

-- ============================================================================
-- 6. blog edit:l1-3 孤儿 role_menu 清理
--    knowhub-blog-edit-level-cleanup.sql 已删 sys_menu 里 edit:l1-3 菜单（perm_key 删，幂等），
--    但未删 sys_role_menu 关联（注释说"可选不强制"）。若该 cleanup 跑过，menu 162-164 已不存在，
--    role_menu 里 (role_id, 162/163/164) 成孤儿。本轮补清（按 menu_id，幂等：影响 0 行不报错）。
--    注：若库未跑过 cleanup.sql，上方第 4 段已按 perm_key 删了菜单 + role_menu，此处再按 id 删是兜底。
-- ============================================================================
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (162, 163, 164);

-- ============================================================================
-- 7. 可选：给 admin 角色（rookie 标准 role_id=1）绑新单键权限键的 sys_role_menu 关联
--    admin 实际不依赖此——rookie 框架 SysLoginServiceImpl.selectAllPermKey 给 admin 取全 sys_menu
--    perm_key（运行时 isAdmin 直通兜底），且 AdminBypassExpressionRoot 在接口/按钮显隐/service 判定
--    全程短路放行。此处补绑仅为"角色管理页能看到 admin 勾了这些键"的展示干净，不补也无功能影响。
--    幂等：按 (role_id, menu_id) NOT EXISTS 守护。
-- ============================================================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.menu_id
FROM `sys_menu` m
WHERE m.`perm_key` IN (
  'knowhub:blog:l1', 'knowhub:blog:l2', 'knowhub:blog:l3',
  'knowhub:article:l1', 'knowhub:article:l2', 'knowhub:article:l3',
  'knowhub:project:l1', 'knowhub:project:l2', 'knowhub:project:l3',
  'knowhub:resource:l1', 'knowhub:resource:l2', 'knowhub:resource:l3'
)
  AND m.`delete` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 验证提示（不自动执行，供人工核对）：
--   1) resource.level 列 + 索引：
--      SHOW COLUMNS FROM resource LIKE 'level';        -- 应 1 行 tinyint NOT NULL DEFAULT 1
--      SHOW INDEX FROM resource WHERE Key_name='idx_resource_level';  -- 应 1 行
--
--   2) resource_level 字典：
--      SELECT dict_id,dict_key FROM sys_dict WHERE dict_id = 42;        -- 42 resource_level
--      SELECT dict_data_value,dict_data_label FROM sys_dict_data
--      WHERE dict_id = 42 ORDER BY dict_data_sort;                      -- 1公开/2内部/3机密
--
--   3) 四模块新单键菜单（应 12 行）：
--      SELECT menu_id,menu_name,perm_key,parent_id FROM sys_menu
--      WHERE perm_key LIKE 'knowhub:%:l%' AND perm_key NOT LIKE '%:%:l%'
--         OR perm_key IN ('knowhub:blog:l1','knowhub:blog:l2','knowhub:blog:l3',
--                         'knowhub:article:l1','knowhub:article:l2','knowhub:article:l3',
--                         'knowhub:project:l1','knowhub:project:l2','knowhub:project:l3',
--                         'knowhub:resource:l1','knowhub:resource:l2','knowhub:resource:l3')
--      ORDER BY perm_key;   -- 应 12 行单键 l1/l2/l3 × 四模块
--
--   4) 旧操作键菜单应全删（应 0 行）：
--      SELECT menu_id,perm_key FROM sys_menu
--      WHERE perm_key LIKE 'knowhub:blog:view:l%'
--         OR perm_key LIKE 'knowhub:blog:edit:l%'
--         OR perm_key LIKE 'knowhub:article:view:l%'
--         OR perm_key LIKE 'knowhub:article:edit:l%'
--         OR perm_key LIKE 'knowhub:project:view:l%'
--         OR perm_key LIKE 'knowhub:project:download:l%'
--         OR perm_key LIKE 'knowhub:project:edit:l%';   -- 应 0 行
--
--   5) user-search 菜单（应 1 行 menu_id=226，不再撞 136）：
--      SELECT menu_id,menu_name,perm_key FROM sys_menu
--      WHERE perm_key='knowhub:authoring:user-search';  -- 应 1 行 226
--      SELECT menu_id,perm_key FROM sys_menu WHERE menu_id=136;  -- 应 1 行 文章管理 knowhub:article
--
--   6) blog edit 孤儿 role_menu（应 0 行）：
--      SELECT * FROM sys_role_menu WHERE menu_id IN (162,163,164);  -- 应 0 行
--
--   7) admin 新单键绑定（应 12 行，可选）：
--      SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.menu_id
--      WHERE rm.role_id=1 AND m.perm_key LIKE 'knowhub:%:l_';  -- 应 12
--
--   8) 跑完后 admin/绑了新键的角色需重新登录（缓存的 UserInfo.permissions 需重登刷新）。
--      后台角色管理页给"内部成员/正式成员"等运营角色勾对应 lN 权限键（本脚本不预置角色，
--      仅保证权限点菜单存在；角色创建 + 绑定由运营在后台角色管理页手工做）。
-- =============================================================================

-- ============================================================================
-- 源文件同步修复提醒（本脚本不改源 SQL 文件，需手工同步防未来重跑再撞）：
--   knowhub-project-portal.sql:40 把 menu_id=136 改成 226：
--     INSERT IGNORE INTO `sys_menu` VALUES (136,'前台选人','knowhub:authoring:user-search',63,3,...)
--     → INSERT IGNORE INTO `sys_menu` VALUES (226,'前台选人','knowhub:authoring:user-search',63,3,...)
--   （本脚本已用 226 重插，但源文件不改则未来重跑 knowhub-project-portal.sql 又会撞 136 静默跳过）
-- ============================================================================
