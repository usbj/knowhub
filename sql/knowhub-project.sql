-- =============================================================================
-- knowhub 项目管理模块 数据库脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   项目管理偏向项目归档记录（后续可能融入代码版本管理）。记录项目介绍、相关文档、
--   项目代码/安装包等文件存储（文件可下载，相当于开一个文件夹统一管理与项目相关的内容），
--   展示项目负责人/参与者/导师。严苛权限管理 + 权限分级：
--   - 查看权限分等级，权限拥有者能看等级及以下的项目内容；无权限者需是项目参与者，
--     只能看参与的项目；下载/更改类似
--   - 权限分系统权限（全局、分等级、所有项目）和项目内权限（单项目、不分等级、与他项目无关）
--   - 项目分等级，与权限对标，权限不够不让看
--   项目类型当前仅 COMPETITION 比赛项目（PRACTICE 练习/OPS 运维暂不做，后续加子表+字典）
--
--   设计要点（详见 doc/knowhub-project-design.md）：
--   - 权限等级获取走 ProjectPermissionResolver：一次扫描 List<Permission> 取 view/download/edit
--     三操作各自最高等级（Math.max 累积，同时持有 l1+l2 取 l2），admin 零特判（登录时全
--     perm_key 已塞入 perms，扫到 l1/l2/l3 全部三条自然得 3）
--   - 主表 project 存公共字段 + level + type + status + author_id(=LEADER) + 审核状态机；
--     类型特有字段走子表 project_competition（1:1，主键兼外键，不继承审计列不软删，随主表）
--   - 项目成员 project_member = 团队名单 + 项目内权限标志位(can_view/can_download/can_edit)，
--     member_role(LEADER/MENTOR/MEMBER)，LEADER 判定时全权不看标志位
--   - 审核流程复用博客/资源范式：project_review_log 结构同 resource_review_log，
--     ReviewAction 枚举 + review_action 字典(dict_id=25)复用，不建新字典
--   - 文件复用 file_object（business_type=PROJECT_SRC/PKG/DOC 已预留，biz_ref_id=project_id）；
--     项目内文件树 project_file 支撑 GitHub 式侧边栏（目录骨架+叶子指向 file_object）
--   - article_id 关联文章管理模块（待开发，非必填，TODO 标记）
--
-- 前置依赖：sql/rookie.sql、sql/knowhub-blog.sql、sql/knowhub-blog-review-log.sql、
--   sql/knowhub-storage.sql（file_object 表 + PROJECT_SRC/PKG/DOC business_type 已预留）、
--   sql/knowhub-resource.sql（review_action 字典 dict_id=25 + ReviewAction 枚举复用）。
-- 运行库：knowhub（与 application.yml 中 url 一致）。
-- 幂等：建表用 DROP IF EXISTS；字典/菜单/sys_config 用 INSERT IGNORE（重跑不冲突）。
-- 编号（续编，避开已占段；实际数据库 MAX(menu_id)=116、MAX(dict_id)=25、MAX(dict_data_id)=107）：
--   菜单 menu_id：117(项目管理页) + 118-122(5按钮:quarry/info/add/delete/member)
--               + 123-126(4审核按钮:publish/revoke/review/reviewLog)
--               + 127-129(view:l1/l2/l3) + 130-132(download:l1/l2/l3) + 133-135(edit:l1/l2/l3)
--               共 19 条（117-135）
--   字典 dict_id：26(project_type) + 27(project_status) + 28(project_level) + 29(project_member_role)
--   dict_data_id：108-116（108 project_type COMPETITION + 109-110 project_status
--                  + 111-113 project_level + 114-116 project_member_role）
--   sys_config config_id 自增（不占上述编号），config_key=knowhub.project.review_enabled
-- =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 项目主表 project
--    公共字段 + level(等级,对标权限) + type(类型,扩展加字典) + status(进行/归档)
--    + author_id(=LEADER userId) + 审核状态机(status/review_status/publish_time)。
--    审核快照(审核员/审核时间/审核意见)全在 project_review_log 流水表，不冗余主表。
--    description 详细介绍走 mediumtext，列表查询不带该列（对齐资源 description）。
--    article_id 关联文章管理模块，非必填，TODO: 文章管理模块开发时关联。
--    审计列 + 软删 deleted（沿用 blog/resource/file_object 约定）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
  `project_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '项目主键',
  `title`          varchar(128) NOT NULL COMMENT '项目名称',
  `type`           varchar(32)  NOT NULL COMMENT '项目类型:COMPETITION比赛(字典 project_type,扩展加 dict_data+子表)',
  `level`          tinyint      NOT NULL DEFAULT 1 COMMENT '项目等级 1公开/2内部/3机密,对标权限等级(view/download/edit:lN)',
  `summary`        varchar(500) DEFAULT NULL COMMENT '项目简介(列表展示)',
  `description`    mediumtext   DEFAULT NULL COMMENT '详细介绍(详情才查,列表不带)',
  `article_id`     bigint       DEFAULT NULL COMMENT '关联文章管理ID,非必填. TODO: 文章管理模块开发时关联',
  `author_id`      bigint       NOT NULL COMMENT '负责人userId(=LEADER,对标博客/资源 author_id;换负责人同步更新)',
  `status`         varchar(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态:DRAFT草稿/PUBLISHED已发布/REVOKED已撤回/PENDING_REVIEW待审核/REJECTED已驳回/ARCHIVED已归档(字典 project_status,审核状态机同资源)',
  `review_status`  varchar(16)  NOT NULL DEFAULT 'NONE'  COMMENT '审核状态:NONE/PENDING/APPROVED/REJECTED(复用字典 review_status)',
  `publish_time`   datetime     DEFAULT NULL COMMENT '发布时间(审核通过/开关关闭直通时回填)',
  `create_by`      varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`project_id`),
  KEY `idx_project_type_level` (`type`, `level`),
  KEY `idx_project_author`     (`author_id`),
  KEY `idx_project_status`     (`status`),
  KEY `idx_project_review`     (`review_status`),
  KEY `idx_project_deleted`    (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主表(归档记录,等级对标权限,审核快照走流水表)';

-- ----------------------------------------------------------------------------
-- 2. 比赛项目子表 project_competition（1:1，主键兼外键，不继承审计列不软删——随主表）
--    只存比赛特有字段；团队名单由 project_member 承载，不在此重复。
--    PRACTICE 练习/OPS 运维暂不做（后续加子表 + 补 project_type 字典 + 前端表单配置，主表不动）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_competition`;
CREATE TABLE `project_competition` (
  `project_id`        bigint       NOT NULL COMMENT 'FK→project(主键兼外键)',
  `competition_name`  varchar(128) NOT NULL COMMENT '比赛名称',
  `competition_level` varchar(32)  DEFAULT NULL COMMENT '比赛级别:校级/省级/国家级/国际级',
  `award_level`       varchar(32)  DEFAULT NULL COMMENT '获奖等级:特等/一等/二等/三等/优秀/无',
  `award_time`        date         DEFAULT NULL COMMENT '获奖时间',
  `competition_time`  date         DEFAULT NULL COMMENT '比赛时间',
  PRIMARY KEY (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比赛项目子表(按 type=COMPETITION 取,主表删则随删)';

-- ----------------------------------------------------------------------------
-- 3. 项目成员表 project_member（团队名单 + 项目内权限标志位）
--    member_role: LEADER负责人/MENTOR导师/MEMBER参与者（字典 project_member_role）。
--    项目内权限标志位 can_view/can_download/can_edit：单项目生效，与他项目无关，不分等级。
--    LEADER 判定时强制全权不看标志位；每项目仅一个 LEADER（service 层事务校验唯一性）。
--    换负责人 = 同步更新 project.author_id + member LEADER 行（事务保证一致）。
--    角色默认标志位（service 层创建成员时按角色给默认值，可微调）：
--      LEADER→1/1/1、MENTOR→1/1/0、MEMBER→1/0/0。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_member`;
CREATE TABLE `project_member` (
  `member_id`    bigint      NOT NULL AUTO_INCREMENT COMMENT '成员记录主键',
  `project_id`   bigint      NOT NULL COMMENT 'FK→project',
  `user_id`      bigint      NOT NULL COMMENT 'sys_user.user_id',
  `member_role`  varchar(16) NOT NULL COMMENT '成员角色:LEADER负责人/MENTOR导师/MEMBER参与者(字典 project_member_role)',
  `can_view`     tinyint     NOT NULL DEFAULT 1 COMMENT '项目内查看权限标志',
  `can_download` tinyint     NOT NULL DEFAULT 1 COMMENT '项目内下载权限标志',
  `can_edit`     tinyint     NOT NULL DEFAULT 0 COMMENT '项目内编辑权限标志',
  `create_by`    varchar(64) NOT NULL COMMENT '创建人(username)',
  `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    varchar(64) NOT NULL COMMENT '更新人',
  `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      tinyint     NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_project_member` (`project_id`, `user_id`, `deleted`),
  KEY `idx_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员(负责人/导师/参与者)+项目内权限标志';

-- ----------------------------------------------------------------------------
-- 4. 项目审核流水表 project_review_log（结构与 resource_review_log 完全同构）
--    只追加不改不删，记全量审核历史；主表只存状态机当前值，流水记全量轨迹。
--    只记动作不记状态前后(action 隐含转移语义)；operator_id 用 userId 稳定锁定，
--    operator 存 username 快照便于直读；role 按动作类型定 AUTHOR/REVIEWER/SYSTEM。
--    action/role 复用 ReviewAction 枚举 + review_action 字典(dict_id=25,博客/资源/项目共用)。
--    不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_review_log`;
CREATE TABLE `project_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `project_id`    bigint       NOT NULL COMMENT '被审项目ID',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回/REVOKE撤回/PUBLISH直通(字典 review_action,复用)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId,稳定锁定)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username,便于直读)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR作者/REVIEWER审核员/SYSTEM系统直通(复用 ReviewAction 枚举绑定)',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过可选)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_prl_project_time`  (`project_id`, `create_time`),
  KEY `idx_prl_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目审核流水(不可变历史,前台时间线+后台审核记录共用,复用 review_action 字典)';

-- ----------------------------------------------------------------------------
-- 5. 项目文件树表 project_file（支撑 GitHub 式侧边栏布局）
--    目录骨架 + 叶子指向 file_object。is_dir=1 目录(object_id=null)/is_dir=0 文件(关联 file_object)。
--    一个项目按 project_id 拉全树，前端内存组装 parent→children 递归渲染（展开/折叠）。
--    与 file_object 分工：file_object=对象存储元数据(扁平,对接 RustFS);
--    project_file=项目内目录树骨架,叶子 object_id 指向 file_object。
--    文件本体复用 file_object（business_type ∈ PROJECT_SRC/PKG/DOC，biz_ref_id=project_id）。
--    删项目：事务内级联软删 project_file + fileService.softDeleteByBizRef 三类(PROJECT_SRC/PKG/DOC)。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_file`;
CREATE TABLE `project_file` (
  `file_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '树节点主键',
  `project_id`  bigint       NOT NULL COMMENT 'FK→project',
  `parent_id`   bigint       DEFAULT NULL COMMENT '父目录ID,根节点 null',
  `name`        varchar(255) NOT NULL COMMENT '文件名/目录名',
  `is_dir`      tinyint      NOT NULL COMMENT '1=目录/0=文件',
  `object_id`   bigint       DEFAULT NULL COMMENT '关联 file_object.object_id,目录=null',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '同级排序(asc)',
  `create_by`   varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   varchar(64)  NOT NULL COMMENT '更新人',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`file_id`),
  KEY `idx_pf_project` (`project_id`),
  KEY `idx_pf_parent`  (`project_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目文件树(目录骨架+叶子指向 file_object,GitHub 式展开)';

-- ============================================================================
-- 菜单与权限：sys_menu（三层结构：目录→页面→按钮）
--   父目录复用已有 knowhub(menu_id=63)。
--   权限分两类：
--   - 按钮(非等级)：quarry/info/add/delete/member + publish/revoke/review/reviewLog → @PreAuthorize
--   - 等级(view/download/edit:lN)：service 层 ProjectPermissionResolver 扫 perms 取最高等级判定
--   edit/download 不设非等级按钮(纯等级门控);admin 登录时全 perm_key 已塞入,自然得 l3 全权。
-- ============================================================================
-- 项目管理页(menu_type=2)，挂在 knowhub 目录(63)下
INSERT IGNORE INTO `sys_menu` VALUES (117,'项目管理','knowhub:project',63,2,'project',0,'/knowhub/project/index','Folder',1,'admin',NOW(),'admin',NOW(),0);
-- 按钮(非等级,menu_type=3)
INSERT IGNORE INTO `sys_menu` VALUES (118,'查询','knowhub:project:quarry',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (119,'详情','knowhub:project:info',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (120,'新增','knowhub:project:add',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (121,'删除','knowhub:project:delete',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (122,'成员管理','knowhub:project:member',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (123,'发布','knowhub:project:publish',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (124,'撤回','knowhub:project:revoke',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (125,'审核','knowhub:project:review',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (126,'审核记录','knowhub:project:reviewLog',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 等级权限(view/download/edit:l1-l3)：ProjectPermissionResolver 扫 perms 取最高等级
INSERT IGNORE INTO `sys_menu` VALUES (127,'查看L1','knowhub:project:view:l1',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (128,'查看L2','knowhub:project:view:l2',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (129,'查看L3','knowhub:project:view:l3',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (130,'下载L1','knowhub:project:download:l1',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (131,'下载L2','knowhub:project:download:l2',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (132,'下载L3','knowhub:project:download:l3',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (133,'编辑L1','knowhub:project:edit:l1',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (134,'编辑L2','knowhub:project:edit:l2',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (135,'编辑L3','knowhub:project:edit:l3',117,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
--   续编 dict_id 从 26 起、dict_data_id 从 108 起（实际数据库 MAX(dict_id)=25、MAX(dict_data_id)=107）。
--   review_action(dict_id=25)/review_status(dict_id=14) 复用已有，不新建。
-- ============================================================================
-- 项目类型（当前仅 COMPETITION；PRACTICE/OPS 暂不做，后续补 dict_data + 子表 + 前端表单配置）
INSERT IGNORE INTO `sys_dict` VALUES (26,'项目类型','project_type',1,'项目类型枚举(ProjectType,当前仅 COMPETITION,PRACTICE/OPS 暂不做后续扩展)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (108,26,'project_type','比赛项目','COMPETITION','比赛类项目(子表 project_competition 存比赛特有字段)',1,'primary','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');

-- 项目状态（6 项，值同 resource_status 语义但含 ARCHIVED 归档，独立字典）
INSERT IGNORE INTO `sys_dict` VALUES (27,'项目状态','project_status',1,'项目状态枚举(ProjectStatus,DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (109,27,'project_status','草稿','DRAFT','新建未发布',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (110,27,'project_status','已发布','PUBLISHED','已发布对可见',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (111,27,'project_status','已撤回','REVOKED','作者撤回,不再可见',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (112,27,'project_status','待审核','PENDING_REVIEW','审核开关开时,作者发布后进入待审',4,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (113,27,'project_status','已驳回','REJECTED','审核员驳回,作者改后可再发布',5,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (114,27,'project_status','已归档','ARCHIVED','项目完结归档,仍可查看仅状态标记',6,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 项目等级（3 项，对标权限等级 view/download/edit:lN；dict_data_value 与 project.level tinyint 对齐）
INSERT IGNORE INTO `sys_dict` VALUES (28,'项目等级','project_level',1,'项目等级枚举(ProjectLevel,1公开/2内部/3机密,对标权限等级)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (115,28,'project_level','公开','1','L1 公开,拥有 view:l1 及以上即可查看',1,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (116,28,'project_level','内部','2','L2 内部,需 view:l2 及以上',2,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (117,28,'project_level','机密','3','L3 机密,需 view:l3 及以上(最高)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 项目成员角色（3 项，LEADER/MENTOR/MEMBER）
INSERT IGNORE INTO `sys_dict` VALUES (29,'项目成员角色','project_member_role',1,'项目成员角色枚举(ProjectMemberRole,LEADER负责人/MENTOR导师/MEMBER参与者)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (118,29,'project_member_role','负责人','LEADER','项目负责人,判定时全权不看标志位,每项目仅一个',1,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (119,29,'project_member_role','导师','MENTOR','项目导师,默认看+下,可微调标志位',2,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (120,29,'project_member_role','参与者','MEMBER','项目参与者,默认只看,可微调标志位',3,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ============================================================================
-- 系统设置：sys_config（全局开关，走 SysConfigUtil 只读 Redis 缓存）
--   仅审核开关走 sys_config（可后台改即时生效）；对账间隔
--   knowhub.project.reconcile-interval-minutes 走 application.yml（@Scheduled
--   注解在 Bean 创建时解析，只能读 yml/环境变量），与博客/资源模块同套路。
--   项目审核默认开启（用户明确要求加审核），与资源默认关闭不同。
-- ============================================================================
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.project.review_enabled', '项目审核开关', 'true', 'BOOLEAN', 1, '项目发布是否需审核(true开启/false直通发布,ProjectConfigReader.isReviewEnabled读取)', 1, NOW(), 'admin', NOW(), 'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   SHOW TABLES LIKE 'project%';                          -- 应有 5 张表
--   SELECT dict_id,dict_key FROM sys_dict WHERE dict_id IN (26,27,28,29);
--                        -- 26 project_type / 27 project_status / 28 project_level / 29 project_member_role
--   SELECT dict_data_value,dict_data_label FROM sys_dict_data WHERE dict_key LIKE 'project%';
--                        -- 应 13 行：108(1 类型) + 109-114(6 状态) + 115-117(3 等级) + 118-120(3 角色)
--   SELECT config_key,config_value FROM sys_config WHERE config_key LIKE 'knowhub.project%';
--                        -- 应 1 行 review_enabled=true
--   SELECT menu_id,menu_name,perm_key,parent_id FROM sys_menu WHERE perm_key LIKE 'knowhub:project%'
--   ORDER BY menu_id;
--                        -- 应 19 行：117(项目管理页) + 118-126(9按钮) + 127-135(9等级权限)
-- ----------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 1;
