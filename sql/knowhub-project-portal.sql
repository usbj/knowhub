-- =============================================================================
-- knowhub 项目前台门户 / 创作对接 补充脚本（在 knowhub-project.sql + knowhub-project-patch.sql 已运行基础上追加）
-- -----------------------------------------------------------------------------
-- 用途：项目前台门户（/portal/project/*）+ 项目创作（/authoring/project/**）+ 轻量选人接口
--   （/authoring/user/search）所需的库表与权限项增量。后端 portal 链路照博客/文章 portal 范式，
--   推荐打分依赖项目主表计数字段（view_count 等），原 knowhub-project.sql 未建计数列，本脚本补齐。
--
-- 续编前必查实际数据库（照 knowhub-sql-id-numbering-pitfall，rookie 上游可能新增模块占 ID）：
--   SHOW ... MAX(menu_id);  MAX(role_id);  -- 本脚本从 MAX(menu_id)>=135 续 136 起
--
-- 幂等：列用 ALTER（MySQL 无 IF NOT EXISTS，重跑靠脚本幂等——重跑前先核对列是否已存在，已存在跳过本段）；
--   菜单/角色菜单用 INSERT IGNORE。SET NAMES utf8mb4 保证中文不乱码（见 doc/README.dev.md SQL 字符约定）。
-- =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. project 主表 补计数字段（对齐博客/资源主表冗余计数，供前台推荐打分 + 卡片展示）
--    view_count 由统一浏览历史回写（见 ViewHistoryServiceImpl + 本脚本相配合的 ViewBizType.PROJECT 枚举与
--      UserViewHistoryMapper.xml分TABLE列名）；download_count 由下载接口成功时 +1；
--      like_count/collect_count 预留，当前项目尚无点赞/收藏接口（后续互动模块给前台时回写）。
--    类比：blog 主表 view_count/like_count/collect_count（knowhub-blog.sql 已建），resource 同。
--    DDL 形态对齐 blog：bigintNOT NULL DEFAULT 0，软删不再处理（计数随主表生命周期）。
--    备注：MySQL ALTER ADD COLUMN 无 IF NOT EXISTS，重跑会报 Duplicate column;
--      本段为一次性增量，已运行过的库需手动跳过。
-- ----------------------------------------------------------------------------
ALTER TABLE `project`
    ADD COLUMN `view_count`     bigint NOT NULL DEFAULT 0 COMMENT '浏览量(独立访客,统一浏览历史回写,推荐打分用)' AFTER `publish_time`,
    ADD COLUMN `like_count`     bigint NOT NULL DEFAULT 0 COMMENT '点赞数(预留,互动接口落地后回写)' AFTER `view_count`,
    ADD COLUMN `collect_count`  bigint NOT NULL DEFAULT 0 COMMENT '收藏数(预留,互动接口落地后回写)' AFTER `like_count`,
    ADD COLUMN `download_count` bigint NOT NULL DEFAULT 0 COMMENT '下载量(下载接口成功+1,推荐打分用+卡片展示)' AFTER `collect_count`;

-- ----------------------------------------------------------------------------
-- 2. 菜单：轻量选人权限项（前台项目创作者添加成员时选人用，不依赖 system:user:quarry）
--    速度：顶部"按钮"型权限（menu_type=3），perm_key=knowhub:authoring:user-search。
--    登录态即可创作项目（/authoring/project/** 走 isAuthenticated 兜底，无按钮权限键），
--      但"选人添加"接口需此权限——默认分配给"实验室成员"角色（该角色待创建，见 sys_role_menu 补段）+ admin。
--    续编从 226 起（2026-08-18 权限大修修撞车：原 136 与 knowhub-article.sql:183 文章管理菜单撞车，
--      article.sql 先跑占 136 → 本菜单 INSERT IGNORE 静默跳过 → AuthoringUserController:38 非 admin 403。
--      现 knowhub-permission-overhaul.sql 用 226 重插修复，本源文件同步改 226 防未来重跑再撞）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES (226,'前台选人','knowhub:authoring:user-search',63,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ----------------------------------------------------------------------------
-- 3. 角色-菜单绑定：admin 已自动全权限（无需显式绑）
--    「实验室成员」角色当前尚未在 sys_role 建立（见把范围局限于"接口已具备权限点+待角色到位"，
--    角色创建 + 绑定权限由后续团队类型权限细化任务统一做），故本脚本不写 sys_role_menu 绑定行。
--    待实验室成员角色(role_id 待定)创建后，追加：
--      INSERT IGNORE INTO sys_role_menu VALUES (<lab_member_role_id>, 226);
--
-- 验证提示（不自动执行，供人工核对）：
--   SHOW COLUMNS FROM project LIKE '%_count';  -- 应 4 行 view/like/collect/download_count
--   SELECT menu_id,menu_name,perm_key FROM sys_menu WHERE perm_key='knowhub:authoring:user-search';  -- 应 1 行 226
-- ----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 1;