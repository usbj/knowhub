-- ============================================================================
-- knowhub 博客模块 数据库脚本（博客文章 CRUD + 受控标签 + 审核）
-- 运行库：knowhub（与 rookie-admin/src/main/resources/application.yml 中 url 一致）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci；引擎：InnoDB
-- 审计列、软删(deleted) 沿用 sys_notice 约定
--
-- 命名说明：模块本身即"博客(blog)"，文章实体直接称 Blog、表名 blog、主键 blog_id，
-- 路由 /blog，权限键两段式 blog:quarry/blog:add/...（无中间实体段，详见 doc/blog/blog-module-design.md）。
--
-- 本脚本独立于上游 sql/rookie.sql，不修改任何上游表/种子数据；
-- 仅在 knowhub 库新建博客相关表，并追加 sys_menu / sys_dict / sys_dict_data 的博客相关行。
--
-- 全文检索：MySQL 8 原生 FULLTEXT 对中文支持差，需 ngram 分词器。
--   建表语句中已带 WITH PARSER ngram；若服务端 ngram_token_size 未生效，
--   请先在 my.cnf [mysqld] 配置 ngram_token_size=2 后重启，或在会话执行
--   SET GLOBAL ngram_token_size = 2; 再建索引。
-- ============================================================================

SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 博客文章主表 blog
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `blog`;
CREATE TABLE `blog` (
  `blog_id`         bigint NOT NULL AUTO_INCREMENT COMMENT '博客主键',
  `title`            varchar(200) NOT NULL COMMENT '标题',
  `content`          longtext NOT NULL COMMENT '正文（Markdown/HTML）',
  `summary`          varchar(500) DEFAULT NULL COMMENT '摘要（可空，可由正文截取）',
  `cover_url`        varchar(255) DEFAULT NULL COMMENT '封面图地址（非必填）',
  `status`           varchar(32) NOT NULL DEFAULT 'DRAFT'
                    COMMENT '状态：DRAFT草稿 PUBLISHED已发布 REVOKED已撤回 PENDING_REVIEW待审核 REJECTED已驳回',
  `publish_time`     datetime DEFAULT NULL COMMENT '发布时间',
  `view_count`       bigint NOT NULL DEFAULT '0' COMMENT '浏览量(Redis 异步落库)',
  `like_count`       bigint NOT NULL DEFAULT '0' COMMENT '点赞量(冗余，以 blog_like 为准)',
  `collect_count`    bigint NOT NULL DEFAULT '0' COMMENT '收藏量(冗余，以 blog_collect 为准)',
  `review_status`    varchar(32) DEFAULT NULL COMMENT '审核状态：NONE无 PENDING待审 APPROVED通过 REJECTED驳回',
  `reviewer`         varchar(64) DEFAULT NULL COMMENT '审核人',
  `review_time`      datetime DEFAULT NULL COMMENT '审核时间',
  `review_advice`    varchar(500) DEFAULT NULL COMMENT '审核意见',
  `create_by`        varchar(64) NOT NULL COMMENT '创建人(作者)',
  `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        varchar(64) NOT NULL COMMENT '更新人',
  `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          tinyint NOT NULL DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`blog_id`),
  KEY `idx_blog_status` (`status`),
  KEY `idx_blog_publish_time` (`publish_time`),
  KEY `idx_blog_deleted` (`deleted`),
  KEY `idx_blog_review_status` (`review_status`),
  FULLTEXT KEY `ft_blog_title_content` (`title`,`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章主表';

-- ----------------------------------------------------------------------------
-- 2. 受控标签表 tag（仅管理员维护）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `tag_id`      bigint NOT NULL AUTO_INCREMENT COMMENT '标签主键',
  `tag_name`    varchar(64) NOT NULL COMMENT '标签名',
  `description` varchar(255) DEFAULT NULL COMMENT '标签说明（可选）',
  `sort`        int NOT NULL DEFAULT '0' COMMENT '排序',
  `status`      tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1启用（禁用后不可被新文章选用）',
  `create_by`   varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`   varchar(64) NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`, `deleted`),
  KEY `idx_tag_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控标签表（管理员维护）';

-- ----------------------------------------------------------------------------
-- 3. 博客-标签关联表 blog_tag（多对多中间表）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag` (
  `blog_id` bigint NOT NULL COMMENT '博客ID',
  `tag_id`  bigint NOT NULL COMMENT '标签ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `tag_id`),
  KEY `idx_bt_tag` (`tag_id`),
  KEY `idx_bt_blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客-标签关联表（多对多）';

-- ----------------------------------------------------------------------------
-- 4. 点赞明细表 blog_like
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `blog_like`;
CREATE TABLE `blog_like` (
  `blog_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `user_id`),
  KEY `idx_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客点赞明细';

-- ----------------------------------------------------------------------------
-- 5. 收藏明细表 blog_collect
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `blog_collect`;
CREATE TABLE `blog_collect` (
  `blog_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `user_id`),
  KEY `idx_collect_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客收藏明细';

-- ============================================================================
-- 菜单与权限：sys_menu
-- 三层结构：目录(menu_type=1) → 页面(menu_type=2) → 按钮(menu_type=3)
-- 列序与 sql/rookie.sql 既有行一致：
--   (menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks, path, icon,
--    status, create_by, create_time, update_by, update_time, delete)
-- 权限键三段式 knowhub:模块:动作，与上游 system:模块:动作 命名规则对齐，knowhub 前缀区分二开新增。
-- 父目录：博客管理(63,menu_type=1,perm_key='knowhub')
--   ├─ 文章管理(64,menu_type=2,perm_key='knowhub:blog', path='/knowhub/blog/index')
--   │    ├─ 查询(65)  knowhub:blog:quarry
--   │    ├─ 详情(66)  knowhub:blog:info
--   │    ├─ 新增(67)  knowhub:blog:add
--   │    ├─ 修改(68)  knowhub:blog:edit
--   │    ├─ 删除(69)  knowhub:blog:delete
--   │    ├─ 发布(70)  knowhub:blog:publish
--   │    ├─ 撤回(71)  knowhub:blog:revoke
--   │    └─ 审核(72)  knowhub:blog:review
--   └─ 标签管理(73,menu_type=2,perm_key='knowhub:tag', path='/knowhub/tag/index')
--        ├─ 查询(74)  knowhub:tag:quarry
--        ├─ 详情(75)  knowhub:tag:info
--        ├─ 新增(76)  knowhub:tag:add
--        ├─ 修改(77)  knowhub:tag:edit
--        └─ 删除(78)  knowhub:tag:delete
-- 上游最大 menu_id=62，本脚本从 63 起。
-- ============================================================================
INSERT INTO `sys_menu` VALUES (63,'博客管理','knowhub',1,1,'blog',0,NULL,'EditPen',1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (64,'文章管理','knowhub:blog',63,2,'blog',0,'/knowhub/blog/index','Document',1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (65,'查询','knowhub:blog:quarry',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (66,'详情','knowhub:blog:info',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (67,'新增','knowhub:blog:add',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (68,'修改','knowhub:blog:edit',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (69,'删除','knowhub:blog:delete',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (70,'发布','knowhub:blog:publish',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (71,'撤回','knowhub:blog:revoke',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (72,'审核','knowhub:blog:review',64,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (73,'标签管理','knowhub:tag',63,2,'tag',0,'/knowhub/tag/index','PriceTag',1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (74,'标签查询','knowhub:tag:quarry',73,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (75,'标签详情','knowhub:tag:info',73,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (76,'标签新增','knowhub:tag:add',73,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (77,'标签修改','knowhub:tag:edit',73,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT INTO `sys_menu` VALUES (78,'标签删除','knowhub:tag:delete',73,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
-- 列序与 sql/rookie.sql 既有行一致：
--   sys_dict      (dict_id, dict_name, dict_key, status, remake, create_time, create_by, update_time, update_by)
--   sys_dict_data (dict_data_id, dict_id, dict_key, dict_data_label, dict_data_value, remark,
--                  dict_data_sort, tag_type, tag_effect, css_class, ext_json, is_default,
--                  status, create_time, create_by, update_time, update_by)
-- 上游最大 dict_id=11、dict_data_id=49，本脚本分别从 12、50 起。
--
-- 新增字典类型：
--   12 blog_review_enabled   博客审核开关（全局布尔开关，后台可改）
--   13 blog_status           博客状态枚举（供前端展示/筛选下拉）
--   14 review_status         审核状态枚举
-- ============================================================================
INSERT INTO `sys_dict` VALUES (12,'博客审核开关','blog_review_enabled',1,'控制博客发布是否需经审核（全局开关，BlogConfigReader 读取）',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (13,'博客状态','blog_status',1,'博客文章状态枚举',NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict` VALUES (14,'审核状态','review_status',1,'博客审核状态枚举',NOW(),'admin',NOW(),'admin');

-- 博客审核开关（两条对称数据项，value 用 true/false）
INSERT INTO `sys_dict_data` VALUES (50,12,'blog_review_enabled','关闭审核','false','发布直通，无需审核',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (51,12,'blog_review_enabled','开启审核','true','发布需经管理员审核',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 博客状态枚举
INSERT INTO `sys_dict_data` VALUES (52,13,'blog_status','草稿','DRAFT','',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (53,13,'blog_status','已发布','PUBLISHED','',2,'success','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (54,13,'blog_status','已撤回','REVOKED','',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (55,13,'blog_status','待审核','PENDING_REVIEW','',4,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (56,13,'blog_status','已驳回','REJECTED','',5,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 审核状态枚举
INSERT INTO `sys_dict_data` VALUES (57,14,'review_status','无','NONE','',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (58,14,'review_status','待审','PENDING','',2,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (59,14,'review_status','通过','APPROVED','',3,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT INTO `sys_dict_data` VALUES (60,14,'review_status','驳回','REJECTED','',4,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

SET FOREIGN_KEY_CHECKS = 1;