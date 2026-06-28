-- rookie 系统菜单与按钮权限初始化脚本
-- 说明：
-- 1. 当前库里已有 system:user 这套权限前缀，本脚本保持同一命名风格
-- 2. 先执行本文件，再执行 sys_role_menu_admin.sql

INSERT INTO sys_menu (
  menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`
) VALUES
  (1, '系统模块', 'system', -1, 1, 'system', 0, NULL, 'system', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (2, '用户管理', 'system:user', 1, 2, 'user', 0, '/system/user/index', 'user', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (3, '用户查询', 'system:user:quarry', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (4, '用户信息', 'system:user:info', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (5, '用户修改', 'system:user:edit', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (6, '用户新增', 'system:user:add', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (7, '用户删除', 'system:user:delete', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (8, '角色管理', 'system:role', 1, 2, 'role', 0, '/system/role/index', 'role', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (9, '角色查询', 'system:role:quarry', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (10, '角色信息', 'system:role:info', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (11, '角色修改', 'system:role:edit', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (12, '角色新增', 'system:role:add', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (13, '角色删除', 'system:role:delete', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (14, '角色状态', 'system:role:status', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (15, '默认角色', 'system:role:default', 8, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (16, '菜单管理', 'system:menu', 1, 2, 'menu', 0, '/system/menu/index', 'menu', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (17, '菜单查询', 'system:menu:quarry', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (18, '菜单信息', 'system:menu:info', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (19, '菜单修改', 'system:menu:edit', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (20, '菜单新增', 'system:menu:add', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (21, '菜单删除', 'system:menu:delete', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (22, '菜单状态', 'system:menu:status', 16, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (23, '字典管理', 'system:dict', 1, 2, 'dict', 0, '/system/dict/index', 'dict', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (24, '字典查询', 'system:dict:quarry', 23, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (25, '字典信息', 'system:dict:info', 23, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (26, '字典修改', 'system:dict:edit', 23, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (27, '字典新增', 'system:dict:add', 23, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (28, '字典删除', 'system:dict:delete', 23, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (29, '字典数据', 'system:dictData', 1, 2, 'dictData', 0, '/system/dictData/index', 'dict', 1, 'admin', NOW(), 'admin', NOW(), 0),
  (30, '字典数据查询', 'system:dictData:quarry', 29, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (31, '字典数据信息', 'system:dictData:info', 29, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (32, '字典数据修改', 'system:dictData:edit', 29, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (33, '字典数据新增', 'system:dictData:add', 29, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (34, '字典数据删除', 'system:dictData:delete', 29, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (35, '用户状态', 'system:user:status', 2, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0)
AS new
ON DUPLICATE KEY UPDATE
  menu_name = new.menu_name,
  perm_key = new.perm_key,
  parent_id = new.parent_id,
  menu_type = new.menu_type,
  route = new.route,
  backlinks = new.backlinks,
  path = new.path,
  icon = new.icon,
  status = new.status,
  update_by = new.update_by,
  update_time = NOW(),
  `delete` = 0;
