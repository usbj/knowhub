-- rookie 日志管理菜单与按钮权限初始化脚本
-- 说明：
-- 1. 日志管理作为系统模块下的独立业务域，新建一级目录"日志管理"，下挂"操作日志""错误日志"两个菜单
-- 2. menu_id 自 52 起（通知模块占用 36~51），不与现有菜单冲突
-- 3. menu_type：1=目录 2=菜单 3=按钮，对齐 sys_notice_menu.sql 约定
-- 4. perm_key 采用 system:operLog:* / system:errorLog:* 风格，供前端 systemPermissions.ts 与 @PreAuthorize 使用
-- 5. 采用 INSERT ... ON DUPLICATE KEY UPDATE 幂等写法，可重复执行
-- 6. 先执行本文件，再执行 sys_log_menu_admin.sql 给超级管理员授权

INSERT INTO sys_menu (
  menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon, status, create_by, create_time, update_by, update_time, `delete`
) VALUES
  -- 一级目录：日志管理（parent=1 挂到系统模块下，可后续在 DB 调整）
  (52, '日志管理', 'log', 1, 1, 'log', 0, NULL, 'log', 1, 'admin', NOW(), 'admin', NOW(), 0),

  -- 操作日志（菜单，parent=52 日志管理）
  (53, '操作日志', 'system:operLog', 52, 2, 'oper-log', 0, '/system/log/oper-log/index', 'log', 1, 'admin', NOW(), 'admin', NOW(), 0),
  -- 操作日志按钮权限
  (54, '操作日志查询', 'system:operLog:quarry', 53, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (55, '操作日志详情', 'system:operLog:info', 53, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (56, '操作日志删除', 'system:operLog:delete', 53, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (57, '操作日志清空', 'system:operLog:clean', 53, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),

  -- 错误日志（菜单，parent=52 日志管理）
  (58, '错误日志', 'system:errorLog', 52, 2, 'error-log', 0, '/system/log/error-log/index', 'log', 1, 'admin', NOW(), 'admin', NOW(), 0),
  -- 错误日志按钮权限
  (59, '错误日志查询', 'system:errorLog:quarry', 58, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (60, '错误日志详情', 'system:errorLog:info', 58, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (61, '错误日志删除', 'system:errorLog:delete', 58, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0),
  (62, '错误日志清空', 'system:errorLog:clean', 58, 3, NULL, 0, NULL, NULL, 1, 'admin', NOW(), 'admin', NOW(), 0)
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
