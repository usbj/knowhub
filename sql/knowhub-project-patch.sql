-- =============================================================================
-- knowhub 项目管理模块 补充脚本（在 knowhub-project.sql 已运行基础上追加）
-- -----------------------------------------------------------------------------
-- 用途：原 knowhub-project.sql 的 project_type 字典只放了 COMPETITION 一项，
-- 用户明确要求项目类型补齐三种（比赛/练习/运维）。本脚本仅追加 dict_data，
-- 不改原 knowhub-project.sql，不动已建的 project_competition 表。
--
-- PRACTICE/OPS 暂无子表，所需属性由主表 description/summary/项目文件覆盖；
-- 后续若有特有字段需求，加子表 + 配套字典/前端表单，主表不动。
-- 续编：project_type 原 dict_id=26 已建，本次补 dict_data_id 从 121 起
-- （原 project_type 108/COMPETITION 已占，project_status 109-114、project_level 115-117、
--  project_member_role 118-120 已占，MAX(dict_data_id) 在原脚本验为 120）。
-- 幂等：INSERT IGNORE，重跑不冲突。
-- =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- 项目类型补齐：PRACTICE 练习项目 / OPS 运维项目（COMPETITION 108 已存在，INSERT IGNORE 跳过）
INSERT IGNORE INTO `sys_dict_data` VALUES (121,26,'project_type','练习项目','PRACTICE','练习类项目(当前无独立子表,属性由主表与文件覆盖)',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (122,26,'project_type','运维项目','OPS','运维类项目(当前无独立子表,属性由主表与文件覆盖)',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   SELECT dict_data_value,dict_data_label FROM sys_dict_data WHERE dict_key='project_type';
--                        -- 应 3 行：108 COMPETITION / 121 PRACTICE / 122 OPS
-- ----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 1;