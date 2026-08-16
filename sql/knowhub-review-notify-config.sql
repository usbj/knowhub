-- ============================================================================
-- 文件作用：
--   新增系统设置项「提审通知」（作品进入 PENDING_REVIEW 时按角色 role_key 发站内通知）。
--   四模块（blog/article/project/resource）publishXxx 在 reviewEnabled 分支经
--   ReviewNotifyService.notifyReviewers 调用——按 knowhub.review.notify_role_key 解析
--   持有该 role_key 且双方有效(sys_role/sys_user status=1 delete=0)的用户 list 后逐一发通知，
--   notifyUser 失败由 NotifySupport try/catch 吞掉不阻断发布主流程。
--
-- 设置项：
--   knowhub.review.notify_enabled (BOOLEAN, 默认 false)：总开关。
--       false → 不通知任何人（默认行为,与现状一致,避免配置未配即打扰）;
--       true  → 按 notify_role_key 找持该角色的有效用户后逐一发通知。
--   knowhub.review.notify_role_key (STRING, 默认 "admin"):被通知角色的 role_key。
--       持有该 role_key 且 sys_role/sys_user 双方有效(status=1, delete=0)的用户都会收到通知。
--       与 sys_role 内置 role_key='admin' 对齐作为兜底默认值。
--
-- 幂等：INSERT IGNORE 重跑安全；不占固定 config_id（自增），与 knowhub-article.sql/article 等
--       review_enabled 同套路（详见 sql/knowhub-sys-config-migration.sql INSERT IGNORE 用法）。
-- ============================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `sys_config`
    (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES
    ('knowhub.review.notify_enabled', '提审通知开关', 'false', 'BOOLEAN', 1,
     '作品(博客/文章/项目/资源)发布进入待审核(PENDING_REVIEW)时是否按角色通知。true=按 notify_role_key 角色发站内通知;false=不发(默认)。ReviewNotifyConfigReader.isNotifyEnabled 读取',
     1, NOW(), 'admin', NOW(), 'admin'),
    ('knowhub.review.notify_role_key', '提审通知角色键', 'admin', 'STRING', 1,
     '提审通知发送到的角色 role_key。持有该 role_key 且 sys_role/sys_user 双方有效(status=1,delete=0)的用户都收到站内通知。默认 admin(与 sys_role 内置 role_key 对齐)。ReviewNotifyConfigReader.getNotifyRoleKey 读取',
     1, NOW(), 'admin', NOW(), 'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   SELECT config_key, config_name, config_value, value_type, status
--   FROM sys_config WHERE config_key LIKE 'knowhub.review.notify%';
--   -- 应 2 行：notify_enabled=false/BOOLEAN + notify_role_key=admin/STRING
--
-- 启用方式（在后台 系统设置 列表面板修改即可即时生效，无需重启）：
--   UPDATE sys_config SET config_value='true' WHERE config_key='knowhub.review.notify_enabled';
--   UPDATE sys_config SET config_value='admin' WHERE config_key='knowhub.review.notify_role_key';
--   然后 SysConfigServiceImpl 自带缓存重写（编辑设置项触发），或重启清缓存。
--   若想发给多个角色：目前只支持单 role_key。若要扩展为多角色,后续改为 JSON 数组 +
--   SysConfigUtil.getList(key, String.class, default)，本轮不做。
-- ----------------------------------------------------------------------------