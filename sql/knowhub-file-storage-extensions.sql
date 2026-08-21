-- ============================================================================
-- knowhub 文件存储功能三扩展点 SQL（打包下载 / 数据迁移 / 本地模式）
-- 统一收口：access_mode 配置项 remark 更新（加 local 说明）+ 两权限点 + 迁移任务表。
-- 编号续编：rookie 上游新增菜单后原 196/197 已被占，本脚本两权限点改用 212/213（查库 MAX 后续编）。
-- 幂等：权限点 INSERT 用 NOT EXISTS 判重；remark UPDATE 按 config_key 定位；建表用 IF NOT EXISTS；
--      source_type 补列用 information_schema 判断（兼容 MySQL 8.0.29 之前无 ADD COLUMN IF NOT EXISTS）。
-- 编码：UTF-8 无 BOM，首行 SET NAMES utf8mb4。
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. sys_config：knowhub.file.access_mode remark 更新（加 local 本地模式说明 + 切换提示）
--    仅改 remark，不动 config_value（默认仍 transfer）。
--    本地模式只影响新上传，历史 OSS 数据不自动迁移；切本地前建议先把历史数据下载到本地再切换。
-- ----------------------------------------------------------------------------
UPDATE `sys_config`
SET `remark` = '文件访问模式：transfer 中转（后端代理 OSS 字节流）/ direct 直链（前端直连 nginx/OSS）/ local 本地（上传落本地磁盘、访问走后端中转读盘，见 FileAccessMode 枚举，StorageConfigReader.accessMode 读取）。本地模式只影响新上传，历史 OSS 数据不自动迁移，切本地前建议先把历史数据打包下载到本地再切换。',
    `update_time` = NOW(),
    `update_by` = 'admin'
WHERE `config_key` = 'knowhub.file.access_mode';

-- ----------------------------------------------------------------------------
-- 2. sys_menu：扩展点1 打包下载按钮权限（knowhub:file:pack-download）
--    挂文件管理菜单(menu_id=79)下作 menu_type=3 按钮权限子节点。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`,
                        `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT 212, '打包下载', 'knowhub:file:pack-download', 79, 3, NULL, 0, NULL, NULL,
       1, 'admin', NOW(), 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 212);

-- ----------------------------------------------------------------------------
-- 3. sys_menu：扩展点2 数据迁移按钮权限（knowhub:file:transfer）
--    挂文件管理菜单(menu_id=79)下作 menu_type=3 按钮权限子节点。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `perm_key`, `parent_id`, `menu_type`, `route`, `backlinks`, `path`, `icon`,
                        `status`, `create_by`, `create_time`, `update_by`, `update_time`, `delete`)
SELECT 213, '数据迁移', 'knowhub:file:transfer', 79, 3, NULL, 0, NULL, NULL,
       1, 'admin', NOW(), 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 213);

-- ----------------------------------------------------------------------------
-- 4. file_migration_task：OSS 数据迁移任务表（扩展点2）
--    记录一次迁移的连接元信息与进度。敏感凭证（accessKey/secretKey）不在表里存明文：
--    迁移请求由管理员后台填连接参数（含凭证），后端内存用完即弃，表只存 endpoint/bucket/进度/状态/错误信息。
--    status 状态机：PENDING 建任务待跑 → RUNNING @Async 线程拷贝中 → SUCCESS/FAILED → CANCELED。
--    source_type 区分迁移方向：OSS（源 OSS→目标 OSS，source_* 填源 OSS 连接元信息）/ LOCAL（本地→目标 OSS，source_* 留空）。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_migration_task` (
  `task_id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '迁移任务主键',
  `source_type`          VARCHAR(8)   NOT NULL DEFAULT 'OSS' COMMENT '源端类型：OSS（源 OSS→目标 OSS）/ LOCAL（本地→目标 OSS）',
  `source_endpoint`      VARCHAR(512) DEFAULT NULL COMMENT '源 OSS endpoint（OSS 源时填，LOCAL 源留空，连接元信息非凭证）',
  `source_region`        VARCHAR(64)  DEFAULT NULL COMMENT '源 OSS region（可空，RustFS 不校验）',
  `source_bucket`        VARCHAR(128) DEFAULT NULL COMMENT '源 OSS 桶名（OSS 源时填，LOCAL 源留空）',
  `source_path_style_access` TINYINT(1) DEFAULT 1 COMMENT '源 OSS 是否 path-style（RustFS 默认 1，LOCAL 源无意义）',
  `target_endpoint`      VARCHAR(512) NOT NULL COMMENT '目标 OSS endpoint',
  `target_region`        VARCHAR(64)  DEFAULT NULL COMMENT '目标 OSS region',
  `target_bucket`        VARCHAR(128) NOT NULL COMMENT '目标 OSS 桶名',
  `target_path_style_access` TINYINT(1) DEFAULT 1 COMMENT '目标 OSS 是否 path-style',
  `status`               VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
  `total_count`          BIGINT       NOT NULL DEFAULT 0 COMMENT '源端对象总数（OSS 源 listObjectsV2 全量计 / LOCAL 源 listLocalEntries 条目数）',
  `done_count`           BIGINT       NOT NULL DEFAULT 0 COMMENT '已成功拷贝数',
  `failed_count`         BIGINT       NOT NULL DEFAULT 0 COMMENT '拷贝失败数',
  `error_message`        TEXT         DEFAULT NULL COMMENT '错误信息（FAILED 时填）',
  `create_by`            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `create_time`          DATETIME     DEFAULT NULL COMMENT '创建时间',
  `update_by`            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`          DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 数据迁移任务表（扩展点2，凭证不落库，支持 OSS→OSS 与本地→OSS）';

-- ----------------------------------------------------------------------------
-- 4b. file_migration_task 补列/放宽约束（对已跑过旧版建表语句的库）
--     新版建表已含 source_type 列且 source_* 可空；此段仅作用于已存在的旧表，幂等：
--     - source_type 列不存在则 ADD（用 information_schema 判断，兼容 MySQL 8.0.29 之前无 ADD COLUMN IF NOT EXISTS）；
--     - source_endpoint/source_bucket 直接 MODIFY 成可空（表必存在，MODIFY 幂等无副作用）。
-- ----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE() AND `table_name` = 'file_migration_task' AND `column_name` = 'source_type');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `file_migration_task` ADD COLUMN `source_type` VARCHAR(8) NOT NULL DEFAULT ''OSS'' COMMENT ''源端类型：OSS（源 OSS→目标 OSS）/ LOCAL（本地→目标 OSS）'' AFTER `task_id`',
    'SELECT ''source_type already exists'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `file_migration_task`
    MODIFY COLUMN `source_endpoint` VARCHAR(512) DEFAULT NULL COMMENT '源 OSS endpoint（OSS 源时填，LOCAL 源留空，连接元信息非凭证）',
    MODIFY COLUMN `source_bucket`   VARCHAR(128) DEFAULT NULL COMMENT '源 OSS 桶名（OSS 源时填，LOCAL 源留空）';

-- ----------------------------------------------------------------------------
-- 5. 验证：access_mode remark 已更新、两权限点入库、迁移任务表已建（含 source_type 列）。
-- ----------------------------------------------------------------------------
-- SELECT config_key, remark FROM sys_config WHERE config_key = 'knowhub.file.access_mode';
-- SELECT menu_id, menu_name, perm_key, parent_id FROM sys_menu WHERE menu_id IN (212, 213);
-- SHOW CREATE TABLE file_migration_task;
