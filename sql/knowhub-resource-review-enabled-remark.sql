-- ============================================================================
-- 文件作用：
--   刷新系统设置项 `knowhub.resource.review_enabled` 的说明文案（config_name + remark），
--   明确其职责是"资源发布审核开关"，并显式声明与"前台分级推荐开关"
--   (knowhub.portal.hierarchical.enabled) 是两个不同配置项，避免后台 sys_config
--   列表里两条相邻 BOOLEAN 开关被误判为同一项。
--
-- 背景：
--   用户反馈："前台分级推荐开关这个设置的键是资源是否开启审核的键，我不知道是什么情况。
--              如果前台分级推荐还没做的话那就先别加设置，我一会统一调配。"
--   实际现状（代码核实，2026-08-17）：
--     - `knowhub.resource.review_enabled`（资源审核开关，DEFAULT false）
--       语义/调用链一直清晰：ResourceConfigReader.isReviewEnabled()，
--       仅控制资源 publish 走 PENDING_REVIEW 或直通 PUBLISHED。
--     - `knowhub.portal.hierarchical.enabled`（前台分级推荐开关，DEFAULT false，
--       见 sql/knowhub-blog-portal-config.sql config_id=9）是另一独立键，
--       控制前台推荐 feed 是否按登录用户 view 等级分级下发。
--     - 两者键名、职责、读取方都不同，**不存在错绑**；但后台 sys_config 列表上
--       两条相邻开关肉眼易混，故把资源审核开关的 remark 写明差异，管理员一眼能辨。
--   按用户拍板：**不动 knowhub.portal.hierarchical.enabled（分级推荐那条），
--   不新建任何分级推荐相关配置项**，用户后续统一调配，本脚本仅刷新资源审核开关的说明。
--
-- 幂等：纯 UPDATE，重跑安全；不改 config_value（保持业务运行态不被脚本干扰，
--       保留当前 DB 里的值，避免覆盖管理员已改设置）。
-- ============================================================================
SET NAMES utf8mb4;

-- 仅刷新 config_name + remark 文案，不动 value_type 不动 config_value
UPDATE `sys_config`
SET `config_name` = '资源审核开关',
    `remark`      = '资源发布是否需审核(true=开启:发布进入PENDING_REVIEW等管理员审核;false=直通PUBLISHED)。仅控制资源发布审核流,与"前台分级推荐开关"knowhub.portal.hierarchical.enabled 是两项独立配置。ResourceConfigReader.isReviewEnabled 读取'
WHERE `config_key` = 'knowhub.resource.review_enabled';

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   SELECT config_key, config_name, config_value, value_type, remark
--   FROM sys_config
--   WHERE config_key IN ('knowhub.resource.review_enabled',
--                        'knowhub.portal.hierarchical.enabled');
--   -- 应 2 行：两条键名都存在、互不相同、remark 各自清晰无歧义
--
--   -- 资源审核开关的 config_value 应保持原值不变（本脚本不改 value）
--   SELECT config_value FROM sys_config WHERE config_key='knowhub.resource.review_enabled';
-- ----------------------------------------------------------------------------