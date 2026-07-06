# 博客审核流程设计

> 日期：2026-07-04 定稿
> 状态：后端+后台前端已落地，前台展示待前台开发时落地
> 关联记忆：knowhub-blog-review-flow

## 一、设计目标

小体量人工审核，复用现有 blog 主表审核字段与发布/审核接口，补齐薄弱环节，留 AI/多级审核扩展口。

## 二、现状盘点（设计前已有）

- `Blog` 实体含 `status`/`reviewStatus`/`reviewer`/`reviewTime`/`reviewAdvice`
- `BlogServiceImpl` 已有 `publishBlog`/`reviewBlog`/`revokeBlog`
- 审核开关走系统设置 `knowhub.blog.review_enabled`（`BlogConfigReader.isReviewEnabled()`）
- 前端 `BlogReviewDialog`/`BlogDetailDialog` 已就绪
- 权限键 `knowhub:blog:review/publish/revoke` 已在 `sys_menu` 注册

## 三、解决的核心缺陷

1. 审核结果只覆盖主表、只存最后一次、无历史 → 新增 `blog_review_log` 流水表
2. 无状态机校验、对草稿/已发布调审核接口也能改状态 → Service 入口加前置状态校验
3. 作者可审自己文章 → review 加审核员回避（userId ≠ author_id）
4. 无用户ID锁定、前台展示昵称不稳 → blog 表加 `author_id`
5. 驳回后无清晰再提审通道 → REJECTED 态 edit 合法 + publish 重走
6. 无审核结果传达给作者 → 前后台审核记录展示代替通知（rookie 无个人通知通道）

## 四、数据库设计

### 4.1 新增 `blog_review_log` 流水表

建表脚本：`sql/knowhub-blog-review-log.sql`（独立增量，不动 `knowhub-blog.sql`）。

```sql
CREATE TABLE `blog_review_log` (
  `review_log_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '审核流水主键',
  `blog_id`        bigint NOT NULL COMMENT '被审博客ID',
  `action`         varchar(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH',
  `operator_id`    bigint NOT NULL COMMENT '操作人userId(稳定锁定)',
  `operator`       varchar(64) NOT NULL COMMENT '操作人username快照(便于直读)',
  `role`           varchar(16) NOT NULL COMMENT 'AUTHOR/REVIEWER/SYSTEM(按动作类型定)',
  `advice`         varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填)',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`review_log_id`),
  KEY `idx_brl_blog_time` (`blog_id`, `create_time`),
  KEY `idx_brl_operator_time` (`operator_id`, `create_time`)
);
```

设计要点：
- **只追加不改不删**，记全量审核历史；主表审核字段保留为「当前快照」便于列表展示
- 主键用 `review_log_id`（不用 `log_id`，避免与 `sys_oper_log` 操作日志语义混淆）
- **只记动作不记状态**（`from_status`/`to_status` 去掉），action 隐含转移语义
- `operator_id` 用 userId 稳定锁定，`operator` 存 username 快照便于直读
- `role` 是审核业务身份（AUTHOR/REVIEWER/SYSTEM）按动作类型定，**非系统角色 sys_role**

### 4.2 blog 表加 `author_id`

```sql
ALTER TABLE `blog` ADD COLUMN `author_id` bigint DEFAULT NULL COMMENT '作者用户ID' AFTER `create_by`;
-- 回填：把 create_by(username) 对应的 user_id 写入 author_id
UPDATE blog b JOIN sys_user u ON b.create_by=u.username SET b.author_id=u.user_id WHERE b.author_id IS NULL;
```

`create_by`(username) 与 `author_id`(userId) 互补：username 可改、userId 不变，前台展示作者昵称 `join sys_user on user_id=author_id` 稳定可靠。

### 4.3 字典 `blog_review_action`

5 值（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH），供前端 DictTag 渲染中文。dict_id=22，dict_data_id 91-95。

## 五、状态机

以 `status` 为主轴，`reviewStatus` 为副轴：

```
[审核开关开]                         [审核开关关]
DRAFT ─publish→ PENDING_REVIEW ─approve→ PUBLISHED    DRAFT ─publish→ PUBLISHED
                ─reject→ REJECTED                       (action=PUBLISH, role=SYSTEM)
