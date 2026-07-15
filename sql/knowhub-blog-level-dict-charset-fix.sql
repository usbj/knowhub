-- ============================================================================
-- 文件作用：修复 blog_level 字典(blog_level, dict_id=34 + dict_data 140-142)中文乱码。
--   背景：sql/knowhub-blog-level-patch.sql 的源文件本身是正确 UTF-8(hex dump 已验：
--         '与'=e4b88e / '机'=e69e9a 等字节正确)，问题出在首次入库时 mysql 客户端连接
--         字符集不是 utf8mb4（很可能是默认 latin1 或执行时未 SET NAMES），
--         导致 UTF-8 字节被按 latin1 解读后重新存库 → 中文变成乱码。
--   所以本文件不补字典(行已存在只是乱码)，改为 UPDATE 覆盖正确中文；
--   并在首行强制 set names utf8mb4，保证无论客户端默认字符集如何，本次连接都按 utf8mb4 读写。
--   教训已记入 doc/README.dev.md「SQL 文件字符与导入约定」一节，避免再犯。
--   另：article_level 等其它历史字典也曾有同样乱码(用户手动改过)，本文件只修 blog_level。
-- ============================================================================
-- 幂等：UPDATE 按 dict_id/dict_data_id 精确定位，重复执行结果不变。
-- ============================================================================

-- 关键：确保本次连接按 utf8mb4 读写，覆盖客户端默认字符集(可能是 latin1)
SET NAMES utf8mb4;

-- 1. 修 sys_dict 主行(dict_id=34)：dict_name + remake 中文
--    注意：sys_dict 的备注列名是 `remake`(rookie 框架历史拼写，非 remark)，sys_dict_data 的才是 remark。
UPDATE `sys_dict`
SET `dict_name` = '博客等级',
    `remake` = '博客等级枚举(BlogLevel,1公开/2内部/3机密,对标系统view:lN查看权限等级)'
WHERE `dict_id` = 34 AND `dict_key` = 'blog_level';

-- 2. 修 sys_dict_data 三行(dict_data_id 140/141/142)：dict_data_label + remark 中文
--    sys_dict_data 的备注列名是 `remark`(正确拼写)，与 sys_dict 的 remake 不同，别混用。
UPDATE `sys_dict_data`
SET `dict_data_label` = '公开',
    `remark` = 'L1 公开,拥有 view:l1 及以上即可查看'
WHERE `dict_data_id` = 140 AND `dict_key` = 'blog_level';

UPDATE `sys_dict_data`
SET `dict_data_label` = '内部',
    `remark` = 'L2 内部,需 view:l2 及以上'
WHERE `dict_data_id` = 141 AND `dict_key` = 'blog_level';

UPDATE `sys_dict_data`
SET `dict_data_label` = '机密',
    `remark` = 'L3 机密,需 view:l3 及以上(最高)'
WHERE `dict_data_id` = 142 AND `dict_key` = 'blog_level';

-- ----------------------------------------------------------------------------
-- 验证提示(不自动执行,供人工核对，确认中文已正常、非乱码):
--   SELECT dict_id, dict_name, dict_key, remake FROM sys_dict WHERE dict_id = 34;
--   SELECT dict_data_id, dict_data_label, dict_data_value, remark
--     FROM sys_dict_data WHERE dict_key = 'blog_level' ORDER BY dict_data_sort;
--   预期: 博客等级 / 公开 / 内部 / 机密 等中文显示正常。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 附：正确执行含中文 SQL 的姿势(导入时务必带字符集,否则再好的文件也会乱码):
--   mysql --default-character-set=utf8mb4 -u<user> -p <db> < sql/knowhub-blog-level-dict-charset-fix.sql
--   或在 SQL 文件首行已有 SET NAMES utf8mb4(本文件即如此),直接 source 也可。
-- ============================================================================