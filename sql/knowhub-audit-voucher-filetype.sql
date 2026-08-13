SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 审计票据附件业务类型续编（批次5 req2）。
-- 真正的校验数据源：sys_config JSON 设置项 knowhub.file.size_limit / knowhub.file.type_whitelist
--   （StorageConfigReader.typeWhitelist / sizeLimitBytes 读这两条 JSON 对象，按业务类型 code 取值）。
-- 故用 JSON_SET 增量补 AUDIT_VOUCHER 项（不覆盖/不丢用户自定义的其它业务类型项），
--   若设置项缺失则 INSERT 一条带 AUDIT_VOUCHER 的全量兜底行；并补 sys_dict_data 装饰行供后台字典展示。
-- access=PRIVATE，图片/PDF，≤5MB。
-- ============================================================================

-- 1) knowhub.file.size_limit 增量补 AUDIT_VOUCHER=5（MB）
UPDATE `sys_config`
SET config_value = CAST(JSON_SET(CAST(config_value AS JSON), '$.AUDIT_VOUCHER', 5) AS CHAR)
WHERE config_key = 'knowhub.file.size_limit'
  AND config_value NOT LIKE '%AUDIT_VOUCHER%';

-- 1.b) 若该设置项压根不存在（老库未跑迁移），INSERT 兜底一条全量默认
INSERT IGNORE INTO `sys_config` (config_key, config_name, config_value, value_type, is_system, remark, status, create_by, create_time, update_by, update_time)
SELECT 'knowhub.file.size_limit', '各业务类型体积上限',
       '{"BLOG_COVER":5,"BLOG_BODY":10,"PROJECT_SRC":500,"PROJECT_PKG":500,"PROJECT_DOC":100,"RESOURCE_FILE":100,"PLUGIN_JAR":50,"ARTICLE_COVER":5,"AUDIT_VOUCHER":5}',
       'JSON', 1,
       'JSON 对象：key=业务类型 code（见 FileBusinessType 枚举），value=体积上限 MB 数；缺失业务类型回退默认 10MB（StorageConfigReader.sizeLimitBytes 读取）',
       1, 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE config_key = 'knowhub.file.size_limit');

-- 2) knowhub.file.type_whitelist 增量补 AUDIT_VOUCHER（图片+PDF）
UPDATE `sys_config`
SET config_value = CAST(JSON_SET(
        CAST(config_value AS JSON),
        '$.AUDIT_VOUCHER', 'image/png,image/jpeg,image/gif,image/webp,application/pdf'
      ) AS CHAR)
WHERE config_key = 'knowhub.file.type_whitelist'
  AND config_value NOT LIKE '%AUDIT_VOUCHER%';

-- 2.b) 兜底 INSERT（老库未跑迁移时）
INSERT IGNORE INTO `sys_config` (config_key, config_name, config_value, value_type, is_system, remark, status, create_by, create_time, update_by, update_time)
SELECT 'knowhub.file.type_whitelist', '各业务类型白名单',
       '{"BLOG_COVER":"image/png,image/jpeg,image/gif,image/webp","BLOG_BODY":"image/png,image/jpeg,image/gif,image/webp","PROJECT_SRC":".zip,.tar,.gz,.7z,.rar","PROJECT_PKG":".zip,.exe,.msi,.dmg,.deb,.rpm,.apk","PROJECT_DOC":".docx,.pptx,.xlsx,.pdf","RESOURCE_FILE":"","PLUGIN_JAR":".jar","ARTICLE_COVER":"image/png,image/jpeg,image/gif,image/webp","AUDIT_VOUCHER":"image/png,image/jpeg,image/gif,image/webp,application/pdf"}',
       'JSON', 1,
       'JSON 对象：key=业务类型 code，value=逗号分隔的扩展名/MIME 白名单；留空或缺失表示不限制（StorageConfigReader.typeWhitelist 读取）',
       1, 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE config_key = 'knowhub.file.type_whitelist');

-- 3) sys_dict_data 装饰行：file_business_type 字典加 AUDIT_VOUCHER（供后台字典管理页展示/前端下拉）
-- 列顺序对齐 sys_dict_data：dict_id,dict_key,dict_data_label,dict_data_value,remark,dict_data_sort,
--   tag_type,tag_effect,css_class,ext_json,is_default,status,create_time,create_by,update_time,update_by
INSERT IGNORE INTO `sys_dict_data`
  (`dict_id`,`dict_key`,`dict_data_label`,`dict_data_value`,`remark`,`dict_data_sort`,
   `tag_type`,`tag_effect`,`css_class`,`ext_json`,`is_default`,`status`,
   `create_time`,`create_by`,`update_time`,`update_by`)
SELECT
  (SELECT `dict_id` FROM `sys_dict` WHERE `dict_key`='file_business_type' LIMIT 1),
  'file_business_type','审计票据附件','AUDIT_VOUCHER',
  'access=PRIVATE 类型图片/PDF 上限5MB 流水票据/凭证',
  (SELECT IFNULL(MAX(`dict_data_sort`),0) FROM `sys_dict_data` WHERE `dict_key`='file_business_type') + 1,
  'info','light','',NULL,'0',1,
  NOW(),'admin',NOW(),'admin';

SET FOREIGN_KEY_CHECKS = 1;