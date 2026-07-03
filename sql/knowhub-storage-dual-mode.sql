-- ============================================================================
-- knowhub 文件存储模块 增量脚本：文件访问双模式（中转/直链）
-- 运行库：knowhub（与 rookie-admin/src/main/resources/application.yml 中 url 一致）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci；引擎：InnoDB
--
-- 前置：已运行 sql/knowhub-storage.sql（本脚本在其基础上增量，不改动原脚本，便于已部署环境直接跑）
--
-- 本次新增（2026-07-03）：
--   1. 新增 2 条 sys_dict：
--      20 file_access_mode     文件访问模式（FileAccessMode 枚举，transfer/direct）
--      21 file_direct_base_url 直链模式对外 OSS 地址 base（nginx 公网反代域名或 OSS 公网 endpoint）
--   2. 新增 3 条 sys_dict_data：
--      88 file_access_mode/中转模式/transfer（默认）
--      89 file_access_mode/直链模式/direct
--      90 file_direct_base_url/直链OSS地址/http://100.82.86.85:9000（占位，公网部署请改）
--   3. 更新 file_access 两条数据项 remark（302→后端中转回显；预签名→/file/download 或 /file/proxy）
--
-- 幂等设计：INSERT 用 IGNORE 防重跑主键冲突；UPDATE 无条件执行（remark 改对即可，重跑也是同值）。
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 新增 sys_dict：file_access_mode / file_direct_base_url
--    dict_id 20/21 紧接 knowhub-storage.sql 的 15-19 之后；INSERT IGNORE 防重跑冲突
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict` VALUES (20,'文件访问模式','file_access_mode',1,'文件访问模式枚举（FileAccessMode，transfer中转/direct直链，控制后端发链接形态，StorageConfigReader.accessMode 读取）',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict` VALUES (21,'直链模式OSS地址','file_direct_base_url',1,'直链模式对外暴露的OSS地址base（nginx公网反代域名或OSS公网endpoint，StorageConfigReader.directBaseUrl 读取）',NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- 2. 新增 sys_dict_data：file_access_mode（2 项）/ file_direct_base_url（1 项）
--    dict_data_id 88/89/90 紧接 knowhub-storage.sql 的 87 之后；INSERT IGNORE 防重跑冲突
-- ----------------------------------------------------------------------------

-- 文件访问模式（与 FileAccessMode 枚举一致；StorageConfigReader.accessMode 读取，控制后端发链接形态）
-- transfer=中转模式（后端代理读写 OSS 字节流，链接指向 /file/proxy-* 同源接口，适用于 OSS 内网/不愿配 CORS）
-- direct=直链模式（链接指向 nginx/OSS 绝对地址前端直连，适用于 OSS 公网可达/配 CORS）
INSERT IGNORE INTO `sys_dict_data` VALUES (88,20,'file_access_mode','中转模式','transfer','后端代理读写OSS字节流，链接指向/file/proxy-*同源接口（适用于OSS内网/不愿配CORS）',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (89,20,'file_access_mode','直链模式','direct','链接指向nginx/OSS绝对地址前端直连（适用于OSS公网可达/配CORS）',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 直链模式 OSS 地址 base（单值；StorageConfigReader.directBaseUrl 读取）
-- value 填 nginx 公网反代域名（如 https://your-domain.com/rustfs）或 OSS 公网 endpoint；留空回退 yml storage.endpoint（仅同网络段可达）
-- 示例值 http://100.82.86.85:9000 仅为占位，公网部署请改为可达的 nginx/OSS 公网地址
INSERT IGNORE INTO `sys_dict_data` VALUES (90,21,'file_direct_base_url','直链OSS地址','http://100.82.86.85:9000','直链模式给前端的链接host（nginx公网反代域名或OSS公网endpoint），留空回退yml endpoint',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- 3. 更新 file_access 两条数据项 remark（原值提及 302/预签名，随双模式落地修正描述）
--    按 dict_key + dict_data_value 定位（dict_data_id 68/69 也行，这里用业务键更直观）
-- ----------------------------------------------------------------------------
UPDATE `sys_dict_data` SET `remark`='走 /file/public/{id} 后端中转回显', `update_time`=NOW(), `update_by`='admin'
 WHERE `dict_key`='file_access' AND `dict_data_value`='PUBLIC';
UPDATE `sys_dict_data` SET `remark`='走 /file/download/{id} 或 /file/proxy/{id} 鉴权下载', `update_time`=NOW(), `update_by`='admin'
 WHERE `dict_key`='file_access' AND `dict_data_value`='PRIVATE';

SET FOREIGN_KEY_CHECKS = 1;
