-- rookie 超级管理员日志管理菜单授权脚本
-- 角色 ID 1: 超级管理员
-- 配合 sys_log_menu.sql 使用，先执行菜单插入再执行本文件

DELETE FROM sys_role_menu
WHERE role_id = 1
  AND menu_id IN (
    52, 53, 54, 55, 56, 57,
    58, 59, 60, 61, 62
  );

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 52), (1, 53), (1, 54), (1, 55), (1, 56), (1, 57),
  (1, 58), (1, 59), (1, 60), (1, 61), (1, 62);
