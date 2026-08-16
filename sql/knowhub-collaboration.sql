-- ============================================================================
-- 文件作用：多人协作两表建表脚本——文章贡献者申请/授权(article_contributor)+项目成员邀请(project_invite)。
--   背景（详见 memory + plan/expressive-coalescing-key.md）：
--     1) article_contributor：读者申请成为某文章的贡献者，作者审批(PENDING/APPROVED/REJECTED)。
--        APPROVED 行供 ChapterServiceImpl.canEditArticle 第三放行分支识别（approved 贡献者可提交
--        章节，不要求系统编辑级权限 knowhub:article:edit:lN>=level，与作者并列放行）。章节提交后
--        按 article.visibility 走既有分叉（PRIVATE 拒非作者/SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW/
--        PUBLIC 直 PUBLISHED），本表只放行"可否提交"，不改 visibility 既有审核语义。
--     2) project_invite：负责人邀请某用户加入项目(PENDING/ACCEPTED/REJECTED)。accept 时 service
--        转插 project_member 一行 member_role=MEMBER + 默认 flag（与现 addMembersBatch 同产出），
--        同时把 invite 置 ACCEPTED（不删 invite 行，留审计迹；project_member 的
--        uk_project_member(project_id,user_id,deleted) 已防重复入）。
--   审核状态/动作不入字典：article_contributor.status 与 project_invite.status 用代码枚举
--   (ArticleContributorStatus/ProjectInviteStatus)，前端 inline statusMeta 映射文案（照 chapters.vue
--   既有 ChapterStatus 口径，不引入新字典）。章节/文章审核的通知道路单独走 NotifySupport.notifyUser，
--   不在 sys_notice 增 biz_type 列（既无必要也避免改共享通知道）。
--   幂等：建表用 DROP IF EXISTS。不新增菜单/不新增字典 → 无 sys_menu/sys_dict/sys_config 续编
--   （记 [[knowhub-sql-id-numbering-pitfall]]——本脚本不触发续编，无需查 MAX）。
-- ============================================================================

SET NAMES utf8mb4;
USE `knowhub`;

-- ----------------------------------------------------------------------------
-- 1. article_contributor（文章贡献者申请/授权名单）
--    读者在文章详情页点"申请成为贡献者"建一条 PENDING；作者在 /collaboration 审批。
--    status：PENDING 待审 / APPROVED 已批准(贡献者可提交章节) / REJECTED 已驳回(可申诉再申)。
--    advice 仅 REJECTED 时填（驳回原因）。apply_by/handle_by 存 username 快照(照全项目审计约定)。
--    uk_article_contributor(article_id,user_id,deleted)：同(文章,用户)只一条 ACTIVE 申请——
--      重复申请前 service 把旧 ACTIVE 行软删（或本 UK 在 deleted 列上分隔，旧 REJECTED 行 deleted=0
--      也能与新一并存在？不行）。实现取舍：service 重复申请检测——若现有任何非 REJECTED 的 ACTIVE
--      行(PENDING/APPROVED 且 deleted=0)则拒(已有待审或已批准)；REJECTED 行允许多次重申(新建一条，
--      旧的保留作历史)，靠本 UK 的 deleted 维度分隔(重申时把旧 REJECTED 软删 deleted=1 再插新的)，
--      保证任意时刻同一(文章,用户)UNIQUE 不冲突且历史可追。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `article_contributor`;
CREATE TABLE `article_contributor` (
  `contributor_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '申请主键',
  `article_id`     bigint       NOT NULL COMMENT 'FK→article',
  `user_id`       bigint       NOT NULL COMMENT '申请者 sys_user.user_id',
  `status`         varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '申请状态:PENDING待审/APPROVED已批准可提交章节/REJECTED已驳回可重申(枚举不入字典)',
  `advice`         varchar(500) DEFAULT NULL COMMENT '驳回原因(仅 REJECTED 时填)',
  `apply_by`       varchar(64)  NOT NULL COMMENT '申请者 username 快照(审计)',
  `apply_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `handle_by`      varchar(64)  DEFAULT NULL COMMENT '审批人 username(文章作者)',
  `handle_time`   datetime     DEFAULT NULL COMMENT '审批时间',
  `create_by`      varchar(64)  NOT NULL COMMENT '创建人(username 快照)',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`contributor_id`),
  UNIQUE KEY `uk_article_contributor` (`article_id`, `user_id`, `deleted`),
  KEY `idx_ac_user`   (`user_id`, `deleted`),
  KEY `idx_ac_status` (`article_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章贡献者申请/授权(PENDING待审/APPROVED放行提交章节/REJECTED可重申)';

-- ----------------------------------------------------------------------------
-- 2. project_invite（项目成员邀请）
--    负责人在项目成员管理选用户发起邀请建一条 PENDING；受邀人在 /collaboration 同意/拒绝。
--    status：PENDING 待回复 / ACCEPTED 已同意(同步插 project_member MEMBER) / REJECTED 已拒绝。
--    inviter_by 受邀人看到"X 邀请你"；handle_by 受邀人 username 快照。accept 同步插 project_member,
--    复用 project_memberMapper.addMember；invite 行不删(留审计迹)。重复邀请幂等：若 (project,invitee)
--    已存在 PENDING/ACCEPTED 的 ACTIVE 行则拒(REJECTED 的可重邀,旧行软删再插)。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `project_invite`;
CREATE TABLE `project_invite` (
  `invite_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '邀请主键',
  `project_id`      bigint       NOT NULL COMMENT 'FK→project',
  `invitee_user_id` bigint       NOT NULL COMMENT '受邀人 sys_user.user_id',
  `status`          varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '邀请状态:PENDING待回复/ACCEPTED已同意(同步插project_member MEMBER)/REJECTED已拒绝(枚举不入字典)',
  `inviter_by`      varchar(64)  NOT NULL COMMENT '邀请人 username 快照(负责人)',
  `invite_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
  `handle_by`       varchar(64)  DEFAULT NULL COMMENT '处理人 username(受邀人)',
  `handle_time`     datetime     DEFAULT NULL COMMENT '处理时间',
  `create_by`       varchar(64)  NOT NULL COMMENT '创建人(username 快照)',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       varchar(64)  DEFAULT NULL COMMENT '更新人',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0 COMMENT '软删:0未删1已删',
  PRIMARY KEY (`invite_id`),
  UNIQUE KEY `uk_project_invite` (`project_id`, `invitee_user_id`, `deleted`),
  KEY `idx_pi_invitee` (`invitee_user_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员邀请(负责人邀请用户,受邀人同意后同步插 project_member)';

-- ----------------------------------------------------------------------------
-- 验证提示（不自动执行，供人工核对）：
--    SHOW CREATE TABLE article_contributor;  -- 应有 uk_article_contributor(article_id,user_id,deleted)+idx_ac_user/idx_ac_status
--    SHOW CREATE TABLE project_invite;        -- 应有 uk_project_invite(project_id,invitee_user_id,deleted)+idx_pi_invitee
--    -- 两表均无 status 字典依赖：status 用代码枚举，前端 inline 映射文案。
--    -- 不新增 sys_menu/sys_dict/sys_config → 无续编（与 knowhub-comment.sql 同一口径）。
-- ----------------------------------------------------------------------------