REJECTED ─edit→ REJECTED ─publish→ PENDING_REVIEW
PUBLISHED ─revoke→ REVOKED ─publish→ PENDING_REVIEW/PUBLISHED
```

前置状态校验规则（Service 入口，非法转移抛 ServiceException）：

| 操作 | 合法前置 status | 目标 status | reviewStatus |
|---|---|---|---|
| publish | DRAFT/REJECTED/REVOKED | PENDING_REVIEW(开关开)/PUBLISHED(开关关) | PENDING/NONE |
| revoke | PUBLISHED | REVOKED | NONE |
| review(pass) | 仅 PENDING_REVIEW | PUBLISHED | APPROVED |
| review(reject) | 仅 PENDING_REVIEW | REJECTED | REJECTED |
| edit | DRAFT/REJECTED/REVOKED（**PUBLISHED 禁止**） | 不变 | 不变 |

**PUBLISHED 禁止原地编辑**：内容已变但状态仍 PUBLISHED 等于绕过审核，作者须先撤回再编辑。

## 六、数据流向（开启审核时）

```
作者 save → DRAFT
作者 publish → DRAFT→PENDING_REVIEW, reviewStatus→PENDING, 写流水(SUBMIT, AUTHOR)
   ↓ 文章进入待审队列（主表 status=PENDING_REVIEW 的行，无独立待审表）
审核员列表筛 status=PENDING_REVIEW → 点审核 → reviewBlog
   ├─ 校验 status==PENDING_REVIEW + 审核员.userId ≠ author_id（回避）
   ├─ pass=true  → PUBLISHED+APPROVED, 写流水(APPROVE, REVIEWER)
   └─ pass=false → REJECTED+REJECTED+advice必填, 写流水(REJECT, REVIEWER)
作者看到驳回 → edit（REJECTED态合法）→ 再 publish → 回 PENDING_REVIEW
```

**审核员获取待审数据方式**：博客列表接口按 `status=PENDING_REVIEW` 筛选，无单独待审表、无"领取"动作。

## 七、展示闭环（代替通知）

rookie 当前只有分组通知、无个人通知通道（`SysNoticeService.addSysNoticeInfo` 投个人 userId 路径不通），故用审核记录展示闭环，不接入通知。同一张流水表两种查询视角：

- **前台文章详情**：展示该文章审核时间线（`WHERE blog_id=? ORDER BY create_time`），作者/读者被动看到审核过程与驳回意见
- **后台管理**：展示审核记录（含审核员是谁，按 operator_id 筛），审核员工作台账
- 接口 `GET /blog/review-log/{blogId}` → `List<ReviewLogVo>`（带 operatorNickname，后端 left join sys_user）
- 后台详情弹窗已加「审核历史」折叠区（`BlogDetailDialog.vue`）
- **前台详情页审核时间线**：待前台开发时落地，详见 [blog-front-review-display.md](./blog-front-review-display.md)

通知预留：`BlogServiceImpl.notifyReviewResult(blog, action, advice)` 当前空实现，待 rookie 支持个人通知后接入，签名零改动。

## 七-2、审核开关切换与遗留 PENDING_REVIEW 对账

**问题**：管理员把审核开关从开切到关后，仍处于 PENDING_REVIEW 的遗留文章无人收口——作者编辑/再发布/撤回均被状态机拒绝（保护审核快照与队列语义），审核员也未必手动批，稿件会"卡死"在待审态。

**方案**：定时任务被动收口，不监听系统设置保存动作（`SysConfigController.editSysConfig` 在 rookie-system 通用 key-value 接口，无法可靠区分"这次保存恰好是 review_enabled"，且改 rookie 不可行）。

```
作者 publish（开关开）→ PENDING_REVIEW + SET Redis flag=1（不计数仅标记存在性，无过期）
                         ↓
BlogReviewReconcileTask 每 5min 跑一次：
  1. isReviewEnabled()=true → return（队列有人工审核意义，不收口）
  2. Redis flag 不存在 → return（没有待审稿，零扫表）
  3. flag 存在 → blogService.reconcilePendingReview()：
       查 status=PENDING_REVIEW && deleted=0 全部 blog_id
       逐条 → PUBLISHED + APPROVED + reviewer=system + publishTime=now
       逐条写流水(PUBLISH, SYSTEM, advice="审核关闭后定时任务自动放行")
       逐条 evictDetail 清详情缓存
       单条失败 try-catch 跳过不阻塞其它稿（状态优先、历史容错，无 @Transactional）
  4. DEL flag（消费后清标记，下次无待审稿时零扫表）
