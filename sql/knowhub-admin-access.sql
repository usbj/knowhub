-- ============================================================================
-- knowhub 后台登录权限闸：system:access 权限点菜单
-- ============================================================================
-- 目的：
--   配合 SysLoginServiceImpl.loginVerification 的后台登录权限闸（2026-08-19 引入），
--   防 visitor（前台注册默认角色）等无后台访问权限的用户登入后台。
--   机制：非 admin 用户登录 /login 时，需在 permissions 中持有 system:access 权限码
--   才放行；admin 走框架短路（UserDetailServiceImpl 按 roleKey=="admin" 标记 isAdmin，
--   且 admin 走 selectAllPermKey 自然含全部按钮权限），不依赖此菜单的 role_menu 绑定。
--
-- 本脚本只做一件事：插入 system:access 权限点菜单（menu_type=3，parent=1 系统模块根）。
--
-- 绑定策略（不预置 sys_role_menu，由运营按需配置）：
--   - admin（role_id=1）：无需绑，框架短路 + selectAllPermKey 已含本权限码。
--   - visitor（role_id=5）：**禁止绑**，绑了则前台注册用户可登后台，与防游客意图相悖。
--   - 未来新增的"后台运营角色"：在后台角色管理页勾选本权限键即可赋予后台登录权。
--
-- menu_id 续编：上一轮 knowhub-permission-overhaul.sql 用到 226，本轮 system:access 用 227。
--   若你的库 MAX(menu_id) 已 > 226（跑了其它后续模块），需手动把 227 顺延到 MAX+1。
-- ============================================================================

SET NAMES utf8mb4;

-- system:access 权限点（后台登录闸校验码），挂在系统模块根菜单（menu_id=1）下
INSERT IGNORE INTO `sys_menu`
    (menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon,
     status, create_by, create_time, update_by, update_time, `delete`)
VALUES
    (227, '后台访问', 'system:access', 1, 3, NULL, 0, NULL, NULL,
     1, 'admin', NOW(), 'admin', NOW(), 0);

-- 说明：不预置 sys_role_menu 绑定。
--   admin 靠框架短路不依赖 role_menu；visitor 不绑以阻止注册用户登后台。
--   运营需让某角色登后台时，在后台角色管理页为该角色勾选「系统模块 → 后台访问」权限键。
