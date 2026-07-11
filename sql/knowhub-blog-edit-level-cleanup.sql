-- ============================================================================
-- 文件作用：博客编辑权限收紧清理 patch
--   背景：上一轮给博客加了文章同款的 L1~L3 等级权限(view/edit:lN + 作者归属)。
--         用户现改方向：博客编辑不再分等级——只有「作者本人 + 超级管理员」能改/发/撤，
--         即便有 knowhub:blog:edit 按钮权限也不能改别人的博客(service 层 isAuthor OR isAdmin 强判)。
--         查看等级(view:l1-l3 + 作者)保留不变(防低权用户看到机密博客的核心诉求)。
--   处理：删 sys_menu 里承载编辑等级权限键的三条菜单(edit:l1/l2/l3,menu 162-164)；
--         查看等级 menu 159-161 保留。编辑不再扫 perms 等级,BlogPermissionResolver 只剩 view 分支。
--   admin 走 rookie 06b3303 引入的框架短路(UserInfo.isAdmin + AdminBypassExpressionRoot),
--         进接口/前端按钮显隐/service 判定全程自洽,不另设 admin 权限键。
-- ============================================================================
-- 本次仅删 menu 162-164,不续编新 ID,无需查 MAX(吸取 [[knowhub-sql-id-numbering-pitfall]] 教训)。
-- ============================================================================

-- 删博客编辑等级权限菜单(新模型编辑不再分等级,仅作者+admin 可改)。
-- 幂等:perm_key 不存在时影响 0 行,不报错,可重复执行。
DELETE FROM `sys_menu`
WHERE `perm_key` IN ('knowhub:blog:edit:l1', 'knowhub:blog:edit:l2', 'knowhub:blog:edit:l3');

-- 角色-菜单关联表 sys_role_menu 里对 menu 162-164 的授权行随 menu 删除而失效(菜单没了查不到),
-- 无需单独清理;若想干净可执行下行(可选,不强制,幂等):
-- DELETE FROM `sys_role_menu` WHERE `menu_id` IN (162, 163, 164);

-- ----------------------------------------------------------------------------
-- 验证提示(不自动执行,供人工核对):
--    SELECT menu_id, menu_name, perm_key, parent_id FROM sys_menu
--    WHERE perm_key LIKE 'knowhub:blog:%' ORDER BY menu_id;
--    应 12 行: 64(博客菜单) + 65-72(9 旧按钮 quarry/info/add/edit/delete/publish/revoke/review
--             +1) + 159-161(3 view:lN) —— 不应再有 edit:l1/l2/l3。
-- ----------------------------------------------------------------------------
