SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 评论配图业务类型续编（评论配图增量）。
--
-- ⚠️ 续编前必查库：
--   SELECT MAX(dict_data_sort) FROM sys_dict_data WHERE dict_key='file_business_type';
--   排序值用 IFNULL(MAX(dict_data_sort),0)+1 续编，避免与已存在的装饰行排序值撞车。
--   跑下面装饰行 INSERT 前，请确认该查得的 MAX 已是无法再追加的既成事实。
--
-- 真正的校验数据源：sys_config JSON 设置项 knowhub.file.size_limit / knowhub.file.type_whitelist
--   （StorageConfigReader.typeWhitelist / sizeLimitBytes 读这两条 JSON 对象，按业务类型 code 取值）。
-- 故用 JSON_SET 增量补 COMMENT_IMAGE 项（不覆盖/不丢用户自定义的其它业务类型项），
--   若设置项缺失则 INSERT 一条带 COMMENT_IMAGE 的全量兜底行；并补 sys_dict_data 装饰行供后台字典展示。
-- access=PUBLIC（${FileAccess.PUBLIC}），仅图片四类，≤2MB（贴合评论区短评配图）。
--
-- 同时给前台默认角色（visitor, role_id=5，注册绑定）挂文件上传菜单（menu 82 knowhub:file:upload），
--   否则普通前台评论者调 /file/upload-token 会 403（admin 走运行时 isAdmin 直通兜底，无需挂）。
-- ============================================================================
-- java 侧 FileBusinessType 枚举需同步加 COMMENT_IMAGE("COMMENT_IMAGE","评论配图",FileAccess.PUBLIC)，
--   见 knowhub/.../enums/storage/FileBusinessType.java。

-- 1) knowhub.file.size_limit 增量补 COMMENT_IMAGE=2（MB）
UPDATE `sys_config`
SET config_value = CAST(JSON_SET(CAST(config_value AS JSON), '$.COMMENT_IMAGE', 2) AS CHAR)
WHERE config_key = 'knowhub.file.size_limit'
  AND config_value NOT LIKE '%COMMENT_IMAGE%';

-- 1.b) 若该设置项压根不存在（老库未跑迁移），INSERT 兜底一条全量默认
INSERT IGNORE INTO `sys_config` (config_key, config_name, config_value, value_type, is_system, remark, status, create_by, create_time, update_by, update_time)
SELECT 'knowhub.file.size_limit', '各业务类型体积上限',
       '{"BLOG_COVER":5,"BLOG_BODY":10,"PROJECT_SRC":500,"PROJECT_PKG":500,"PROJECT_DOC":100,"RESOURCE_FILE":100,"PLUGIN_JAR":50,"ARTICLE_COVER":5,"AUDIT_VOUCHER":5,"COMMENT_IMAGE":2}',
       'JSON', 1,
       'JSON 对象：key=业务类型 code（见 FileBusinessType 枚举），value=体积上限 MB 数；缺失业务类型回退默认 10MB（StorageConfigReader.sizeLimitBytes 读取）',
       1, 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE config_key = 'knowhub.file.size_limit');

-- 2) knowhub.file.type_whitelist 增量补 COMMENT_IMAGE（仅图片四类）
UPDATE `sys_config`
SET config_value = CAST(JSON_SET(
        CAST(config_value AS JSON),
        '$.COMMENT_IMAGE', 'image/png,image/jpeg,image/gif,image/webp'
      ) AS CHAR)
WHERE config_key = 'knowhub.file.type_whitelist'
  AND config_value NOT LIKE '%COMMENT_IMAGE%';

-- 2.b) 兜底 INSERT（老库未跑迁移时）
INSERT IGNORE INTO `sys_config` (config_key, config_name, config_value, value_type, is_system, remark, status, create_by, create_time, update_by, update_time)
SELECT 'knowhub.file.type_whitelist', '各业务类型白名单',
       '{"BLOG_COVER":"image/png,image/jpeg,image/gif,image/webp","BLOG_BODY":"image/png,image/jpeg,image/gif,image/webp","PROJECT_SRC":".zip,.tar,.gz,.7z,.rar","PROJECT_PKG":".zip,.exe,.msi,.dmg,.deb,.rpm,.apk","PROJECT_DOC":".docx,.pptx,.xlsx,.pdf","RESOURCE_FILE":"","PLUGIN_JAR":".jar","ARTICLE_COVER":"image/png,image/jpeg,image/gif,image/webp","AUDIT_VOUCHER":"image/png,image/jpeg,image/gif,image/webp,application/pdf","COMMENT_IMAGE":"image/png,image/jpeg,image/gif,image/webp"}',
       'JSON', 1,
       'JSON 对象：key=业务类型 code，value=逗号分隔的扩展名/MIME 白名单；留空或缺失表示不限制（StorageConfigReader.typeWhitelist 读取）',
       1, 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE config_key = 'knowhub.file.type_whitelist');

-- 3) sys_dict_data 装饰行：file_business_type 字典加 COMMENT_IMAGE（供后台字典管理页展示/前端下拉）
-- 列顺序对齐 sys_dict_data：dict_id,dict_key,dict_data_label,dict_data_value,remark,dict_data_sort,
--   tag_type,tag_effect,css_class,ext_json,is_default,status,create_time,create_by,update_time,update_by
INSERT IGNORE INTO `sys_dict_data`
  (`dict_id`,`dict_key`,`dict_data_label`,`dict_data_value`,`remark`,`dict_data_sort`,
   `tag_type`,`tag_effect`,`css_class`,`ext_json`,`is_default`,`status`,
   `create_time`,`create_by`,`update_time`,`update_by`)
SELECT
  (SELECT `dict_id` FROM `sys_dict` WHERE `dict_key`='file_business_type' LIMIT 1),
  'file_business_type','评论配图','COMMENT_IMAGE',
  'access=PUBLIC 类型图片 上限2MB 评论区配图',
  (SELECT IFNULL(MAX(`dict_data_sort`),0) FROM `sys_dict_data` WHERE `dict_key`='file_business_type') + 1,
  'info','light','',NULL,'0',1,
  NOW(),'admin',NOW(),'admin';

-- 4) 权限绑定：给前台游客角色（role_id=5 visitor，注册默认绑定）挂文件上传菜单（menu 82 knowhub:file:upload）
--    普通前台评论者属 visitor，未挂此菜单则调 /file/upload-token 会 403，评论配图无法上传。
--    代价：visitor 从此可申请任意 businessType 上传令牌——但 upload-token 只签令牌，
--    后端仍按 sys_config 白名单/size 校验把关文件类型与大小、写入审计元数据；前台通用上传场景风险可控。
--    admin（role_id=1）走运行时 isAdmin 直通兜底拿全菜单（SysLoginServiceImpl.java:257-261），无需挂此类行。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (5, 82);

SET FOREIGN_KEY_CHECKS = 1;