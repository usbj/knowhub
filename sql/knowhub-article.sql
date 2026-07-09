-- =============================================================================
-- knowhub 文章管理模块 数据库脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   文章 = 章节集合（参考 Vue / Element-Plus 官方文档站结构：一篇文章是一
--   本"文档书"，章节是其中的"页面"）。章节 ≈ 博客（Markdown 正文，整页文档
--   语义）。系统审核颗粒度到"文章"（对外发布把关）；章节走"文章内部权限"的
--   三档可见性 + 作者审核（仅半公开场景）。
--
--   权限模型（轻量，仅系统级 + 作者归属，无成员表/无项目内标志位）：
--   - 系统权限（全局·分等级·所有文章）：knowhub:article:view:l1/l2/l3、
--     knowhub:article:edit:l1/l2/l3。userLvl(op)=max(角色勾到的 lN)，能对
--     level<=userLvl 的文章执行 op。ArticlePermissionResolver 一次扫描 perms
--     取最高等级（照 ProjectPermissionResolver，admin 零特判）。
--   - 作者归属：文章 author_id 是单一所有者；作者对自己的文章全权（不看等级
--     /不看 visibility），类比项目 LEADER 的"所有者"。无成员表。
--   - 章节不分等级，章节可见性 = 文章可见性。
-- 
--    文章内部可见性三档（visibility）：
--    - PRIVATE 未公开：仅作者能写章节，章节提交免审直接 PUBLISHED
--    - SEMIPUBLIC 半公开：有文章更改权限者(hold knowhub:article:edit:lN ≥ level
--      或作者)可提交章节，提交后需文章作者审核（走 chapter_review_log）
--    - PUBLIC 全公开：有文章更改权限者可提交章节，提交后直接 PUBLISHED 免审
-- 
--    双重审核流并存，共用 ReviewAction 枚举 + review_action 字典(dict_id=25)：
--    - 文章系统审核（对外发布把关）：照搬博客范式，流水 article_review_log；
--      开关 knowhub.article.review_enabled 走 sys_config，对账任务走 yml。
--    - 章节作者审核（半公开场景内部把关）：仅 visibility=SEMIPUBLIC 触发，流水
--      chapter_review_log；不受系统审核开关影响（是 visibility tier 固有机制）。
-- 
--    主表不冗余审核快照（reviewer/review_time/review_advice 全在流水表），只
--    留 status + review_status + publish_time。比博客主表更干净，照项目范式。
--    章节正文不分表（文档站语义：整页 Markdown，列表不带 content 即可）。
-- 
--    关联：project.article_id 单向关联文章（项目表已预留字段），文章侧不反查、
--   不加 project_id。
--
-- 前置依赖：sql/knowhub-blog-review-log.sql（ReviewAction 枚举 + review_action
--   字典 dict_id=25）、sql/knowhub-resource.sql（review_status 字典 dict_id=14）、
--   sql/knowhub-storage.sql（file_object 表 + file_business_type 字典）。
-- 运行库：knowhub（与 application.yml 中 url 一致）。
-- 幂等：建表用 DROP IF EXISTS；字典/菜单/sys_config 用 INSERT IGNORE。
-- 编号（续编，避开已占段；实际数据库 MAX(menu_id)=135、MAX(dict_id)=29、
--   MAX(dict_data_id)=122、MAX(config_id)=11）：
--   菜单 menu_id：136(文章管理页) + 137-142(6按钮:quarry/info/add/delete/publish/revoke)
--                  + 143-144(审核按钮:review/reviewLog)
--                  + 145-147(view:l1/l2/l3) + 148-150(edit:l1/l2/l3)   = 15 条
--                  章节按钮权限键(无菜单页,挂在文章菜单下作隐形 menu_type=3):
--                  + 151-155(quarry/info/add/delete/reviewLog) + 156-158(publish/revoke/review)
--                  = 8 条
--                  共 23 条（136-158）
--    字典 dict_id：30(article_status) + 31(article_level) + 32(article_visibility)
--                + 33(chapter_status)
--    dict_data_id：123-127(article_status 5) + 128-130(article_level 3)
--                 + 131-133(article_visibility 3) + 134-138(chapter_status 5)
--                 + 139(ARTICLE_COVER 续编 file_business_type)
--    sys_config config_id 自增，config_key=knowhub.article.review_enabled
--  =============================================================================
SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 文章主表 article（章节集合，不存正文）
--    公共字段 + level(等级,对标权限) + visibility(内部可见性三档) + author_id(单一作者)
--    + 审核状态机(status/review_status/publish_time)。
--    审核快照(审核员/审核时间/审核意见)全在 article_review_log 流水表，不冗余主表。
--    summary 走 mediumtext（前言/编者按）；封面走 file_object(ARTICLE_COVER)。
--     不加 project_id（单向关联：project.article_id → article，文章侧不反查）。
--     审计列 + 软删 deleted（沿用 blog/resource/project 约定）。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
  `article_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '文章主键',
  `title`             varchar(200) NOT NULL COMMENT '文章标题',
  `summary`           mediumtext   DEFAULT NULL COMMENT '前言/编者按(整书导言,列表可预览)',
  `level`             tinyint      NOT NULL DEFAULT 1 COMMENT '文章等级 1公开/2内部/3机密,对标权限等级(view/edit:lN)',
  `visibility`        varchar(20)  NOT NULL DEFAULT 'PRIVATE' COMMENT '内部可见性 PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开(字典 article_visibility,决定章节提交审不审)',
  `author_id`         bigint       NOT NULL COMMENT '作者userId(单一所有者,对标博客/资源 author_id;类比项目 LEADER 的所有者)',
  `cover_object_key`  varchar(255) DEFAULT NULL COMMENT '封面图RustFS对象key(对接file_object business_type=ARTICLE_COVER biz_ref_id=article_id)',
  `status`            varchar(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '文章状态:DRAFT草稿/PUBLISHED已发布/REVOKED已撤回/PENDING_REVIEW待审核/REJECTED已驳回(字典 article_status,审核状态机同博客)',
  `review_status`     varchar(16)  NOT NULL DEFAULT 'NONE'  COMMENT '审核状态:NONE/PENDING/APPROVED/REJECTED(复用字典 review_status)',
  `publish_time`      datetime     DEFAULT NULL COMMENT '发布时间(审核通过/开关关闭直通时回填)',
  `create_by`         varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`article_id`),
  KEY `idx_article_level`     (`level`),
  KEY `idx_article_author`   (`author_id`),
  KEY `idx_article_status`   (`status`),
  KEY `idx_article_review`   (`review_status`),
  KEY `idx_article_deleted`  (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章主表(章节集合,审核快照走流水表,正文在chapter)';

-- ----------------------------------------------------------------------------
-- 2. 章节表 chapter（≈博客，正文走主表不分表）
--     章节是"文档站页面"，正文 Markdown 整页语义。列表查询不带 content 列避免拖列表。
--     author_id=章节作者(提交者);status 多一个 PENDING_AUTHOR_REVIEW(仅 SEMIPUBLIC 触发)。
--     章节不分等级,可见性=文章可见性(reading 文章能看就能看其 PUBLISHED 章节;
--     DRAFT/PENDING_AUTHOR_REVIEW/REJECTED 仅文章作者 + 章节作者可见)。
--     审核快照(reviewer/review_time/review_advice)全在 chapter_review_log,不冗余主表。
--     审计列 + 软删 deleted。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `chapter`;
CREATE TABLE `chapter` (
  `chapter_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '章节主键',
  `article_id`      bigint       NOT NULL COMMENT 'FK→article(所属文章)',
  `chapter_name`    varchar(200) NOT NULL COMMENT '章节名(≈博客标题,文档页面标题)',
  `sort_order`      int          NOT NULL DEFAULT 0 COMMENT '章节排序(asc,同级按此排序)',
  `author_id`       bigint       NOT NULL COMMENT '章节作者userId(提交者;非作者提交且SEMIPUBLIC时需文章作者审核)',
  `content`         mediumtext   NOT NULL COMMENT 'Markdown正文(整页文档语义,不分表;列表不带此列)',
  `status`          varchar(24)  NOT NULL DEFAULT 'DRAFT' COMMENT '章节状态:DRAFT草稿/PENDING_AUTHOR_REVIEW待作者审/PUBLISHED已发布/REJECTED已驳回/REVOKED已撤回(字典 chapter_status)',
  `review_status`   varchar(16)  NOT NULL DEFAULT 'NONE'  COMMENT '审核状态:NONE/PENDING/APPROVED/REJECTED(复用字典 review_status)',
  `publish_time`   datetime     DEFAULT NULL COMMENT '章节发布时间(直通或作者审通过时回填)',
  `create_by`      varchar(64)  NOT NULL COMMENT '创建人(username)',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`chapter_id`),
  KEY `idx_chapter_article` (`article_id`, `sort_order`),
  KEY `idx_chapter_author`  (`author_id`),
  KEY `idx_chapter_status`  (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节(≈博客,正文不分表,作者审核流水走chapter_review_log)';

-- ----------------------------------------------------------------------------
-- 3. 文章系统审核流水表 article_review_log（结构与 blog_review_log/project_review_log 同构）
--     只追加不改不删,记全量审核历史;主表只存状态机当前值,流水记全量轨迹。
--     只记动作不记状态前后(action 隐含转移语义);operator_id 用 userId 稳定锁定,
--     operator 存 username 快照便于直读;role 按动作类型定 AUTHOR/REVIEWER/SYSTEM。
--     action/role 复用 ReviewAction 枚举 + review_action 字典(dict_id=25)。
--     不继承 BaseEntity（流水无 updateBy/updateTime,只有动作时间 createTime）。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `article_review_log`;
CREATE TABLE `article_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `article_id`    bigint       NOT NULL COMMENT '被审文章ID',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回/REVOKE撤回/PUBLISH直通(字典 review_action,复用)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId,稳定锁定)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username,便于直读)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR作者/REVIEWER审核员/SYSTEM系统直通(复用 ReviewAction 枚举绑定)',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过可选)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_arl_article_time`  (`article_id`, `create_time`),
  KEY `idx_arl_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章系统审核流水(不可变历史,前台时间线+后台审核记录共用,复用 review_action 字典)';

-- ----------------------------------------------------------------------------
-- 4. 章节作者审核流水表 chapter_review_log（仅 visibility=SEMIPUBLIC 场景触发）
--     照搬 article_review_log 结构,被审对象换 chapter_id。action 仅 SUBMIT/APPROVE/REJECT
--     三值(章节无 revoke/publish 直通语义;章节撤回走 editChapter 状态机不进此表)。
--     role 取 AUTHOR(章节提交者) / REVIEWER(文章作者审);SYSTEM 不会出现在章节流水。
--     不受系统审核开关影响(是 visibility tier 固有机制,独立于文章系统审核)。
--  ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `chapter_review_log`;
CREATE TABLE `chapter_review_log` (
  `review_log_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `chapter_id`    bigint       NOT NULL COMMENT '被审章节ID',
  `action`        varchar(32)  NOT NULL COMMENT '审核动作:SUBMIT提交/APPROVE通过/REJECT驳回(字典 review_action,复用三值)',
  `operator_id`   bigint       NOT NULL COMMENT '操作人用户ID(userId,稳定锁定)',
  `operator`      varchar(64)  NOT NULL COMMENT '操作人用户名快照(username,便于直读)',
  `role`          varchar(16)  NOT NULL COMMENT '审核业务身份:AUTHOR章节提交者/REVIEWER文章作者审(复用 ReviewAction)',
  `advice`        varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过可选)',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_crl_chapter_time`  (`chapter_id`, `create_time`),
  KEY `idx_crl_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节作者审核流水(仅 SEMIPUBLIC 场景,复用 review_action 字典)';

-- ============================================================================
-- 菜单与权限：sys_menu（三层结构：目录→页面→按钮）
--    父目录复用已有 knowhub(menu_id=63)。
--    文章管理页(menu_type=2)挂在 63 下；章节无独立菜单页,章节按钮权限键挂在文章菜单(136)下
--    作隐形 menu_type=3(前端按权限键控按钮显隐,不渲染为菜单项)。
--    权限分两类:
--    - 按钮(非等级): quarry/info/add/delete/publish/revoke/review/reviewLog → @PreAuthorize
--    - 等级(view/edit:lN): service 层 ArticlePermissionResolver 扫 perms 取最高等级判定
--    edit 不设非等级按钮(纯等级门控);admin 登录时全 perm_key 已塞入,自然得 l3 全权。
--  ============================================================================
-- 文章管理页(menu_type=2),挂在 knowhub 目录(63)下
INSERT IGNORE INTO `sys_menu` VALUES (136,'文章管理','knowhub:article',63,2,'article',0,'/knowhub/article/index','Document',1,'admin',NOW(),'admin',NOW(),0);
-- 文章按钮(非等级,menu_type=3)
INSERT IGNORE INTO `sys_menu` VALUES (137,'查询','knowhub:article:quarry',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (138,'详情','knowhub:article:info',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (139,'新增','knowhub:article:add',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (140,'删除','knowhub:article:delete',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (141,'发布','knowhub:article:publish',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (142,'撤回','knowhub:article:revoke',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (143,'审核','knowhub:article:review',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (144,'审核记录','knowhub:article:reviewLog',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 等级权限(view/edit:l1-l3):ArticlePermissionResolver 扫 perms 取最高等级
INSERT IGNORE INTO `sys_menu` VALUES (145,'查看L1','knowhub:article:view:l1',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (146,'查看L2','knowhub:article:view:l2',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (147,'查看L3','knowhub:article:view:l3',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (148,'编辑L1','knowhub:article:edit:l1',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (149,'编辑L2','knowhub:article:edit:l2',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (150,'编辑L3','knowhub:article:edit:l3',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
-- 章节按钮权限键(无菜单页,挂文章菜单下隐形 menu_type=3;前端按权限键控按钮显隐)
INSERT IGNORE INTO `sys_menu` VALUES (151,'章节查询','knowhub:chapter:quarry',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (152,'章节详情','knowhub:chapter:info',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (153,'章节新增','knowhub:chapter:add',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (154,'章节删除','knowhub:chapter:delete',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (155,'章节审核记录','knowhub:chapter:reviewLog',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (156,'章节发布','knowhub:chapter:publish',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (157,'章节撤回','knowhub:chapter:revoke',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);
INSERT IGNORE INTO `sys_menu` VALUES (158,'章节作者审核','knowhub:chapter:review',136,3,NULL,0,NULL,NULL,1,'admin',NOW(),'admin',NOW(),0);

-- ============================================================================
-- 字典：sys_dict / sys_dict_data
--    续编 dict_id 从 30 起、dict_data_id 从 123 起（实际数据库 MAX(dict_id)=29、MAX(dict_data_id)=122）。
--    review_action(dict_id=25)/review_status(dict_id=14) 复用已有,不新建。
--  ============================================================================
-- 文章状态（5 项，值同博客 status 语义但去掉 ARCHIVED，独立字典）
INSERT IGNORE INTO `sys_dict` VALUES (30,'文章状态','article_status',1,'文章状态枚举(ArticleStatus,DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (123,30,'article_status','草稿','DRAFT','新建未发布',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (124,30,'article_status','已发布','PUBLISHED','已发布对外可见',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (125,30,'article_status','已撤回','REVOKED','作者撤回,不再可见',3,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (126,30,'article_status','待审核','PENDING_REVIEW','审核开关开时,作者发布后进入待审',4,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (127,30,'article_status','已驳回','REJECTED','审核员驳回,作者改后可再发布',5,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 文章等级（3 项，对标权限等级 view/edit:lN；dict_data_value 与 article.level tinyint 对齐）
INSERT IGNORE INTO `sys_dict` VALUES (31,'文章等级','article_level',1,'文章等级枚举(ArticleLevel,1公开/2内部/3机密,对标权限等级)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (128,31,'article_level','公开','1','L1 公开,拥有 view:l1 及以上即可查看',1,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (129,31,'article_level','内部','2','L2 内部,需 view:l2 及以上',2,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (130,31,'article_level','机密','3','L3 机密,需 view:l3 及以上(最高)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 文章可见性（3 项，决定章节提交审不审；与文章等级正交：等级管外部可见，可见性管章节提交策略）
INSERT IGNORE INTO `sys_dict` VALUES (32,'文章可见性','article_visibility',1,'文章可见性枚举(ArticleVisibility,PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开,决定章节提交走不走作者审核)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (131,32,'article_visibility','未公开','PRIVATE','仅作者能写章节,提交免审直接 PUBLISHED',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (132,32,'article_visibility','半公开','SEMIPUBLIC','有文章更改权限者可提交章节,需文章作者审核',2,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (133,32,'article_visibility','全公开','PUBLIC','有文章更改权限者可提交章节,提交直接 PUBLISHED 免审',3,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- 章节状态（5 项，多一个 PENDING_AUTHOR_REVIEW 半公开作者审核待审态）
INSERT IGNORE INTO `sys_dict` VALUES (33,'章节状态','chapter_status',1,'章节状态枚举(ChapterStatus,DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED)',NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (134,33,'chapter_status','草稿','DRAFT','新建未提交',1,'info','light','',NULL,'1',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (135,33,'chapter_status','待作者审核','PENDING_AUTHOR_REVIEW','半公开文章:非作者提交后待文章作者审核',2,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (136,33,'chapter_status','已发布','PUBLISHED','已发布(直通或作者审通过)',3,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (137,33,'chapter_status','已驳回','REJECTED','文章作者驳回,章节作者改后可再提交',4,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (138,33,'chapter_status','已撤回','REVOKED','作者撤回已发布章节,改后可再发布',5,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- file_business_type 字典(dict_id=15)续编:文章封面图
--    续编 dict_data_id=139(实际数据库该字典现有 61-67,MAX 应为 67;此处 139 是全库 dict_data_id 续编位,
--    与字典内现有 ID 不连续但无冲突,因 sys_dict_data.dict_id+dict_data_id 复合区分)。
--    其实 dict_data_id 是全库自增主键,不受单字典内连续约束,139 安全不撞现有 67。
--  ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_data` VALUES (139,15,'file_business_type','文章封面图','ARTICLE_COVER','文章封面图,access=PUBLIC,对接 file_object',1,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ============================================================================
-- 系统设置：sys_config（全局开关，走 SysConfigUtil 只读 Redis 缓存）
--    仅文章系统审核开关走 sys_config(可后台改即时生效);对账间隔
--    knowhub.article.reconcile-interval-minutes 走 application.yml(@Scheduled
--    注解在 Bean 创建时解析,只能读 yml/环境变量),与博客/资源/项目模块同套路。
--    文章审核默认开启(与项目一致);章节作者审核是 visibility 固有机制,不走此开关。
--  ============================================================================
INSERT IGNORE INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `is_system`, `remark`, `status`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES ('knowhub.article.review_enabled', '文章审核开关', 'true', 'BOOLEAN', 1, '文章发布是否需审核(true开启/false直通发布,ArticleConfigReader.isReviewEnabled读取)', 1, NOW(), 'admin', NOW(), 'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW TABLES LIKE 'article%';                          -- 应有 2 张表
--    SHOW TABLES LIKE 'chapter%';                          -- 应有 2 张表(chapter + chapter_review_log)
--    SELECT dict_id,dict_key FROM sys_dict WHERE dict_id IN (30,31,32,33);
--                         -- 30 article_status / 31 article_level / 32 article_visibility / 33 chapter_status
--    SELECT dict_data_value,dict_data_label FROM sys_dict_data WHERE dict_id IN (30,31,32,33);
--                         -- 应 16 行:5文章状态+3等级+3可见性+5章节状态
--    SELECT config_key,config_value FROM sys_config WHERE config_key LIKE 'knowhub.article%';
--                         -- 应 1 行 review_enabled=true
--    SELECT menu_id,menu_name,perm_key,parent_id FROM sys_menu WHERE perm_key LIKE 'knowhub:article%'
--    ORDER BY menu_id;
--                         -- 应 15 行:136(文章管理页)+137-144(8按钮)+145-150(6等级权限)
--    SELECT menu_id,menu_name,perm_key FROM sys_menu WHERE perm_key LIKE 'knowhub:chapter%'
--    ORDER BY menu_id;
--                        -- 应 8 行:151-158(章节隐形按钮权限键)
--  ----------------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 1;