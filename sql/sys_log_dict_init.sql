-- rookie 日志模块字典初始化脚本
-- 设计说明：
-- 1. 按 README/记忆约定：日志表涉及枚举一律进字典系统，便于前端标签映射
-- 2. 字典结构对齐项目真实表 sys_dict(dict_key) / sys_dict_data(dict_key/dict_data_label/dict_data_value/tag_type/tag_effect/is_default)
-- 3. dict_data_value 用大写枚举字符串(对齐 sys_notice_type 风格)；status 值用 '0'/'1' 字符串匹配 TINYINT
-- 4. 采用幂等写法：先按 dict_key 判断字典是否存在并取 dict_id，再插入数据项；可重复执行

-- ========== 1. 操作日志-业务类型 ==========
INSERT INTO sys_dict (dict_name, dict_key, status, remake, create_by, create_time, update_by, update_time)
SELECT '操作业务类型', 'sys_oper_business_type', 1, '操作日志的业务类型', 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_key = 'sys_oper_business_type');

INSERT INTO sys_dict_data (dict_id, dict_key, dict_data_label, dict_data_value, remark, dict_data_sort, tag_type, tag_effect, is_default, status, create_by, create_time, update_by, update_time)
SELECT d.dict_id, 'sys_oper_business_type', t.label, t.value, t.remark, t.sort, t.tag_type, t.tag_effect, t.is_default, 1, 'admin', NOW(), 'admin', NOW()
FROM sys_dict d
JOIN (
  SELECT '其他' AS label, 'OTHER' AS value, '其他操作' AS remark, 1 AS sort, 'info' AS tag_type, 'light' AS tag_effect, '1' AS is_default
  UNION ALL SELECT '新增', 'INSERT', '新增操作', 2, 'success', 'light', '0'
  UNION ALL SELECT '修改', 'UPDATE', '修改操作', 3, 'warning', 'light', '0'
  UNION ALL SELECT '删除', 'DELETE', '删除操作', 4, 'danger', 'light', '0'
  UNION ALL SELECT '授权', 'GRANT', '授权操作', 5, 'primary', 'light', '0'
  UNION ALL SELECT '导出', 'EXPORT', '导出操作', 6, 'primary', 'light', '0'
  UNION ALL SELECT '导入', 'IMPORT', '导入操作', 7, 'primary', 'light', '0'
  UNION ALL SELECT '清空', 'CLEAN', '清空操作', 8, 'danger', 'light', '0'
) t
WHERE d.dict_key = 'sys_oper_business_type'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data dd WHERE dd.dict_id = d.dict_id AND dd.dict_data_value = t.value
  );

-- ========== 2. 操作日志-设备类型 ==========
INSERT INTO sys_dict (dict_name, dict_key, status, remake, create_by, create_time, update_by, update_time)
SELECT '操作设备类型', 'sys_oper_device_type', 1, '操作日志的设备类型(UA解析)', 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_key = 'sys_oper_device_type');

INSERT INTO sys_dict_data (dict_id, dict_key, dict_data_label, dict_data_value, remark, dict_data_sort, tag_type, tag_effect, is_default, status, create_by, create_time, update_by, update_time)
SELECT d.dict_id, 'sys_oper_device_type', t.label, t.value, t.remark, t.sort, t.tag_type, t.tag_effect, t.is_default, 1, 'admin', NOW(), 'admin', NOW()
FROM sys_dict d
JOIN (
  SELECT 'PC端' AS label, 'PC' AS value, '桌面浏览器' AS remark, 1 AS sort, '' AS tag_type, '' AS tag_effect, '1' AS is_default
  UNION ALL SELECT '移动端', 'MOBILE', '手机浏览器', 2, 'success', 'light', '0'
  UNION ALL SELECT '平板', 'TABLET', '平板设备', 3, 'warning', 'light', '0'
  UNION ALL SELECT '未知', 'UNKNOWN', '无法识别', 4, 'info', 'light', '0'
) t
WHERE d.dict_key = 'sys_oper_device_type'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data dd WHERE dd.dict_id = d.dict_id AND dd.dict_data_value = t.value
  );

-- ========== 3. 操作日志-状态 ==========
INSERT INTO sys_dict (dict_name, dict_key, status, remake, create_by, create_time, update_by, update_time)
SELECT '操作状态', 'sys_oper_status', 1, '操作日志的执行状态', 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_key = 'sys_oper_status');

INSERT INTO sys_dict_data (dict_id, dict_key, dict_data_label, dict_data_value, remark, dict_data_sort, tag_type, tag_effect, is_default, status, create_by, create_time, update_by, update_time)
SELECT d.dict_id, 'sys_oper_status', t.label, t.value, t.remark, t.sort, t.tag_type, t.tag_effect, t.is_default, 1, 'admin', NOW(), 'admin', NOW()
FROM sys_dict d
JOIN (
  SELECT '正常' AS label, '0' AS value, '操作成功' AS remark, 1 AS sort, 'success' AS tag_type, 'light' AS tag_effect, '1' AS is_default
  UNION ALL SELECT '异常', '1', '操作失败', 2, 'danger', 'light', '0'
) t
WHERE d.dict_key = 'sys_oper_status'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data dd WHERE dd.dict_id = d.dict_id AND dd.dict_data_value = t.value
  );

-- ========== 4. 错误日志-来源类型 ==========
INSERT INTO sys_dict (dict_name, dict_key, status, remake, create_by, create_time, update_by, update_time)
SELECT '错误来源类型', 'sys_error_source_type', 1, '错误日志的触发来源', 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_key = 'sys_error_source_type');

INSERT INTO sys_dict_data (dict_id, dict_key, dict_data_label, dict_data_value, remark, dict_data_sort, tag_type, tag_effect, is_default, status, create_by, create_time, update_by, update_time)
SELECT d.dict_id, 'sys_error_source_type', t.label, t.value, t.remark, t.sort, t.tag_type, t.tag_effect, t.is_default, 1, 'admin', NOW(), 'admin', NOW()
FROM sys_dict d
JOIN (
  SELECT '请求触发' AS label, 'REQUEST' AS value, 'HTTP请求触发' AS remark, 1 AS sort, 'primary' AS tag_type, 'light' AS tag_effect, '1' AS is_default
  UNION ALL SELECT '定时任务', 'SCHEDULED', '定时任务报错', 2, 'warning', 'light', '0'
  UNION ALL SELECT '异步任务', 'ASYNC', '异步任务报错', 3, 'warning', 'light', '0'
  UNION ALL SELECT '事件监听', 'EVENT', '事件监听报错', 4, 'warning', 'light', '0'
  UNION ALL SELECT '启动初始化', 'INIT', '启动/初始化阶段报错', 5, 'danger', 'light', '0'
  UNION ALL SELECT '其他', 'OTHER', '其他来源报错', 6, 'info', 'light', '0'
) t
WHERE d.dict_key = 'sys_error_source_type'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data dd WHERE dd.dict_id = d.dict_id AND dd.dict_data_value = t.value
  );
