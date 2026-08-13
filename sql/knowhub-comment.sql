-- ============================================================================
-- 文件作用：统一评论模块建表 + 四主表加评论开关列。
--   背景（详见记忆 + plan/lovely-zooming-pudding.md）：
--     1) comment 统一评论表，四类作品(BLOG/ARTICLE/PROJECT/RESOURCE)共用，两层嵌套
--        (parent_id 指向顶级 comment_id，仅两层) + 软删 + 评论精选开关式审核
--        (作者开精选后新评论 review_status=PENDING 仅发表人+作者可见，作者 inline 同意展示后他人可见)。
--     2) comment_like 评论点赞事实表，照 resource_like 范式(自增PK+UNIQUE(comment_id,user_id))。
--     3) blog/article/project/resource 四主表各加两列：
--        comment_enabled(评论区开关 默认1开) + comment_curated(评论精选开关 默认0关)。
--   审核状态/动作直接复用既有字典 review_status(dict_id=14 NONE/PENDING/APPROVED/REJECTED)
--   与 review_action(dict_id=25 APPROVE/REJECT)，本脚本不新增字典/不新增菜单/不新增后台权限。
--   幂等：建表用 DROP IF EXISTS；ALTER 用 information_schema 判列存在再执行，重复执行不报错。
--   续编警告：本脚本不涉及 sys_menu/sys_dict/sys_config 续编，无需查 MAX。记
--   [[knowhub-sql-id-numbering-pitfall]]——若后续要加后台评论管理菜单/字典再查 MAX。
-- ============================================================================

SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. comment（统一评论主表，四类作品共用，两层嵌套+软删+评论精选）
--    biz_type 用 varchar 枚举(BLOG/ARTICLE/PROJECT/RESOURCE，不进字典)，biz_id 统四模块主键。
--    parent_id=NULL 即顶级评论；非空指向顶级 comment_id（只两层，service 拒三层）。
--    review_status 复用字典 review_status：NONE=直接可见(作者未开精选恒NONE) /
--      PENDING=待精仅作者+发表人可见 / APPROVED=作者同意展示全员可见 / REJECTED=作者拒绝仍仅作者+发表人可见。
--    reviewer/review_time/review_advice 为审核动作快照落主表（不另建流水表，无审核历史查询需求）。
--    like_count 冗余列供列表快读（service 同步 +1/-1，以 comment_like 为准）。
--    deleted 软删：删顶级评论连带其下回复 deleted=1（service 一个 update），不保留壳帖。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `comment_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  `biz_type`          varchar(16)  NOT NULL COMMENT '业务类型 BLOG/ARTICLE/PROJECT/RESOURCE(枚举不入字典)',
  `biz_id`            bigint       NOT NULL COMMENT '业务ID(各主表主键)',
  `author_id`         bigint       NOT NULL COMMENT '评论发起人用户ID(userId，对标主表 author_id)',
  `parent_id`         bigint       DEFAULT NULL COMMENT '父评论ID(顶级评论为NULL，回复指向顶级comment_id；只两层)',
  `reply_to_user_id`  bigint       DEFAULT NULL COMMENT '@人用户ID(回复某楼内某用户时填，直回复楼主为NULL)',
  `reply_to_nickname` varchar(64)  DEFAULT NULL COMMENT '@人昵称快照(展示用，username 改动不影响)',
  `content`           varchar(2000) NOT NULL COMMENT '评论内容(纯文本，前端限长)',
  `like_count`        bigint       NOT NULL DEFAULT 0 COMMENT '点赞数(冗余，以 comment_like 为准，service 维护)',
  `review_status`     varchar(16)  NOT NULL DEFAULT 'NONE' COMMENT '审核状态:复用字典review_status NONE直接可见/PENDING待精仅作者+本人可见/APPROVED作者同意展示/REJECTED作者拒绝仍仅作者+本人可见',
  `reviewer`          bigint       DEFAULT NULL COMMENT '审核人用户ID(作者精选时填作者自身 userId)',
  `review_time`       datetime     DEFAULT NULL COMMENT '审核时间',
  `review_advice`     varchar(500) DEFAULT NULL COMMENT '审核意见(仅作者精选动作时填，拒绝可留空)',
  `create_by`         varchar(64)  NOT NULL COMMENT '创建人(username 快照)',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删(删顶级评论连带其下回复 deleted=1，不保留壳帖)',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comment_biz`    (`biz_type`, `biz_id`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_author` (`author_id`),
  KEY `idx_comment_review` (`review_status`),
  KEY `idx_comment_create` (`biz_type`, `biz_id`, `create_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一评论表(博客/文章/项目/资源共用，两层嵌套+软删+评论精选)';

-- ----------------------------------------------------------------------------
-- 2. comment_like（评论点赞事实表，照 resource_like 范式）
--    自增PK + UNIQUE(comment_id,user_id) 作 toggle 依据，一人一评论一条。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `comment_like`;
CREATE TABLE `comment_like` (
  `like_id`     bigint   NOT NULL AUTO_INCREMENT COMMENT '点赞主键',
  `comment_id`  bigint   NOT NULL COMMENT '评论ID',
  `user_id`     bigint   NOT NULL COMMENT '点赞用户ID(userId)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_cl_com_user` (`comment_id`, `user_id`),
  KEY `idx_cl_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论点赞(一人一评论一条，toggle 依据)';

-- ----------------------------------------------------------------------------
-- 3. blog 主表加 comment_enabled + comment_curated 两列（幂等 ALTER）
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND COLUMN_NAME = 'comment_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `blog` ADD COLUMN `comment_enabled` tinyint NOT NULL DEFAULT 1 COMMENT ''评论区开关:1开0关(作者编辑勾选)''',
  'SELECT ''blog.comment_enabled 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND COLUMN_NAME = 'comment_curated'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `blog` ADD COLUMN `comment_curated` tinyint NOT NULL DEFAULT 0 COMMENT ''评论精选开关:0关新评论直接可见/1开新评论仅作者+发表人可见，作者同意展示后他人可见''',
  'SELECT ''blog.comment_curated 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. article 主表加 comment_enabled + comment_curated
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'comment_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `comment_enabled` tinyint NOT NULL DEFAULT 1 COMMENT ''评论区开关:1开0关(作者编辑勾选)''',
  'SELECT ''article.comment_enabled 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article' AND COLUMN_NAME = 'comment_curated'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `article` ADD COLUMN `comment_curated` tinyint NOT NULL DEFAULT 0 COMMENT ''评论精选开关:0关新评论直接可见/1开新评论仅作者+发表人可见，作者同意展示后他人可见''',
  'SELECT ''article.comment_curated 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 5. project 主表加 comment_enabled + comment_curated
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project' AND COLUMN_NAME = 'comment_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `project` ADD COLUMN `comment_enabled` tinyint NOT NULL DEFAULT 1 COMMENT ''评论区开关:1开0关(作者编辑勾选)''',
  'SELECT ''project.comment_enabled 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project' AND COLUMN_NAME = 'comment_curated'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `project` ADD COLUMN `comment_curated` tinyint NOT NULL DEFAULT 0 COMMENT ''评论精选开关:0关新评论直接可见/1开新评论仅作者+发表人可见，作者同意展示后他人可见''',
  'SELECT ''project.comment_curated 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 6. resource 主表加 comment_enabled + comment_curated
-- ----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'comment_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `resource` ADD COLUMN `comment_enabled` tinyint NOT NULL DEFAULT 1 COMMENT ''评论区开关:1开0关(作者编辑勾选)''',
  'SELECT ''resource.comment_enabled 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'comment_curated'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `resource` ADD COLUMN `comment_curated` tinyint NOT NULL DEFAULT 0 COMMENT ''评论精选开关:0关新评论直接可见/1开新评论仅作者+发表人可见，作者同意展示后他人可见''',
  'SELECT ''resource.comment_curated 已存在,跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW COLUMNS FROM comment;       -- 应含 review_status/reviewer/review_advice/parent_id/deleted 等
--    SHOW COLUMNS FROM comment_like;  -- 应有 UNIQUE uk_cl_com_user(comment_id,user_id)
--    SHOW COLUMNS FROM blog     LIKE 'comment_enabled';    SHOW COLUMNS FROM blog     LIKE 'comment_curated';
--    SHOW COLUMNS FROM article  LIKE 'comment_enabled';    SHOW COLUMNS FROM article  LIKE 'comment_curated';
--    SHOW COLUMNS FROM project  LIKE 'comment_enabled';    SHOW COLUMNS FROM project  LIKE 'comment_curated';
--    SHOW COLUMNS FROM resource LIKE 'comment_enabled';    SHOW COLUMNS FROM resource LIKE 'comment_curated';
--    -- 八列均应存在；comment_enabled 默认 1，comment_curated 默认 0
-- ----------------------------------------------------------------------------