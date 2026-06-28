-- rookie 超级管理员通知管理菜单授权脚本
-- 角色 ID 1: 超级管理员
-- 配合 sys_notice_menu.sql 使用，先执行菜单插入再执行本文件

DELETE FROM sys_role_menu
WHERE role_id = 1
  AND menu_id IN (
    36, 37, 38, 39, 40, 41, 42, 43, 44,
    45, 46, 47, 48, 49, 50, 51
  );

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 36), (1, 37), (1, 38), (1, 39), (1, 40),
  (1, 41), (1, 42), (1, 43), (1, 44), (1, 45),
  (1, 46), (1, 47), (1, 48), (1, 49), (1, 50), (1, 51);
