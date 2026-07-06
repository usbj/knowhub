-- =============================================================================
-- knowhub 博客审核流水模块 增量脚本
-- -----------------------------------------------------------------------------
-- 背景：
--   博客审核雏形已落地（blog 主表含 status/review_status/reviewer/review_time/
--   review_advice，BlogServiceImpl 已有 publish/review/revoke，审核开关走系统设置
--   knowhub.blog.review_enabled）。但审核结果只覆盖主表、只存最后一次、无历史可溯，
--   且无状态机校验、无审核员回避、无用户ID稳定锁定。本次补审核流水表 + blog 表加
--   author_id + 审核动作字典，为「状态机校验 + 回避 + 前后台审核记录展示」提供底座。
--
--   设计要点（详见 doc/knowhub/blog/blog-review-flow-design.md / 记忆 knowhub-blog-review-flow）：
--   - blog_review_log 只追加不改不删，记全量审核历史；主表审核字段保留为「当前快照」
--   - 流水只记动作(action)不记状态前后(from/to_status 去掉)，action 隐含转移语义
--   - operator_id 用 userId 稳定锁定作者/审核员，operator 存 username 快照便于直读
--   - role 是审核业务身份(AUTHOR/REVIEWER)按动作类型定，非系统角色(sys_role)
--   - blog 加 author_id 供前台展示作者昵称稳定 join，username 改动不影响
--
-- 本脚本包含：
--   1. 新建 blog_review_log 审核流水表
--   2. blog 表追加 author_id 列 + 按 create_by(username) 回填 user_id
--   3. 新增审核动作字典 blog_review_action（5 值，供前端 DictTag 渲染中文）
--
-- 前置依赖：需先运行 sql/rookie.sql（sys_user 等）与 sql/knowhub-blog.sql（blog 表）。
-- 运行库：knowhub（与 application.yml 中 url 一致）。
-- 幂等：建表用 DROP IF EXISTS；ALTER 用 information_schema 判列存在再执行；字典用
--       INSERT IGNORE / ON DUPLICATE KEY UPDATE，重跑不冲突。
-- 编号：字典 dict_id 续 knowhub-storage.sql 之后从 22 起，dict_data_id 从 91 起。
-- =============================================================================

SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. 审核流水表 blog_review_log（不可变追加，记全量审核历史）
--    主键用 review_log_id（不用 log_id，避免与 sys_oper_log 操作日志语义混淆）。
--    只记动作不记状态：action 隐含状态转移（SUBMIT→PENDING_REVIEW、APPROVE→PUBLISHED、
--    REJECT→REJECTED、REVOKE→REVOKED、PUBLISH→PUBLISHED 直通）。
--    operator_id 用 userId 稳定锁定，operator 存 username 快照便于直读不依赖 join。
--    role 按动作类型定：SUBMIT/REVOKE→AUTHOR、APPROVE/REJECT→REVIEWER、PUBLISH→SYSTEM。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `blog_review_log`;
CREATE TABLE `blog_review_log` (
  `review_log_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `blog_id`        bigint NOT NULL COMMENT '被审博客ID',
  `action`         varchar(32) NOT NULL COMMENT '审核动作：SUBMIT提交审核 APPROVE通过 REJECT驳回 REVOKE撤回 PUBLISH直通发布',
  `operator_id`    bigint NOT NULL COMMENT '操作人用户ID(userId，稳定锁定)',
  `operator`       varchar(64) NOT NULL COMMENT '操作人用户名快照(username，便于直读)',
  `role`           varchar(16) NOT NULL COMMENT '审核业务身份：AUTHOR作者 REVIEWER审核员 SYSTEM系统直通',
  `advice`         varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填，通过可选)',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '动作时间',
  PRIMARY KEY (`review_log_id`),
  KEY `idx_brl_blog_time` (`blog_id`, `create_time`),
  KEY `idx_brl_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客审核流水（不可变历史，前台时间线+后台审核记录共用）';

-- ----------------------------------------------------------------------------
-- 2. blog 表追加 author_id 列 + 按 create_by(username) 回填 user_id
--    create_by 原存 username（rookie 审计列约定），author_id 补 userId 稳定锁定，
--    前台展示作者昵称 join sys_user on user_id=author_id，username 改动不影响。
--    用 information_schema 判列存在再 ALTER，幂等可重跑。
-- ----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND COLUMN_NAME = 'author_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `blog` ADD COLUMN `author_id` bigint DEFAULT NULL COMMENT ''作者用户ID(userId，稳定锁定，前台展示昵称join用)'' AFTER `create_by`',
  'SELECT ''column author_id already exists, skip'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填：把现有 blog.create_by(username) 对应的 user_id 写入 author_id（仅回填空行）
UPDATE `blog` b
JOIN `sys_user` u ON b.`create_by` = u.`username`
SET b.`author_id` = u.`user_id`
WHERE b.`author_id` IS NULL;

-- ----------------------------------------------------------------------------
-- 3. 审核动作字典 blog_review_action（5 值，供前端 DictTag 渲染中文标签）
--    流水表 action 字段为英文 code，前端审核历史/时间线展示时用此字典转中文。
--    dict_id 续 knowhub-storage.sql(20/21) 之后从 22 起；dict_data_id 从 91 起。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict` VALUES (22,'审核动作','blog_review_action',1,'博客审核流水动作枚举(前端审核历史展示用)',NOW(),'admin',NOW(),'admin');

INSERT IGNORE INTO `sys_dict_data` VALUES (91,22,'blog_review_action','提交审核','SUBMIT','作者提交文章进入待审',1,'primary','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (92,22,'blog_review_action','通过','APPROVE','审核员通过文章',2,'success','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (93,22,'blog_review_action','驳回','REJECT','审核员驳回文章(advice必填)',3,'danger','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (94,22,'blog_review_action','撤回','REVOKE','作者撤回已发布文章',4,'warning','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');
INSERT IGNORE INTO `sys_dict_data` VALUES (95,22,'blog_review_action','直通发布','PUBLISH','审核开关关时系统直通发布',5,'info','light','',NULL,'0',1,NOW(),'admin',NOW(),'admin');

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--   DESC blog;                          -- 应含 author_id 列
--   SELECT COUNT(*) FROM blog WHERE author_id IS NULL AND deleted=0;
--                        -- 应为 0（现有未删行已回填；若>0 说明有 create_by 对应
--                        -- 的 sys_user 已删/改名，需人工核对）
--   SELECT dict_key FROM sys_dict WHERE dict_id=22;  -- 应有 blog_review_action
--   SELECT dict_data_value, dict_data_label FROM sys_dict_data WHERE dict_key='blog_review_action';
--                        -- 应 5 行 SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH
-- ----------------------------------------------------------------------------
