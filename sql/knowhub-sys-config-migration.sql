-- =============================================================================
-- knowhub 配置型设置：从字典系统迁移到系统设置模块（sys_config）
-- -----------------------------------------------------------------------------
-- 背景：
--   rookie 层在 sql/sys_config.sql 新增了独立的系统设置模块（SysConfig/SysConfigUtil），
--   专门承载"后台可改、全站生效"的键值型配置。knowhub 的两个配置读取收口类
--   BlogConfigReader、StorageConfigReader 之前走字典系统（DictUtil）读取 5 个配置型字典键，
--   现统一迁移到 sys_config，让它们出现在「系统设置」管理页里维护。
--
--   注意区分两类字典，只迁配置型、不迁枚举型：
--   - 枚举型字典（前端下拉 + 后端校验共用，本脚本保留不动）：
--       file_business_type / file_access / upload_status
--   - 配置型字典（后台改阈值/开关，本脚本迁移后清理）：
--       blog_review_enabled       → knowhub.blog.review_enabled      (BOOLEAN)
--       file_access_mode          → knowhub.file.access_mode         (STRING)
--       file_direct_base_url      → knowhub.file.direct_base_url     (STRING)
--       file_size_limit (7行)     → knowhub.file.size_limit          (JSON 对象)
--       file_type_whitelist (7行) → knowhub.file.type_whitelist      (JSON 对象)
--
-- 本脚本包含：
--   1. 插入 5 条 knowhub 系统设置项（is_system=1, status=1，受内置项保护：禁删/禁改键/禁改类型/禁停用）
--   2. 清理 5 个孤儿配置型字典（sys_dict_data + sys_dict），枚举型字典保留
--
-- 前置依赖：需先运行 sql/sys_config.sql 建 sys_config 表并初始化 3 条 rookie 内置项（config_id 1~3）。
-- 运行库：与 application.yml 中 url 一致的业务库（knowhub 字典与 sys_config 同库）。
-- 幂等：插入用 INSERT ... ON DUPLICATE KEY UPDATE（按 uk_config_key 唯一键，重跑只更新值/名称/备注）；
--       清理用无条件 DELETE（重跑即删空，幂等）。
-- 编号：config_id 续 sql/sys_config.sql 的 3 之后，本脚本从 4 起（5 条用 4~8）。
-- =============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 插入 knowhub 系统设置项（is_system=1：内置项，仅可改值/名称/备注，禁删/禁改键与类型/禁停用）
--    config_value 初始值与原字典默认值对齐（blog_review_enabled 默认 false、file_access_mode 默认 transfer 等）。
--    JSON 项存「业务类型 code → 值」的对象，与 StorageConfigReader.sizeLimitBytes/typeWhitelist 的读取结构一致。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_config` (`config_id`, `config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  (4, 'knowhub.blog.review_enabled', '博客审核开关', 'false', 'BOOLEAN', 1, 'true 开启发布审核，false 直通发布（BlogConfigReader.isReviewEnabled 读取）', 1, 'admin', NOW(), 'admin', NOW()),
  (5, 'knowhub.file.access_mode',    '文件访问模式', 'transfer', 'STRING', 1, '文件访问模式：transfer 中转（后端代理 OSS 字节流）/ direct 直链（前端直连 nginx/OSS，见 FileAccessMode 枚举，StorageConfigReader.accessMode 读取）', 1, 'admin', NOW(), 'admin', NOW()),
  (6, 'knowhub.file.direct_base_url', '直链模式OSS地址', 'http://100.82.86.85:9000', 'STRING', 1, '直链模式对外暴露的 OSS 地址 base（nginx 公网反代域名或 OSS 公网 endpoint，留空回退 yml storage.endpoint，StorageConfigReader.directBaseUrl 读取；公网部署请改为可达地址）', 1, 'admin', NOW(), 'admin', NOW()),
  (7, 'knowhub.file.size_limit',     '各业务类型体积上限', '{"BLOG_COVER":5,"BLOG_BODY":10,"PROJECT_SRC":500,"PROJECT_PKG":500,"PROJECT_DOC":100,"RESOURCE_FILE":100,"PLUGIN_JAR":50}', 'JSON', 1, 'JSON 对象：key=业务类型 code（见 FileBusinessType 枚举），value=体积上限 MB 数；缺失业务类型回退默认 10MB（StorageConfigReader.sizeLimitBytes 读取）', 1, 'admin', NOW(), 'admin', NOW()),
  (8, 'knowhub.file.type_whitelist', '各业务类型白名单', '{"BLOG_COVER":"image/png,image/jpeg,image/gif,image/webp","BLOG_BODY":"image/png,image/jpeg,image/gif,image/webp","PROJECT_SRC":".zip,.tar,.gz,.7z,.rar","PROJECT_PKG":".zip,.exe,.msi,.dmg,.deb,.rpm,.apk","PROJECT_DOC":".docx,.pptx,.xlsx,.pdf","RESOURCE_FILE":"","PLUGIN_JAR":".jar"}', 'JSON', 1, 'JSON 对象：key=业务类型 code，value=逗号分隔的扩展名/MIME 白名单；留空或缺失表示不限制（StorageConfigReader.typeWhitelist 读取）', 1, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `config_name`  = VALUES(`config_name`),
  `config_value` = VALUES(`config_value`),
  `value_type`   = VALUES(`value_type`),
  `is_system`    = VALUES(`is_system`),
  `remark`       = VALUES(`remark`),
  `status`       = VALUES(`status`);

-- ----------------------------------------------------------------------------
-- 2. 清理 5 个孤儿配置型字典（已迁移到 sys_config，不再有代码读取）
--    先删 sys_dict_data（子表）再删 sys_dict（主表），按 dict_key 业务键定位，幂等可重跑。
--    枚举型字典 file_business_type / file_access / upload_status 不在此列表，保留不动。
-- ----------------------------------------------------------------------------
DELETE FROM `sys_dict_data` WHERE `dict_key` IN ('blog_review_enabled', 'file_size_limit', 'file_type_whitelist', 'file_access_mode', 'file_direct_base_url');
DELETE FROM `sys_dict`      WHERE `dict_key` IN ('blog_review_enabled', 'file_size_limit', 'file_type_whitelist', 'file_access_mode', 'file_direct_base_url');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   select config_key, config_value, value_type, is_system, status from sys_config;
--     应有 8 条（3 条 rookie 内置 sys.* + 5 条本脚本 knowhub.*）
--   select dict_key from sys_dict where dict_key in ('blog_review_enabled','file_size_limit','file_type_whitelist','file_access_mode','file_direct_base_url');
--     应为空（已清理）
--   select dict_key from sys_dict where dict_key in ('file_business_type','file_access','upload_status');
--     应有 3 条（枚举型保留）
-- ----------------------------------------------------------------------------
