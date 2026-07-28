-- ============================================================================
-- 文件作用：前台门户分级推荐开关系统设置项（决策#8 唯一例外开关）。
--   背景：前台推荐/详情默认 level=1 二元闸（L2/L3 永不下发门户）；
--         本开关 true 时前台按登录用户实际 view 等级分级下发（level<=userViewLevel 阶梯闸）。
--         默认关 = 公开门户语义不变、无越级风险。
--   幂等：ON DUPLICATE KEY UPDATE 按 uk_config_key 幂等。
--   续编前已查实际数据库 MAX(config_id)=8（sql/sys_config.sql 占 1-3，
--   sql/knowhub-sys-config-migration.sql 占 4-8），本脚本续编 config_id=9。
--   记 [[knowhub-sql-id-numbering-pitfall]]：续编前应再跑 SELECT MAX(config_id) FROM sys_config; 确认线上无人插过 9+。
--   注意：value_type 必须为 BOOLEAN（SysConfigUtil.getBoolean 校验值类型，写错会回落默认值 false 而不报错）。
-- ============================================================================

INSERT INTO `sys_config` (`config_id`, `config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (9, 'knowhub.portal.hierarchical.enabled', '前台分级推荐开关',
        'false', 'BOOLEAN', 1,
        '前台博客推荐/详情默认仅下发 L1 公开内容(二元闸)；开启后按登录用户 view 等级分级下发 L2/L3(阶梯闸)。默认关=公开门户无越级风险。详见 doc/blog-portal-and-view-history-plan.md 决策#8。',
        1, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `config_name`=VALUES(`config_name`), `config_value`=VALUES(`config_value`), `value_type`=VALUES(`value_type`),
  `is_system`=VALUES(`is_system`), `remark`=VALUES(`remark`), `status`=VALUES(`status`);

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SELECT config_id,config_key,config_value,value_type,status FROM sys_config
--    WHERE config_key='knowhub.portal.hierarchical.enabled';  -- 应 1 行：9/false/BOOLEAN/1
-- ----------------------------------------------------------------------------