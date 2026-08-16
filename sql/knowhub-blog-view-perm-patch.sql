-- ============================================================================
-- 文件作用：博客 view:l1/l2/l3 权限键补丁（修 Bug A：后台看不到他人待审核博客）
--   根因：博客 view:lN 等级权限键原本在 knowhub-blog-level-patch.sql 用 **固定 menu_id
--   159-161 + INSERT IGNORE** 注册。ID 在带审计模块的库里与审计菜单（audit 主菜单 162、
--   audit dict_data_id 159/160/161 使用同名数据）撞车，INSERT IGNORE 静默跳过 → view:lN 三键
--   在 sys_menu 根本不存在 → BlogPermissionResolver 对所有人返回 view=0 → BlogMapper.quarryBlog
--   权限过滤 `(level<=0 OR author_id=me)` 永远只有自己写的博客，admin 看不到他人 PENDING_REVIEW
--   博客。文章/项目 正常，是因为它们 view:lN 内联在主 SQL 用连续 menu_id。
--   修复：按 perm_key 兜底（不去认死 menu_id）：NOT EXISTS (perm_key=...) 守护下用
--        (SELECT COALESCE(MAX(menu_id),0)+1 FROM sys_menu) 作 menu_id 头部插入，任何 ID 冲突
--        都不再发生、也无视存量 patch 是否跑过、无视 stock DB 还是 mixed DB 都补成"view:l1/l2/l3
--        三键齐全"。幂等：每行 NOT EXISTS 守护，重复执行不重复插。
--   额外：给 admin 角色（rookie 标准 role_id=1）补绑 sys_role_menu 三条关联，幂等。文章/项目其实
--         未绑也能工作（rookie 框架 selectAllPermKey 给 admin 取全 sys_menu），但补上更稳。
--   schema：sys_menu 列序（rookie.sql 第 79-95 行）=
--     menu_id,menu_name,perm_key,parent_id,menu_type,route,backlinks,path,icon,status,
--     create_by,create_time,update_by,update_time,delete   (15 列)
--   不用续编固定 menu_id：MAX+1 子查询动态续编。吸取 [[knowhub-sql-id-numbering-pitfall]]：
--   跨库 MAX 不同（审计/评论/协作等续编各库不一），固定 ID 必撞，动态最安全。
-- ============================================================================

-- (1) view:l1/l2/l3 三键兜底插入：按 perm_key 判"不存在则插"，menu_id 取 MAX+1，parent_id=64
--    （博客"文章管理"菜单 knowhub-blog.sql 第 138 行固定 64），menu_type=3 隐形按钮节仅承载权限键。
--    原版文章 view:lN 用"15 列 VALUES(……)"形态，这里换成"列出列名 + SELECT"形态以便写 NOT EXISTS。
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`,
   `route`, `backlinks`, `path`, `icon`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT
  (SELECT COALESCE(MAX(t.menu_id), 0) + 1 FROM `sys_menu` t),
  '查看L1', 'knowhub:blog:view:l1', 64, 3,
  NULL, -1, NULL, NULL, 1,
  'admin', NOW(), 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'knowhub:blog:view:l1' AND `delete` = 0);

INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`,
   `route`, `backlinks`, `path`, `icon`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT
  (SELECT COALESCE(MAX(t.menu_id), 0) + 1 FROM `sys_menu` t),
  '查看L2', 'knowhub:blog:view:l2', 64, 3,
  NULL, -1, NULL, NULL, 1,
  'admin', NOW(), 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'knowhub:blog:view:l2' AND `delete` = 0);

INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`,
   `route`, `backlinks`, `path`, `icon`, `status`,
   `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT
  (SELECT COALESCE(MAX(t.menu_id), 0) + 1 FROM `sys_menu` t),
  '查看L3', 'knowhub:blog:view:l3', 64, 3,
  NULL, -1, NULL, NULL, 1,
  'admin', NOW(), 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perm_key` = 'knowhub:blog:view:l3' AND `delete` = 0);

-- (2) 给 admin 角色（rookie 标准 role_id=1）补绑这三条权限键的 sys_role_menu 关联。幂等：
--     按 (role_id, menu_id) 已存在则不插。
--     注：若你环境 admin 的 role_id 不是 1（被改造过），把下面的 role_id 改成你的 admin 角色 id。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.menu_id
FROM `sys_menu` m
WHERE m.`perm_key` IN ('knowhub:blog:view:l1', 'knowhub:blog:view:l2', 'knowhub:blog:view:l3')
  AND m.`delete` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm
    WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SELECT menu_id,menu_name,perm_key,parent_id,delete FROM sys_menu
--    WHERE perm_key LIKE 'knowhub:blog:view:l%' ORDER BY perm_key;    -- 应有 3 行 l1/l2/l3
--    SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.menu_id
--    WHERE rm.role_id=1 AND m.perm_key LIKE 'knowhub:blog:view:l%';   -- 应为 3
--    修复后 admin **重新登录**（缓存的 UserInfo.permissions 需重登刷新），在后台博客列表
--    按 status=待审核 应能看到他人提交的 PENDING_REVIEW 博客。
-- ----------------------------------------------------------------------------