-- =============================================================================
-- knowhub 资源管理模块 数据库脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   资源管理（后台菜单名；前台展示端待做，叫"资源推荐"）——用户分享对他人有用的
--   文件/程序/文档/网站链接等内容。FILE 类资源复用文件存储模块（businessType=
--   RESOURCE_FILE，已预留），LINK 类资源只存 link_url。审核流程复用博客那套
--   （流水表+状态机+回避+author_id+对账定时任务），ReviewAction 枚举代码层复用，
--   字典 review_action（dict_id=25，博客+资源共用）承接原 blog_review_action 的 5 个动作值。
--
--   设计要点（详见 记忆 knowhub-resource-module / doc 设计探讨）：
--   - 主表 resource 不冗余审核快照（reviewer/review_time/review_advice 全在流水表），
--     只留 status/review_status/publish_time，比博客主表更干净
--   - 互动计数（点赞/收藏/评分）不冗余主表，走事实表聚合，主表零写无热点行；
--     仅 download_count 冗余主表（仅 FILE 下载 +1，LINK 点击不计）
--   - resource_category_id 写全名（不写 category_id，防歧义），NOT NULL DEFAULT -1，
--     -1=其他（前端硬编码约定）；删分类时把挂载资源置 -1 再删分类行
--   - 互动事实表均 UNIQUE(resource_id, user_id) 做"是否已操作"判定与 toggle/upsert
--   - 审核流水表结构与 blog_review_log 完全同构（只记动作不记状态前后，operator_id
--     用 userId 稳定锁定，role 按动作类型定 AUTHOR/REVIEWER/SYSTEM）
--
-- 前置依赖：需先运行 sql/rookie.sql（sys_user/sys_menu/sys_dict/sys_config 等）、
--   sql/knowhub-blog.sql（blog 表）、sql/knowhub-blog-review-log.sql（blog_review_log 表）、
--   sql/knowhub-storage.sql（file_object 表，资源文件载体复用）。
--   注意：原 blog_review_action 字典因 rookie sys_config_value_type 占用 dict_id=22 已失效，
--   本脚本不再依赖它，改为新建 review_action 字典到 dict_id=25。
-- 运行库：knowhub（与 application.yml 中 url 一致）。
-- 幂等：建表用 DROP IF EXISTS；字典/菜单/sys_config 用 INSERT IGNORE（重跑不冲突）。
--       重跑前若已误插过旧版编号（menu 100/101、dict_data 95），需先清理（见 devlog 2026-07-07 修正记录）。
-- 编号（2026-07-07 修正，rookie 新增字典数据/系统设置模块后实际数据库占用与原假设不同）：
--   菜单 menu_id：86(资源管理页,已存在) + 102-111(资源管理按钮) + 112(资源分类页) + 113-116(资源分类按钮)
--     （86-99 被 rookie 新增的字典数据管理/系统设置模块占用，故按钮从 102 起）
--   字典 dict_id：23(resource_type,已存在) + 24(resource_status,已存在) + 25(review_action,新建)
--     （22 被 rookie sys_config_value_type 占用，原 blog_review_action 改名迁移作废，改为新建 25）
--   dict_data_id：96-102(资源类型/状态,已存在) + 103-107(review_action 5 项,新建)
--   sys_config 配置项不占上述编号（自增主键）。
-- =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 资源主表 resource
--    主表只存状态机字段(status/review_status/publish_time)，审核员/审核时间/审核意见
--    全在 resource_review_log 流水表（不冗余主表快照，比博客主表更干净）。
--    互动计数(点赞/收藏/评分)不冗余主表，走事实表聚合，主表零写无热点行；
--    仅 download_count 冗余主表（仅 FILE 下载 +1，LINK 点击不计）。
--    resource_category_id 写全名 NOT NULL DEFAULT -1，-1=其他（前端硬编码约定）。
--    审计列 + 软删 deleted（沿用 blog/file_object 约定）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `resource`;
CREATE TABLE `resource` (
  `resource_id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '资源主键',
  `author_id`             bigint       NOT NULL COMMENT '作者userId(稳定锁定,前台展示昵称走join sys_user)',
  `resource_type`         varchar(16)  NOT NULL COMMENT '资源类型:FILE文件/LINK链接(字典 resource_type)',
  `resource_category_id`  bigint       NOT NULL DEFAULT -1 COMMENT '分类id,-1=其他(前端硬编码约定,不查分类表)',
  `title`                 varchar(128) NOT NULL COMMENT '资源标题',
  `summary`               varchar(512) DEFAULT NULL COMMENT '资源简介(可不填)',
  `description`           text         DEFAULT NULL COMMENT '详细说明(支持Markdown)',
  `file_object_id`        bigint       DEFAULT NULL COMMENT 'FILE类型:关联file_object.object_id(RESOURCE_FILE业务)',
  `link_url`              varchar(512) DEFAULT NULL COMMENT 'LINK类型:外部链接URL',
  `link_icon`             varchar(512) DEFAULT NULL COMMENT 'LINK类型:图标URL(首版运行时拼favicon,可空)',
  `status`                varchar(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED(字典 resource_status)',
  `review_status`         varchar(16)  NOT NULL DEFAULT 'NONE'  COMMENT '审核状态:NONE/PENDING/APPROVED/REJECTED(复用字典 review_status)',
  `publish_time`          datetime     DEFAULT NULL COMMENT '发布时间(审核通过/开关关闭直通时回填)',
  `download_count`        bigint       NOT NULL DEFAULT 0 COMMENT '下载次数(仅FILE下载+1,LINK点击不计)',
  `create_by`             varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`resource_id`),
  KEY `idx_res_author`       (`author_id`),
  KEY `idx_res_category`     (`resource_category_id`),
  KEY `idx_res_type`         (`resource_type`),
  KEY `idx_res_status`       (`status`),
  KEY `idx_res_publish_time` (`publish_time`),
  KEY `idx_res_deleted`      (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源主表(FILE文件/LINK链接,审核快照走流水表)';

-- ----------------------------------------------------------------------------
-- 2. 资源分类表 resource_category（自关联树）
--    parent_id=0 表示顶级；同级按 sort asc 排序。status:0禁1启。
--    删除分类时：有子分类拒绝删（提示先处理子分类）；无子分类则把挂载该分类的资源
--    置 resource_category_id=-1（其他）后再软删分类行。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `resource_category`;
CREATE TABLE `resource_category` (
  `category_id`   bigint       NOT NULL AUTO_INCREMENT COMMENT '分类主键',
  `parent_id`     bigint       NOT NULL DEFAULT 0 COMMENT '父分类id(0=顶级)',
  `category_name` varchar(64)  NOT NULL COMMENT '分类名',
  `sort`          int          NOT NULL DEFAULT 0 COMMENT '排序(同级内asc)',
  `status`        tinyint      NOT NULL DEFAULT 1 COMMENT '状态:0禁1启',
  `create_by`     varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`category_id`),
  KEY `idx_rc_parent` (`parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源分类(自关联树)';

-- ----------------------------------------------------------------------------
-- 3. 资源审核流水表 resource_review_log（结构与 blog_review_log 完全同构）
--    只追加不改不删，记全量审核历史；主表只存状态机当前值，流水记全量轨迹。
--    只记动作不记状态前后(action 隐含转移语义)；operator_id 用 userId 稳定锁定，
--    operator 存 username 快照便于直读；role 按动作类型定 AUTHOR/REVIEWER/SYSTEM。
--    不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `resource_review_log`;
CREATE TABLE `resource_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `resource_id`   bigint       NOT NULL COMMENT '被审资源ID',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回/REVOKE撤回/PUBLISH直通(字典 review_action)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId,稳定锁定)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username,便于直读)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR作者/REVIEWER审核员/SYSTEM系统直通',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过可选)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_rrl_res_time`     (`resource_id`, `create_time`),
  KEY `idx_rrl_operator_time`(`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源审核流水(不可变历史,前台时间线+后台审核记录共用)';

-- ----------------------------------------------------------------------------
-- 4. 互动事实表（点赞/收藏/评分）
--    计数不冗余主表，走事实表聚合（COUNT/AVG），主表零写无热点行；
--    均 UNIQUE(resource_id, user_id) 做"是否已操作"判定与 toggle/upsert 依据。
-- ----------------------------------------------------------------------------
-- 4.1 点赞
DROP TABLE IF EXISTS `resource_like`;
CREATE TABLE `resource_like` (
  `like_id`     bigint   NOT NULL AUTO_INCREMENT COMMENT '点赞主键',
  `resource_id` bigint   NOT NULL COMMENT '资源ID',
  `user_id`     bigint   NOT NULL COMMENT '点赞用户ID(userId)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_rl_res_user` (`resource_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源点赞(一人一资源一条,toggle依据)';

-- 4.2 收藏（结构同点赞）
DROP TABLE IF EXISTS `resource_collect`;
CREATE TABLE `resource_collect` (
  `collect_id`  bigint   NOT NULL AUTO_INCREMENT COMMENT '收藏主键',
  `resource_id` bigint   NOT NULL COMMENT '资源ID',
  `user_id`     bigint   NOT NULL COMMENT '收藏用户ID(userId)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`collect_id`),
  UNIQUE KEY `uk_rc_res_user` (`resource_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源收藏(一人一资源一条,toggle依据)';

-- 4.3 评分（一人一资源可改分,UNIQUE 支撑 upsert;主表 rating_avg/rating_count 由聚合重算）
DROP TABLE IF EXISTS `resource_rating`;
CREATE TABLE `resource_rating` (
  `rating_id`   bigint   NOT NULL AUTO_INCREMENT COMMENT '评分主键',
  `resource_id` bigint   NOT NULL COMMENT '资源ID',
  `user_id`     bigint   NOT NULL COMMENT '评分用户ID(userId)',
  `score`       tinyint  NOT NULL COMMENT '评分1-5',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评分时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '改分时间',
  PRIMARY KEY (`rating_id`),
  UNIQUE KEY `uk_rr_res_user` (`resource_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源评分(一人一资源可改分,upsert)';

-- ============================================================================
-- 菜单与权限：sys_menu（三层结构：目录→页面→按钮）
--   (menu_id, menu_name, perm_key, parent_id, menu_type, route, backlinks,
--    path, icon, status, create_by, create_time, update_by, update_time, delete)
-- 父目录复用已有 knowhub(menu_id=63)。
--
-- ⚠ 编号说明（2026-07-07 修正）：
--   早期脚本假设 86 起空闲，但实际数据库中 rookie 上游新增了"字典数据管理"
--   (87-92) 和"系统设置"(93-99) 两个模块，且"资源管理"页(86)已先行落入数据库，
--   故 86-99 段被占用。本脚本菜单编号改为从 102 起续编（实际数据库 MAX(menu_id)=101
--   已清理误插后为 99，102 起安全空闲）。
--   - 86 资源管理页：已存在，下方 INSERT IGNORE 重跑跳过（幂等）
--   - 102-111 资源管理按钮（10 个：quarry/info/add/edit/delete/publish/revoke/review/reviewLog/download）
--   - 112 资源分类页
--   - 113-116 资源分类按钮（4 个：quarry/add/edit/delete）
-- ============================================================================
-- 资源管理页(menu_type=2)，挂在 knowhub 目录(63)下。86 已在数据库存在，INSERT IGNORE 跳过
INSERT IGNORE INTO `sys_menu` VALUES (86,'资源管理','knowhub:resource',63,2,'resource',0,'/knowhub/resource/index','Share',1,'admin',NOW(),'admin',NOW(),0);
-- 资源管理按钮权限(menu_type=3)，从 102 起避开 rookie 新增的字典数据/系统设置模块
INSERT IGNORE INTO `sys_menu` VALUES (102,'查询','knowhub:resource:quarry',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (103,'详情','knowhub:resource:info',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (104,'新增','knowhub:resource:add',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (105,'编辑','knowhub:resource:edit',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (106,'删除','knowhub:resource:delete',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (107,'发布','knowhub:resource:publish',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (108,'撤回','knowhub:resource:revoke',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (109,'审核','knowhub:resource:review',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (110,'审核记录','knowhub:resource:reviewLog',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (111,'下载','knowhub:resource:download',86,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- 资源分类管理页(独立菜单,menu_type=2)，挂在 knowhub 目录(63)下
INSERT IGNORE INTO `sys_menu` VALUES (112,'资源分类','knowhub:resource:category',63,2,'resource-category',0,'/knowhub/resource/category/index','Files',1,'admin',NOW(),'admin',NOW(),0);
-- 资源分类按钮权限(menu_type=3)
INSERT IGNORE INTO `sys_menu` VALUES (113,'查询','knowhub:resource:category:quarry',112,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (114,'新增','knowhub:resource:category:add',112,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (115,'编辑','knowhub:resource:category:edit',112,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (116,'删除','knowhub:resource:category:delete',112,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
--   sys_dict      (dict_id, dict_name, dict_key, status, remake, create_time, create_by, update_time, update_by)
--   sys_dict_data (dict_data_id, dict_id, dict_key, dict_data_label, dict_data_value, remark,
--                  dict_data_sort, tag_type, tag_effect, css_class, ext_json, is_default,
--                  status, create_time, create_by, update_time, update_by)
--
-- ⚠ 编号说明（2026-07-07 修正）：
--   原计划 dict_id 从 23 起，但实际数据库中 rookie 系统设置模块的 sys_config_value_type
--   占了 dict_id=22 + dict_data 91-94（与原 blog_review_action 的 22/91-95 撞车）。
--   原 blog_review_action 字典头已被覆盖、dict_data 散落。本次修正：
--   - 23 resource_type、24 resource_status：已落入数据库且无冲突，保留（INSERT IGNORE 跳过）
--   - review_action：不再做"改名迁移"（原 blog_review_action 已不存在，且 dict_id=22 被
--     sys_config_value_type 占用不能动），改为新建字典到 dict_id=25 + dict_data 103-107
--   - dict_data_id 续 MAX(102) 之后从 103 起
-- review_status 字典复用已有（NONE/PENDING/APPROVED/REJECTED），不新建。
-- ============================================================================

-- 资源类型（2 项，与 ResourceType 枚举一致；23 已在库存在，INSERT IGNORE 跳过）
INSERT IGNORE INTO `sys_dict` VALUES (23,'资源类型','resource_type',1,'资源类型枚举(ResourceType,FILE文件/LINK链接)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (96,23,'resource_type','文件','FILE','文件类资源(走文件存储模块上传)',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (97,23,'resource_type','链接','LINK','链接类资源(只存外部URL)',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 资源状态（5 项，值同 blog_status 但独立字典；24 已在库存在，INSERT IGNORE 跳过）
INSERT IGNORE INTO `sys_dict` VALUES (24,'资源状态','resource_status',1,'资源状态枚举(ResourceStatus,DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (98,24,'resource_status','草稿','DRAFT','新建未发布',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (99,24,'resource_status','已发布','PUBLISHED','已发布对读者可见',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (100,24,'resource_status','已撤回','REVOKED','作者撤回,不再对读者可见',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (101,24,'resource_status','待审核','PENDING_REVIEW','审核开关开时,作者发布后进入待审',4,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (102,24,'resource_status','已驳回','REJECTED','审核员驳回,作者改后可再发布',5,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- review_action 审核动作字典（新建到 dict_id=25，博客+资源共用）
--   历史背景：原 blog_review_action 由 knowhub-blog-review-log.sql 创建于 dict_id=22，
--   但 rookie 系统设置模块后落地占用 dict_id=22(sys_config_value_type)+dict_data 91-94，
--   导致原字典头被覆盖、dict_data 散落（仅 95 残留已被清理）。故此处新建 review_action
--   到空闲 dict_id=25，完整补 5 条审核动作数据（dict_data 103-107），博客+资源共用。
--   前端博客/资源审核历史 DictTag 用 dictKey="review_action" 渲染中文。
--   后端 ReviewAction 枚举代码层复用（值 SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH 不变）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict` VALUES (25,'审核动作','review_action',1,'审核流水动作枚举(博客/资源审核历史展示用,前端DictTag渲染中文)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (103,25,'review_action','提交审核','SUBMIT','作者提交进入待审',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (104,25,'review_action','通过','APPROVE','审核员通过',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (105,25,'review_action','驳回','REJECT','审核员驳回(advice必填)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (106,25,'review_action','撤回','REVOKE','作者撤回已发布',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (107,25,'review_action','直通发布','PUBLISH','审核开关关时系统直通发布',5,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ============================================================================
-- 系统设置：sys_config（全局开关/单值配置，走 SysConfigUtil 只读 Redis 缓存）
--   字段：config_id, config_key, config_name, config_value, value_type
--        (STRING/BOOLEAN/NUMBER/JSON), is_system(0业务1内置), remark, status, ...
--   knowhub 业务项用 knowhub. 前缀，is_system=1 标记代码硬依赖项（受内置项保护）。
--   编号：config_id 自增，不占上述菜单/字典编号。
--
--   注意：仅审核开关走 sys_config（可后台改即时生效）；对账间隔
--   knowhub.resource.reconcile-interval-minutes 走 application.yml（@Scheduled
--   注解在 Bean 创建时解析，只能读 yml/环境变量，读不了 sys_config Redis 缓存），
--   与博客模块对账间隔同套路。故本脚本只写 review_enabled 一项。
-- ============================================================================
-- 资源审核开关（BOOLEAN，true 开启审核 / false 直通发布，ResourceConfigReader 读取）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.resource.review_enabled', '资源审核开关', 'false', 'BOOLEAN', 1, '资源发布是否需审核(true开启/false直通发布,ResourceConfigReader.isReviewEnabled读取)', 1, NOW(), 'admin', NOW(), 'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   SHOW TABLES LIKE 'resource%';                          -- 应有 6 张表
--   SELECT dict_id,dict_key FROM sys_dict WHERE dict_id IN (23,24,25);
--                        -- 23 resource_type / 24 resource_status / 25 review_action
--   SELECT dict_data_value,dict_data_label FROM sys_dict_data WHERE dict_key='review_action';
--                        -- 应 5 行 SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH（博客+资源共用）
--   SELECT dict_key FROM sys_dict WHERE dict_id=22;        -- 应为 sys_config_value_type（rookie 占用，不能动）
--   SELECT config_key,config_value FROM sys_config WHERE config_key LIKE 'knowhub.resource%';
--                        -- 应 1 行 review_enabled=false（对账间隔走 yml 不在此）
--   SELECT menu_id,menu_name,perm_key,parent_id FROM sys_menu WHERE perm_key LIKE 'knowhub:resource%'
--   ORDER BY menu_id;
--                        -- 应 16 行：86(资源管理页) + 102-111(10按钮,parent=86)
--                        --         + 112(资源分类页) + 113-116(4按钮,parent=112)
-- ----------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 1;
