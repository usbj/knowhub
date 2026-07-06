-- ============================================================================
-- knowhub 文件存储模块 数据库脚本（RustFS + AWS SDK v2 + 预签名直传）
-- 运行库：knowhub（与 rookie-admin/src/main/resources/application.yml 中 url 一致）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci；引擎：InnoDB
-- 审计列、软删(deleted) 沿用 sys_notice / blog 约定
--
-- 命名说明：模块 knowhub，文件存储业务直接称 FileObject、表名 file_object、主键 object_id，
-- 路由 /file，权限键三段式 knowhub:file:quarry/...（详见 doc/storage/file-storage-module-design.md）。
--
-- 本脚本独立于上游 sql/rookie.sql，也不改动已落地的 sql/knowhub-blog.sql；
-- 仅在 knowhub 库新建 file_object 表，并追加 sys_menu / sys_dict / sys_dict_data 的文件存储相关行。
--
-- 编号续编：blog 用到 menu_id 78、dict_id 14、dict_data_id 60，本脚本从 79 / 15 / 61 起。
-- ============================================================================

SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 文件对象元数据表 file_object
--    只存对象元数据，对象本体在 RustFS，元数据与对象解耦。
--    软删 deleted=1 仅标记元数据，对象本体由 GC 定时任务异步 DeleteObject（设计稿 §3.4）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `file_object`;
CREATE TABLE `file_object` (
  `object_id`      bigint NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `bucket`         varchar(64)  NOT NULL COMMENT '桶名（RustFS bucket）',
  `object_key`     varchar(512) NOT NULL COMMENT '对象 key（业务前缀/日期/uuid.扩展名）',
  `original_name`  varchar(255) DEFAULT NULL COMMENT '原始文件名（用户上传时的名字，仅展示）',
  `content_length` bigint       DEFAULT NULL COMMENT '对象字节数（HeadObject 确认后回填）',
  `content_type`   varchar(128) DEFAULT NULL COMMENT 'MIME 类型（上传时声明，确认时以 HeadObject 为准）',
  `checksum`       varchar(128) DEFAULT NULL COMMENT '校验值（ETag，确认时回填）',
  `business_type`  varchar(32)  NOT NULL COMMENT '业务类型：BLOG_COVER/BLOG_BODY/PROJECT_SRC/...（见 FileBusinessType 枚举）',
  `biz_ref_id`     bigint       DEFAULT NULL COMMENT '业务关联 ID（可空：上传时业务行可能还没建，确认/绑定后回填）',
  `access`         varchar(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT '访问语义：PUBLIC公开 PRIVATE私有',
  `upload_status`  varchar(16)  NOT NULL DEFAULT 'PENDING'
                  COMMENT '上传状态：PENDING待确认 CONFIRMED已确认 FAILED失败 GC待回收',
  `create_by`      varchar(64)  NOT NULL COMMENT '上传人(用户名)',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`object_id`),
  UNIQUE KEY `uk_bucket_object_key` (`bucket`, `object_key`),
  KEY `idx_file_biz` (`business_type`, `biz_ref_id`),
  KEY `idx_file_status` (`upload_status`),
  KEY `idx_file_create_time` (`create_time`),
  KEY `idx_file_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件对象元数据（RustFS 对象索引，只存元数据）';

-- ============================================================================
-- 菜单与权限：sys_menu
-- 三层结构：目录(menu_type=1) → 页面(menu_type=2) → 按钮(menu_type=3)
-- 列序与 sql/rookie.sql 既有行一致：
--   (menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon,
--    status, create_by, create_time, update_by, update_time, delete)
-- 权限键三段式 knowhub:file:动作，挂在已有 knowhub 目录(menu_id=63)下新建"文件管理"页。
-- blog 用到 menu_id 78，本脚本从 79 起。
-- ============================================================================
-- 文件管理页(menu_type=2)，挂在 knowhub 目录(63)下
INSERT INTO `sys_menu` VALUES (79,'文件管理','knowhub:file',63,2,'file',0,'/knowhub/file/index','Paperclip',1,'admin',NOW(),'admin',NOW(),0);
-- 按钮权限(menu_type=3)
INSERT INTO `sys_menu` VALUES (80,'查询','knowhub:file:quarry',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (81,'详情','knowhub:file:info',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (82,'上传','knowhub:file:upload',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (83,'下载','knowhub:file:download',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (84,'删除','knowhub:file:delete',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (85,'审核','knowhub:file:review',79,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
-- 列序与 sql/rookie.sql 既有行一致：
--   sys_dict      (dict_id, dict_name, dict_key, status, remake, create_time, create_by, update_time, update_by)
--   sys_dict_data (dict_data_id, dict_id, dict_key, dict_data_label, dict_data_value, remark,
--                  dict_data_sort, tag_type, tag_effect, css_class, ext_json, is_default,
--                  status, create_time, create_by, update_time, update_by)
-- blog 用到 dict_id 14、dict_data_id 60，本脚本从 15 / 61 起。
--
-- 枚举类对应的字典（前端下拉 + 后端校验共用）：
--   15 file_business_type   文件业务类型（FileBusinessType 枚举，7 项）
--   16 file_access          访问语义（FileAccess 枚举，2 项）
--   17 upload_status        上传状态（UploadStatus 枚举，4 项）
-- 配置型字典（StorageConfigReader 读取，可后台改）：
--   18 file_size_limit      各业务类型体积上限（label=业务类型 code，value=MB 数）
--   19 file_type_whitelist  各业务类型类型白名单（label=业务类型 code，value=逗号分隔的扩展名/MIME）
-- ============================================================================
INSERT INTO `sys_dict` VALUES (15,'文件业务类型','file_business_type',1,'文件业务类型枚举（FileBusinessType，供前端下拉与后端校验共用）',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (16,'文件访问语义','file_access',1,'文件访问语义枚举（FileAccess）',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (17,'文件上传状态','upload_status',1,'文件上传状态枚举（UploadStatus，四态机）',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (18,'文件大小上限','file_size_limit',1,'各业务类型体积上限（label=业务类型code，value=MB数，StorageConfigReader 读取）',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (19,'文件类型白名单','file_type_whitelist',1,'各业务类型类型白名单（label=业务类型code，value=逗号分隔的扩展名/MIME）',NOW(),'admin',NOW(),'admin');

-- 文件业务类型（7 项，与 FileBusinessType 枚举 code/label 一一对应）
INSERT INTO `sys_dict_data` VALUES (61,15,'file_business_type','博客封面图','BLOG_COVER','access=PUBLIC 类型图片 上限5MB',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (62,15,'file_business_type','博客正文配图','BLOG_BODY','access=PUBLIC 类型图片 上限10MB',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (63,15,'file_business_type','项目源码压缩包','PROJECT_SRC','access=PRIVATE 类型压缩包 上限500MB',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (64,15,'file_business_type','项目可执行包','PROJECT_PKG','access=PRIVATE 类型安装包 上限500MB',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (65,15,'file_business_type','项目大 Office 文档','PROJECT_DOC','access=PRIVATE 类型docx/pptx/xlsx/pdf 上限100MB',5,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (66,15,'file_business_type','资源模块文件资源','RESOURCE_FILE','access=PRIVATE 通用 上限100MB',6,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (67,15,'file_business_type','插件市场插件包','PLUGIN_JAR','access=PRIVATE 类型.jar 上限50MB',7,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 文件访问语义（2 项，与 FileAccess 枚举一致）
INSERT INTO `sys_dict_data` VALUES (68,16,'file_access','公开','PUBLIC','走 /file/public/{id} 302 回显',1,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (69,16,'file_access','私有','PRIVATE','走 /file/download/{id} 鉴权预签名下载',2,'danger','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');

-- 文件上传状态（4 项，与 UploadStatus 枚举一致）
INSERT INTO `sys_dict_data` VALUES (70,17,'upload_status','待确认','PENDING','已签发上传令牌，前端尚未传完确认',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (71,17,'upload_status','已确认','CONFIRMED','HeadObject 核对通过，可正常取用',2,'success','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (72,17,'upload_status','失败','FAILED','确认时 HeadObject 不通过或超限',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (73,17,'upload_status','待回收','GC','已软删，待定时任务 DeleteObject 并物理删元数据',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 文件大小上限（label=业务类型 code，value=MB 数；StorageConfigReader.sizeLimitBytes 读取）
INSERT INTO `sys_dict_data` VALUES (74,18,'file_size_limit','BLOG_COVER','5','博客封面图 5MB',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (75,18,'file_size_limit','BLOG_BODY','10','博客正文配图 10MB',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (76,18,'file_size_limit','PROJECT_SRC','500','项目源码压缩包 500MB',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (77,18,'file_size_limit','PROJECT_PKG','500','项目可执行包 500MB',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (78,18,'file_size_limit','PROJECT_DOC','100','项目大 Office 文档 100MB',5,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (79,18,'file_size_limit','RESOURCE_FILE','100','资源模块文件资源 100MB',6,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (80,18,'file_size_limit','PLUGIN_JAR','50','插件市场插件包 50MB',7,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 文件类型白名单（label=业务类型 code，value=逗号分隔的扩展名/MIME；StorageConfigReader.typeWhitelist 读取）
INSERT INTO `sys_dict_data` VALUES (81,19,'file_type_whitelist','BLOG_COVER','image/png,image/jpeg,image/gif,image/webp','博客封面图允许的图片类型',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (82,19,'file_type_whitelist','BLOG_BODY','image/png,image/jpeg,image/gif,image/webp','博客正文配图允许的图片类型',2,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (83,19,'file_type_whitelist','PROJECT_SRC','.zip,.tar,.gz,.7z,.rar','项目源码压缩包允许的压缩包类型',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (84,19,'file_type_whitelist','PROJECT_PKG','.zip,.exe,.msi,.dmg,.deb,.rpm,.apk','项目可执行包允许的安装包类型',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (85,19,'file_type_whitelist','PROJECT_DOC','.docx,.pptx,.xlsx,.pdf','项目大 Office 文档允许的类型',5,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (86,19,'file_type_whitelist','RESOURCE_FILE','','资源模块文件资源不限类型（留空=不限制）',6,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (87,19,'file_type_whitelist','PLUGIN_JAR','.jar','插件市场插件包仅限 jar',7,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

SET FOREIGN_KEY_CHECKS = 1;
