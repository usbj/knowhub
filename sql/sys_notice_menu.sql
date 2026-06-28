-- rookie 通知管理菜单与按钮权限初始化脚本
-- 说明：
-- 1. 通知管理作为独立业务域，新建一级目录"通知管理"，下挂"通知内容""通知分组"两个菜单
-- 2. 菜单 path 指向前端 src/views 下的组件，由 dynamicRoutes.ts 自动匹配
--    通知内容 → /system/notice/notice-content/index  对应 src/views/system/notice/notice-content/index.vue
--    通知分组 → /system/notice/notice-group/index  对应 src/views/system/notice/notice-group/index.vue
-- 3. perm_key 与前端 src/constants/systemPermissions.ts 中权限 key 数组的首个值对齐
-- 4. 先执行本文件，再执行 sys_notice_menu_admin.sql 给超级管理员授权
-- 5. 采用 INSERT ... ON DUPLICATE KEY UPDATE 幂等写法，可重复执行

INSERT INTO sys_menu (
  menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`
) VALUES
  -- 一级目录：通知管理（parent 改为 1 即挂到系统模块下，由用户后续在 DB 中调整）
  (36, '通知管理', 'notice', 1, 1, 'notice', 0, NULL, 'notice', 1, 'admin', NOW(), 'admin', NOW(), 0),

  -- 内容管理（菜单，parent=36 通知管理）
  (37, '内容管理', 'system:notice', 36, 2, 'notice-content', 0, '/system/notice/notice-content/index', 'notice', 1, 'admin', NOW(), 'admin', NOW(), 0),
  -- 内容管理按钮权限
  (38, '通知查询', 'system:notice:quarry', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (39, '通知信息', 'system:notice:info', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (40, '通知新增', 'system:notice:add', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (41, '通知修改', 'system:notice:edit', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (42, '通知删除', 'system:notice:delete', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (43, '通知发布', 'system:notice:publish', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (44, '通知撤回', 'system:notice:revoke', 37, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),

  -- 分组管理（菜单，parent=36 通知管理）
  (45, '分组管理', 'system:noticeGroup', 36, 2, 'notice-group', 0, '/system/notice/notice-group/index', 'notice', 1, 'admin', NOW(), 'admin', NOW(), 0),
  -- 分组管理按钮权限
  (46, '分组查询', 'system:noticeGroup:quarry', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (47, '分组信息', 'system:noticeGroup:info', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (48, '分组新增', 'system:noticeGroup:add', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (49, '分组修改', 'system:noticeGroup:edit', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (50, '分组删除', 'system:noticeGroup:delete', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (51, '分组成员管理', 'system:noticeGroup:member', 45, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0)
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