```

**为什么不改作者侧状态机**：定时任务自动收口已足够，作者最多等 5 分钟稿即被放行。保持 publish/edit/revoke 对 PENDING_REVIEW 的拒绝语义，避免"审核中可编辑/可重复提交"污染快照与队列。

**边界**：
- 开关切关→开期间未放行的 PENDING_REVIEW 留在队列等人工审（正确：重新开审核 = 要重新人工审）
- flag 假阳（稿已被审核员手动批但 flag 未清）→ 定时任务多扫一次空表、清 flag，可接受
- 放行写 PUBLISH/SYSTEM 流水，advice 区分场景，**不新增 ReviewAction**

**落点**：
| 层 | 文件 | 改动 |
|---|---|---|
| Mapper | `BlogMapper`+xml | 增 `listPendingReviewIds()`（status=PENDING_REVIEW && deleted=0） |
| Service | `BlogService`+`BlogServiceImpl` | 增 `reconcilePendingReview()`；`publishBlog` 进 PENDING_REVIEW 分支时 SET flag |
| Task | `task/BlogReviewReconcileTask.java`（新建） | `@Scheduled` 每 5min，读开关+读 flag+调对账+删 flag |
| 配置 | `rookie-admin/application.yml` | 加 `knowhub.blog.reconcile-interval-minutes: 5` |
| Redis | `{baseKey}blog:review:pending-flag` | 值 "1"，无过期，提交时 SET、对账消费后 DEL |

## 八、落点文件（全 `com.knowhub.*`，不碰 rookie）

| 层 | 文件 | 改动 |
|---|---|---|
| SQL | `sql/knowhub-blog-review-log.sql`（新建） | 建流水表 + blog 加 author_id + 回填 + 字典 |
| Entity | `BlogReviewLog.java`（新建）、`Blog.java` | 流水实体；Blog 加 authorId |
| Mapper | `BlogReviewLogMapper`+xml（新建）、`BlogMapper.xml` | 流水 insert/listByBlogId(left join sys_user 带昵称)；blog resultMap 加 author_id、addBlog 写 author_id；`listPendingReviewIds` 对账查询 |
| VO | `ReviewLogVo.java`（新建）、`BlogVo.java` | 流水出参(含 operatorNickname)；BlogVo 加 authorId |
| Enum | `ReviewAction.java`（新建） | SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH + role |
| Service | `BlogService`+`BlogServiceImpl` | 状态校验/回避/写流水/通知预留/addBlog 写 author_id/listReviewLog/`reconcilePendingReview`+publish 置 flag |
| Controller | `BlogController.java` | 加 `GET /blog/review-log/{blogId}` |
| Task | `task/BlogReviewReconcileTask.java`（新建） | 审核关闭后遗留待审稿对账放行 |
| 前端 | `api/knowhub/blog.ts`、`types/api/knowhub/blog.ts` | getReviewLogApi、ReviewLogRecord、BlogRecord 加 authorId |
| 前端 | `BlogDetailDialog.vue` | 加「审核历史」折叠区（时间线+DictTag） |
| 文档 | `doc/knowhub-api.md`、`doc/knowhub-devlog.md` | 接口同步、变更日志 |

**不动**：rookie 任何代码、Blog 既有字段（只加 author_id）、既有字典（只新增 blog_review_action）、BlogMapper.xml 现有查询 SQL。

## 九、扩展余地（预留不实现）

- **多级审核**：ReviewAction 加 APPROVE_L1/L2，主表加 review_stage
- **AI 审核**：publish 后异步 AiReviewTask，流水 role=SYSTEM
- **审核工作台**：独立菜单「我的待审」，固定 status=PENDING_REVIEW 筛选
- **批量审核**：`PUT /blog/review-batch`，权限点 `knowhub:blog:reviewBatch`
- **审核 SLA**：定时扫超时 PENDING_REVIEW 催办
