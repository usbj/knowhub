# knowhub 专属 API 接口文档

本文件记录 **knowhub 知识库博客系统二次开发** 阶段新增的后端接口（知识库博客业务：文档模块、项目管理模块、资源推荐模块、审核与内容治理等）。

> 说明：上游 `rookie` 阶段已有的后台管理基础接口（认证 / 用户 / 角色 / 菜单 / 字典 / 字典数据 / 消息通知 / 通知分组 / 操作日志 / 错误日志）保留在 `doc/api.md` 不动。本文件只承接 knowhub 阶段新增的业务接口。

**更新日志格式约定：**
- 每次接口更新在「接口更新日志」章节追记一条，一条日志推荐覆盖 5–8 个接口或一个模块的完整变更
- 不满记录门限时可先将变更记在中间暂存区，满足后再合并为正式日志记录
- 单条日志描述本次变更涉及的范围、接口清单和简要说明
- 此约定后续可能取消或调整

**文档格式约定：**
- 每个接口统一划分为四个子节：基本信息、请求头、请求体（含参数说明）、响应示例
- 若无请求头/请求体，写"无"
- 参数说明统一使用表格；若请求体为类接收，参数表格即该类属性的描述；若含嵌套类，在下方另起一表
- 认证 Token 的请求头名称为 `Token`（无 Bearer 前缀），由 `TokenVerifyFilter` 从请求头 `Token` 字段直接取 JWT 值

---

## 通用响应说明

所有接口使用 `com.rookie.common.pojo.Result<T>` 包裹响应体：
- `code`: 200 成功，其他值失败（500 通用服务端错误）
- `msg`: 成功时固定 `"请求成功"`，失败时携带错误描述
- `data`: 成功时携带业务数据，失败时为 `null`

列表类接口使用 PageHelper 分页，返回 `PageInfo` 对象，内含 `list`、`total`、`pageNum`、`pageSize`。

需认证的接口通过 Spring Security + JWT 拦截，前端需在请求头中携带 `Token: <令牌值>`（无前缀，无 Bearer），由 `TokenVerifyFilter` 校验。业务接口的鉴权沿用上游约定：通过 `@PreAuthorize("hasAuthority('<模块>:<子模块>:<动作>')")`（权限前缀随模块确定，新增模块时与 `sys_menu` 中 `perm_key` 现网数据保持一致），未登录访问由 `AuthenticationEntryPointImpl` 返回 `{code:401}`，无权限访问由 `AccessDeniedHandlerImpl` 返回 `{code:403}`；超级管理员（`role_key='admin'`）直通加载全部按钮权限。

> knowhub 阶段业务接口尚未落地。下方从首个业务模块发布起，按「接口更新日志」+ 各业务模块章节的格式追加。

---

## 接口更新日志

### 2026-06-30 博客模块首批接口落地（文章 CRUD + 受控标签 + 审核/点赞/收藏）

本次随 knowhub-blog 模块首次落地，新增博客文章与受控标签两块业务接口，共 13 个：

- 文章：`GET /blog/list`、`GET /blog/{blogId}`、`POST /blog`、`PUT /blog`、`DELETE /blog/{blogIds}`、`PUT /blog/publish/{blogId}`、`PUT /blog/revoke/{blogId}`、`PUT /blog/review`
- 标签：`GET /tag/list`、`GET /tag/{tagId}`、`POST /tag`、`PUT /tag`、`DELETE /tag/{tagIds}`

说明：发布接口受全局审核开关 `knowhub.blog.review_enabled`（系统设置 sys_config，BOOLEAN）控制，开关开则发布进入 `PENDING_REVIEW` 待审、由 `PUT /blog/review` 通过/驳回；开关关则直接 `PUBLISHED`。点赞/收藏接口原 `/blog/like|collect/{id}` 后改挪前台 `/authoring/blog/{id}/like|collect`（详见下方「博客创作模块」/「博客模块」章节)。

### 2026-07-01 文件存储模块落地（预签名直传 + PUBLIC 回显 + PRIVATE 下载 + 对象 GC）

本次随 knowhub 文件存储底座落地，新增文件对象管理接口共 8 个：

- 上传令牌/确认：`POST /file/upload-token`、`POST /file/confirm/{objectId}`
- 回显/下载：`GET /file/public/{objectId}`（后端中转字节流，无鉴权）、`GET /file/download/{objectId}`
- 管理：`GET /file/list`、`GET /file/{objectId}`、`PUT /file/bind`、`DELETE /file/{objectIds}`

说明：上传走预签名直传——后端校验 contentType/size 后下发 `PutObject` 预签名 URL，前端直传 RustFS，后端不经流文件字节；`confirm` 用 `HeadObject` 核对真实值后置 `CONFIRMED`。PUBLIC 对象走 `/file/public/{id}` 302 重定向到 RustFS（供 Markdown `<img>` 直接引用，靠 vite proxy / nginx 转发 `/file`）；PRIVATE 对象走 `/file/download/{id}` 鉴权后下发短期 GET 预签名（带 `attachment;filename` 强制下载）。超时 PENDING 与软删对象由 `FileGcTask` 定时 GC。详见下方「文件存储模块」章节。

### 2026-07-02 预签名 uploadUrl/downloadUrl 改 /rustfs 同源代理相对路径

为修复浏览器直传 RustFS 的跨域 CORS 拦截（preflight 返 200 但无 `Access-Control-Allow-*` 头），后端 `FileServiceImpl` 新增 `rewriteUrlForProxy` 把预签名绝对 URL 的 RustFS endpoint 前缀改写为 `/rustfs`，仅影响两个接口的出参字段：

- `POST /file/upload-token` 响应 `data.uploadUrl`：由 `http://<rustfs-endpoint>/knowhub/.../x.png?X-Amz-...` 改为 `/rustfs/knowhub/.../x.png?X-Amz-...`
- `GET /file/download/{objectId}` 响应 `data.downloadUrl`：同上改写

前端 PUT/GET 走当前 origin 经 vite proxy（dev `/rustfs`→`<rustfs-endpoint>`）/ nginx（prod 同名转发）到 RustFS，同源无 CORS。`GET /file/public/{objectId}` 改为后端中转字节流（`s3Client.getObject` 拉流 + `StreamingResponseBody` 回写，见下方「PUBLIC 对象回显」），不再 302 跳 RustFS——前端 `<img src="/file/public/{id}">` 同源拉图，后续迁移 OSS 只改后端存储配置、前端零改动。接口签名、请求体、其余字段均不变。prod 需 nginx 加 `/rustfs` 转发（与 `/api`、`/file` 并列）。详见下方「文件存储模块」章节各接口响应示例与说明。

### 2026-07-03 文件访问双模式（中转/直链）+ 地址由后端决定

为消除前端对 OSS 地址的硬依赖（迁 OSS / 切部署拓扑时前端与 nginx 零改动），新增**文件访问模式**开关（系统设置 `knowhub.file.access_mode`，值 `transfer`/`direct`，`StorageConfigReader.accessMode` 读取，运维后台改、运行时生效），后端按模式决定发给前端的链接形态——前端永远只认后端给的链接，地址完全由后端决定。同时直链模式 OSS 地址 base 走系统设置 `knowhub.file.direct_base_url`（`StorageConfigReader.directBaseUrl` 读取，填 nginx 公网反代域名或 OSS 公网 endpoint）。

- **中转模式（transfer，默认）**：`uploadUrl` 填 `/file/proxy-upload/{objectId}`（后端代理转发上传字节，同源带 Token）；`downloadUrl` 填 `/file/proxy/{objectId}`（后端中转下载字节流，同源带 Token）；PUBLIC 回显仍走 `/file/public/{id}`。适用于 OSS 在内网/不愿配 CORS，代价是后端经文件字节流。
- **直链模式（direct）**：`uploadUrl` 填预签名绝对 URL（host 用 `directBaseUrl`，前端直连 nginx/OSS，需配 CORS）；`downloadUrl` 填预签名绝对 URL（带 `attachment;filename`）；PUBLIC 回显链接填 `{directBaseUrl}/{bucket}/{objectKey}`（公开读直链，不带签名，永不过期）。适用于 OSS 公网可达 + 配 CORS，后端不经字节流。

新增接口：
- `PUT /file/proxy-upload/{objectId}`（中转模式上传，权限 `knowhub:file:upload`，接收字节流写入 OSS + confirm）
- `GET /file/proxy/{objectId}`（中转模式下载，权限 `knowhub:file:download`，后端拉 OSS 字节回写，PRIVATE 带 `attachment;filename`）
- `GET /file/url/{objectId}`（取 PUBLIC 回显链接，无鉴权，按模式返回 `/file/public/{id}` 或直链）

前端 `rookie-ui` 适配：`utils/upload.ts` 按链接形态（相对/绝对）自动决定 PUT 是否带 Token；PUBLIC 上传成功后调 `/file/url/{id}` 取按模式回显链接（异常回退 `/file/public/{id}`）；`views/knowhub/file/index.vue` 下载按链接形态分流（绝对 URL 直接 `window.open`，相对路径 `fetch` 带 Token 取 blob）。`vite.config.ts` 删除已无用的 `/rustfs` 代理（双模式下都不再使用）。字典 SQL 见新建增量脚本 `sql/knowhub-storage-dual-mode.sql`（不改原 `knowhub-storage.sql`，dict_id 20/21、dict_data_id 88-90，INSERT IGNORE 幂等，已部署环境直接跑）。详见下方「文件存储模块」章节。

### 2026-07-04 博客审核流水模块落地（审核历史 + 状态机 + 回避 + author_id）

博客审核雏形已落地（主表审核字段 + publish/review/revoke + 审核开关），但审核结果只覆盖主表只存最后一次、无历史可溯，且无状态机校验、无审核员回避、无用户ID稳定锁定。本次补审核流水表 + blog 表加 author_id + 审核动作字典 + 状态机/回避/流水写入 + 审核历史接口，并用「前后台审核记录展示」代替通知闭环。设计详见 `doc/blog/blog-review-flow-design.md`。

**新增接口（1 个）**
- `GET /blog/review-log/{blogId}`（权限 `knowhub:blog:info`）→ `List<ReviewLogVo>`，按动作时间升序返回该文章全量审核流水（含 operatorNickname，后端 left join sys_user 带出）

**既有接口语义增强（签名不变）**
- `PUT /blog/publish/{blogId}`：加状态机前置校验（仅 DRAFT/REJECTED/REVOKED 可发布，PUBLISHED/PENDING_REVIEW 报错）；写审核流水（开关开 SUBMIT/AUTHOR，开关关 PUBLISH/SYSTEM）
- `PUT /blog/revoke/{blogId}`：加状态机前置校验（仅 PUBLISHED 可撤回）；写流水 REVOKE/AUTHOR；reviewStatus 清为 NONE
- `PUT /blog/review`：加状态机前置校验（仅 PENDING_REVIEW 可审核）+ 审核员回避（userId ≠ author_id，作者不能审自己）；写流水 APPROVE/REJECT + REVIEWER
- `PUT /blog`（编辑）：加 PUBLISHED 禁止编辑校验（已发布文章须先撤回再编辑，防绕过审核改已发布内容）
- `POST /blog`（新增）：写入 `author_id`（当前用户 userId）

**新增字段**
- `blog` 表加 `author_id`（bigint，作者 userId，与 create_by(username) 互补，前台展示昵称 join sys_user 稳定）；BlogVo 加 authorId
- `ReviewLogVo`：reviewLogId/blogId/action/operatorId/operator/operatorNickname/role/advice/createTime

**SQL（新建独立脚本 `sql/knowhub-blog-review-log.sql`，不动 knowhub-blog.sql）**
- 建 `blog_review_log` 流水表（review_log_id/blog_id/action/operator_id/operator/role/advice/create_time + 两索引）
- `ALTER blog ADD author_id` + 按 create_by(username) 回填 user_id（information_schema 判列存在幂等）
- 新增字典 `blog_review_action`（5 值 SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，dict_id=22，dict_data_id 91-95）

**前端**
- `api/knowhub/blog.ts` 加 `getReviewLogApi`；`types/api/knowhub/blog.ts` 加 `ReviewLogRecord`、`BlogRecord` 加 authorId
- `BlogDetailDialog.vue` 加「审核历史」折叠区（ElCollapse + 时间线，DictTag 渲染 action，展示 operatorNickname/时间/advice）

**遵守约定**：未修改任何 `rookie-*` 代码/配置；改动全在 knowhub 模块 + rookie-ui knowhub 二开文件；通知预留 `notifyReviewResult` 空方法待 rookie 支持个人通知后接入。

**校验**：`mvn -q -pl knowhub -am -DskipTests compile` 通过；`rookie-ui npm run type-check` 通过。

**待用户人工验证**：① 跑 `sql/knowhub-blog-review-log.sql` → `DESC blog` 应含 author_id；现有未删 blog 行 author_id 应已回填（`SELECT COUNT(*) FROM blog WHERE author_id IS NULL AND deleted=0` 应为 0）；字典 blog_review_action 应 5 行。② 发布流程：开关开时发布→待审→审核员审核（通过/驳回）→查 `blog_review_log` 应有 SUBMIT+APPROVE/REJECT 流水；驳回后作者编辑→再发布→回待审，流水新增 SUBMIT。③ 状态校验：对已发布文章调审核接口应报"仅待审核文章可审核"；对草稿调撤回应报"仅已发布文章可撤回"；编辑已发布文章应报"已发布文章请先撤回再编辑"。④ 回避：作者尝试审核自己文章应报"不能审核自己提交的文章"。⑤ 详情弹窗「审核历史」折叠区展示时间线。前台审核时间线展示待前台开发落地，详见 `doc/blog/blog-front-review-display.md`。

### 2026-07-06 审核开关切换遗留 PENDING_REVIEW 对账定时任务

审核开关从开切到关后，仍处于 PENDING_REVIEW 的遗留文章无人收口（作者编辑/再发布/撤回均被状态机拒绝，审核员未必手动批，稿件卡死待审态）。本次用定时任务被动收口，不监听系统设置保存动作（`SysConfigController.editSysConfig` 在 rookie-system 通用 key-value 接口，无法可靠区分"这次保存恰好是 review_enabled"，改 rookie 不可行）。设计详见 `doc/blog/blog-review-flow-design.md` §七-2。

**后端（knowhub 模块，无新接口，新增定时任务 + Service 方法）**
- `BlogMapper`+xml 增 `listPendingReviewIds()`：`select blog_id from blog where status='PENDING_REVIEW' and deleted=0`，对账专用
- `BlogService`+`BlogServiceImpl` 增 `int reconcilePendingReview()`：查 list → 逐条转 PUBLISHED+APPROVED+reviewer=system+publishTime=now → 逐条写 `PUBLISH/SYSTEM` 流水（advice="审核关闭后定时任务自动放行"）→ 逐条 evictDetail；无 @Transactional，单条失败跳过不阻塞其它稿；返回放行条数
- `BlogServiceImpl.publishBlog` 审核开关开分支进入 PENDING_REVIEW 时 SET Redis 待审标记 `{baseKey}blog:review:pending-flag=1`（不计数仅标记存在性，无过期）
- `task/BlogReviewReconcileTask.java`（新建）：`@Scheduled(fixedDelayString = "#{${knowhub.blog.reconcile-interval-minutes:5} * 60 * 1000}", initialDelay = 60000)`。审核开关开→return；Redis 标记不存在→return（零扫表）；标记存在→调 `reconcilePendingReview()`→DEL 标记；放行 >0 记 info 日志

**配置（rookie-admin/application.yml 追加，用户已解除禁令）**
- `knowhub.blog.reconcile-interval-minutes: 5`（对账任务扫描间隔，分钟，默认 5）

**遵守约定**：未修改任何 `rookie-*` 模块代码/配置（application.yml 是 rookie-admin 的，用户已解除禁令）；@EnableScheduling 走 knowhub 自带 SchedulingConfig；流水复用 ReviewAction.PUBLISH/SYSTEM 不新增 action；不改 publish/edit/revoke 对 PENDING_REVIEW 的拒绝语义。

**校验**：`mvn -q -pl knowhub -am compile` 通过（EXIT=0）。

**待用户人工验证**：① 开审核状态下发布文章 → 进 PENDING_REVIEW，Redis 出现 `rookie:framework:blog:review:pending-flag=1`。② 关审核开关，等 ≤5 分钟，遗留待审文章应全部变 PUBLISHED，`blog_review_log` 出现 PUBLISH/SYSTEM + advice="审核关闭后定时任务自动放行"，Redis flag 被清。③ 审核员在定时任务跑之前手动批了某篇（flag 仍在），下次定时任务扫到空表、清 flag，无副作用。

### 2026-07-06 资源管理模块落地（资源 CRUD + 分类树 + 完整审核 + 互动 + 文件复用）

资源管理模块（后台菜单名"资源管理"，前台展示端待做叫"资源推荐"）——用户分享对他人有用的文件/程序/文档/网站链接。本轮新增接口共 18 个：

- 资源：`GET /resource/list`、`GET /resource/{resourceId}`、`POST /resource`、`PUT /resource`、`DELETE /resource/{resourceIds}`、`PUT /resource/publish/{resourceId}`、`PUT /resource/revoke/{resourceId}`、`PUT /resource/review`、`GET /resource/review-log/{resourceId}`、`PUT /resource/like/{resourceId}`、`PUT /resource/collect/{resourceId}`、`PUT /resource/rating/{resourceId}`、`GET /resource/download/{resourceId}`
- 资源分类：`GET /resource-category/tree`、`GET /resource-category/{categoryId}`、`POST /resource-category`、`PUT /resource-category`、`DELETE /resource-category/{categoryId}`

说明：资源分 FILE 文件 / LINK 链接两类。FILE 类复用文件存储模块（`businessType=RESOURCE_FILE`，PRIVATE/100MB/不限类型），通过 `file_object_id` 关联，后端在 add/edit 时调 `PUT /file/bind` 回填 `biz_ref_id`，删除资源时级联软删 file_object 行。审核流程复用博客那套（状态机+回避+流水表+对账任务），`ReviewAction` 枚举代码层复用，字典 `review_action`（dict_id=25，博客+资源共用）承接原 `blog_review_action` 的 5 个动作值。互动计数（点赞/收藏/评分）不冗余主表，走事实表聚合回填；下载数 `download_count` 仅 FILE 下载 +1（LINK 点击不计）。资源分类为自关联树，`resource_category_id=-1` 约定为"其他"（前端硬编码，删分类时挂载资源置 -1）。详见下方「资源管理模块」章节。

**字典与菜单编号修正（2026-07-07）**：rookie 上游新增"字典数据管理"(menu 87-92)和"系统设置"(menu 93-99)两模块，且 `sys_config_value_type` 占了 dict_id=22，故原"blog_review_action 改名迁移"作废——改为新建 `review_action` 字典到 dict_id=25（数据 103-107）。资源菜单编号从 102 起（86 资源管理页已存在 + 102-111 资源管理按钮 + 112 资源分类页 + 113-116 分类按钮）。前端博客/资源审核历史 DictTag dictKey 已统一为 `review_action`。

**配置**：审核开关 `knowhub.resource.review_enabled`（sys_config BOOLEAN，默认 false，`ResourceConfigReader.isReviewEnabled()` 读取）；对账间隔 `knowhub.resource.reconcile-interval-minutes`（application.yml，默认 5，@Scheduled fixedDelayString 读 yml 不读 sys_config）。

---

## 博客模块

> 路径前缀：`/blog`、`/tag`（blog 模块在 `com.knowhub.blog` 命名空间，不套 `/sys`）。
> 鉴权：写/审核/发布/撤回/删除类接口挂 `@PreAuthorize('knowhub:blog:*' / 'knowhub:tag:*')`，对应 `sys_menu` 中权限键；点赞/收藏仅要求登录。
> 全文检索：列表 `keyword` 参数走 MySQL FULLTEXT(ngram) 命中 `title`/`content`；标签筛选 `tagIds` 走 `blog_tag` 关联精确过滤，二者可复合。

### 文章接口

#### 1. 获取博客文章列表

**基本信息：** `GET /blog/list`　权限：`knowhub:blog:quarry`

**请求头：** `Token: <令牌值>`

**请求体（query string）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页条数，默认 10 |
| title | string | 否 | 标题模糊过滤 |
| keyword | string | 否 | 全文检索关键词（命中 title/content 的 FULLTEXT） |
| tagIds | array&lt;long&gt; | 否 | 标签 id 列表，需同时命中全部所选标签 |
| status | string | 否 | 文章状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED |
| reviewStatus | string | 否 | 审核状态过滤（管理台看待审传 PENDING） |
| createBy | string | 否 | 作者用户名过滤 |
| beginTime | datetime | 否 | 创建时间起 |
| endTime | datetime | 否 | 创建时间止 |

**响应示例：**
```json
{
  "code": 200,
  "msg": "请求成功",
  "data": {
    "pageNum": 1, "pageSize": 10, "total": 0, "list": [
      {
        "blogId": 1, "title": "示例", "summary": "...", "coverUrl": null,
        "status": "PUBLISHED", "publishTime": "2026-06-30 22:00:00",
        "viewCount": 0, "likeCount": 0, "collectCount": 0,
        "tagIds": [1, 2], "tagNames": ["Java", "架构"],
        "createBy": "admin", "createTime": "2026-06-30 21:00:00"
      }
    ]
  }
}
```

#### 2. 获取博客文章详情

**基本信息：** `GET /blog/{blogId}`　权限：`knowhub:blog:info`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`blogId` 路径参数）

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "blogId": 1, "title": "示例", "content": "正文...", "summary": "...", "coverUrl": null,
    "status": "PUBLISHED", "publishTime": "2026-06-30 22:00:00",
    "viewCount": 5, "likeCount": 0, "collectCount": 0,
    "reviewStatus": "NONE", "reviewer": null, "reviewTime": null, "reviewAdvice": null,
    "tagIds": [1, 2], "tagNames": ["Java", "架构"],
    "createBy": "admin", "createTime": "2026-06-30 21:00:00",
    "hasLiked": false, "hasCollected": false
  }
}
```
> 详情带 Redis 缓存；浏览量经 Redis 原子累加，不实时回写主表。

#### 3. 添加博客文章

**基本信息：** `POST /blog`　权限：`knowhub:blog:add`　日志：`@Log(博客文章, INSERT)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`BlogVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 标题 |
| content | string | 是 | 正文 |
| summary | string | 否 | 摘要 |
| coverUrl | string | 否 | 封面图地址（非必填） |
| tagIds | array&lt;long&gt; | 否 | 受控标签 id 列表，须全部为启用标签 |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 新建即草稿（status=DRAFT，reviewStatus=NONE），写入 author_id（当前用户 userId，与 create_by 互补）。

#### 4. 编辑博客文章

**基本信息：** `PUT /blog`　权限：`knowhub:blog:edit`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`BlogVo`）：** 同"添加"，`blogId` 必填；标签先删后插重建。

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 仅作者本人或具备 `knowhub:blog:review` 权限者可编辑他人文章。
> 状态机：仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 报"已发布文章请先撤回再编辑"，PENDING_REVIEW 报"审核中文章不能编辑，如需修改请先驳回或撤回后操作"（审核员审的是提交快照，作者审核中改动会污染依据）。

#### 5. 批量删除博客文章

**基本信息：** `DELETE /blog/{blogIds}`　权限：`knowhub:blog:delete`　日志：`@Log(博客文章, DELETE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`blogIds` 路径参数，逗号分隔）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 软删（deleted=1），并清理 `blog_tag` 关联与详情缓存。

#### 6. 发布博客文章

**基本信息：** `PUT /blog/publish/{blogId}`　权限：`knowhub:blog:publish`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 受审核开关 `knowhub.blog.review_enabled`（系统设置）控制：开关关→直接 `PUBLISHED` 并写 publish_time，写流水(PUBLISH, SYSTEM)；开关开→`PENDING_REVIEW` + review_status=PENDING，写流水(SUBMIT, AUTHOR)，待 `PUT /blog/review` 处理。
> 状态机：仅 DRAFT/REJECTED/REVOKED 可发布；PUBLISHED 报"已发布，无需重复发布"，PENDING_REVIEW 报"审核中，请勿重复提交"。

#### 7. 撤回博客文章

**基本信息：** `PUT /blog/revoke/{blogId}`　权限：`knowhub:blog:revoke`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 状态置 `REVOKED`、review_status 清为 NONE，写流水(REVOKE, AUTHOR)；可经编辑回 DRAFT 后再发布。
> 状态机：仅 PUBLISHED 可撤回，其他状态报"仅已发布文章可撤回"。

#### 8. 审核博客文章

**基本信息：** `PUT /blog/review`　权限：`knowhub:blog:review`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`ReviewVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| blogId | long | 是 | 文章ID |
| pass | boolean | 是 | true 通过→PUBLISHED；false 驳回→REJECTED |
| advice | string | 驳回必填 | 审核意见 |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 通过：status=PUBLISHED、publish_time=now、review_status=APPROVED、reviewer/review_time 写入，写审核流水(APPROVE, REVIEWER)。
> 驳回：status=REJECTED、review_status=REJECTED、review_advice 写入，写审核流水(REJECT, REVIEWER)。
> 状态机：仅 status=PENDING_REVIEW 可审核，其他状态报"仅待审核文章可审核"。
> 回避：审核员 userId ≠ author_id，作者审核自己文章报"不能审核自己提交的文章"。

#### 8.1 获取博客审核历史

**基本信息：** `GET /blog/review-log/{blogId}`　权限：`knowhub:blog:info`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`blogId` 路径参数）

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": [
    {
      "reviewLogId": 1, "blogId": 1, "action": "SUBMIT",
      "operatorId": 2, "operator": "zhangsan", "operatorNickname": "张三",
      "role": "AUTHOR", "advice": null, "createTime": "2026-07-04 10:00:00"
    },
    {
      "reviewLogId": 2, "blogId": 1, "action": "REJECT",
      "operatorId": 1, "operator": "admin", "operatorNickname": "管理员",
      "role": "REVIEWER", "advice": "正文图片需补充来源", "createTime": "2026-07-04 11:00:00"
    },
    {
      "reviewLogId": 3, "blogId": 1, "action": "SUBMIT",
      "operatorId": 2, "operator": "zhangsan", "operatorNickname": "张三",
      "role": "AUTHOR", "advice": null, "createTime": "2026-07-04 12:00:00"
    },
    {
      "reviewLogId": 4, "blogId": 1, "action": "APPROVE",
      "operatorId": 1, "operator": "admin", "operatorNickname": "管理员",
      "role": "REVIEWER", "advice": null, "createTime": "2026-07-04 13:00:00"
    }
  ]
}
```

> 按动作时间升序返回全量审核流水（`blog_review_log` 表，只追加不改不删）。
> `action` 见字典 `blog_review_action`（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH）。
> `operatorNickname` 由后端 left join sys_user 带出（用户删/改名时回落 null，此时前端可展示 operator 账号快照）。
> 前台文章详情页审核时间线 / 后台详情弹窗「审核历史」折叠区共用此接口。前台落地鉴权策略见 `doc/blog/blog-front-review-display.md`。

#### 9. 点赞 / 取消点赞

**基本信息：** `PUT /authoring/blog/{blogId}/like?liked={true|false}`　权限：登录即可（/authoring/** authenticated 兜底）　日志：`@Log(博客点赞, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`liked` query 参数，缺省 true）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 接口已从后台 `/blog/like/{blogId}` 挪到前台 `/authoring/blog/{blogId}/like`（后台管理用不到，与文章 `/authoring/article/{id}/like` 范式对齐）。
> `liked=true` 插入 `blog_like` 明细并 `like_count+1`；`false` 删除明细并 `-1`。幂等。

#### 10. 收藏 / 取消收藏

**基本信息：** `PUT /authoring/blog/{blogId}/collect?collected={true|false}`　权限：登录即可（/authoring/** authenticated 兜底）　日志：`@Log(博客收藏, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`collected` query 参数，缺省 true）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 同样从后台 `/blog/collect/{blogId}` 挪到前台 `/authoring/blog/{blogId}/collect`。与点赞对称，操作 `blog_collect` 与 `collect_count`。

### 标签接口（管理员维护）

#### 11. 获取标签列表

**基本信息：** `GET /tag/list`　权限：`knowhub:tag:quarry`

**请求头：** `Token: <令牌值>`

**请求体（query string）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tagName | string | 否 | 标签名模糊 |
| status | int | 否 | 0禁用 1启用 |

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": [
    {"tagId":1,"tagName":"Java","description":null,"sort":0,"status":1}
  ]
}
```

#### 12. 获取标签详情

**基本信息：** `GET /tag/{tagId}`　权限：`knowhub:tag:info`

**请求头：** `Token: <令牌值>`

**请求体：** 无

**响应示例：** `{"code":200,"msg":"请求成功","data":{"tagId":1,"tagName":"Java","status":1,...}}`

#### 13. 添加标签

**基本信息：** `POST /tag`　权限：`knowhub:tag:add`　日志：`@Log(博客标签, INSERT)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`TagVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tagName | string | 是 | 标签名（唯一） |
| description | string | 否 | 说明 |
| sort | int | 否 | 排序 |
| status | int | 否 | 0禁用 1启用，默认 1 |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

#### 14. 编辑标签

**基本信息：** `PUT /tag`　权限：`knowhub:tag:edit`　日志：`@Log(博客标签, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`TagVo`）：** `tagId` 必填，余同"添加"。

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

#### 15. 批量删除标签

**基本信息：** `DELETE /tag/{tagIds}`　权限：`knowhub:tag:delete`　日志：`@Log(博客标签, DELETE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`tagIds` 路径参数，逗号分隔）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 软删标签并级联清理 `blog_tag` 中该标签的关联行（主表不存标签字段，清关联即可）。

---

## 文件存储模块

> 路径前缀：`/file`（文件存储在 `com.knowhub` 命名空间，不套 `/sys`）。
> 鉴权：写/下载/管理类接口挂 `@PreAuthorize('knowhub:file:*')`，对应 `sys_menu` 中权限键；PUBLIC 回显接口 `GET /file/public/{id}` **无鉴权**（`SecurityConfig` 已 `permitAll` 放行 `/file/public/**`，供 `<img>`/`<a>` 无 Token 直接引用）；`GET /file/url/{id}`（取回显链接）也无鉴权。
> 预签名直传：上传不经后端字节流——`POST /file/upload-token` 签发 `PutObject` 预签名 URL，前端直传 RustFS，`POST /file/confirm/{id}` 用 `HeadObject` 核对。
> PUBLIC 回显：后端中转字节流——`GET /file/public/{id}` 由后端 `s3Client.getObject` 拉流后 `StreamingResponseBody` 回写，带 `Content-Type`/`Content-Length`/`Cache-Control`(7 天 immutable)/`ETag`，前端 `<img src="/file/public/123">` 同源拉图（相对路径不走 axios，靠 vite proxy / nginx 转发 `/file` 到后端，已在 `rookie-ui/vite.config.ts` 加 `/file` 代理）。
>
> **文件访问模式（双模式，系统设置 `knowhub.file.access_mode` 开关，`StorageConfigReader.accessMode` 读取）**——后端按模式决定发给前端的链接形态，前端永远只认后端给的链接，地址由后端决定，迁 OSS 只改后端配置：
> - **中转模式（transfer，默认）**：上传走 `PUT /file/proxy-upload/{id}`（后端代理转发字节流，同源带 Token）；PRIVATE 下载走 `GET /file/proxy/{id}`（后端中转回写字节流，同源带 Token）；PUBLIC 回显走 `/file/public/{id}`。适用于 OSS 在内网/不愿配 CORS，代价是后端经文件字节流。
> - **直链模式（direct）**：上传/下载走预签名绝对 URL（host 用系统设置 `knowhub.file.direct_base_url`，前端直连 nginx/OSS，需配 CORS）；PUBLIC 回显走 `{directBaseUrl}/{bucket}/{objectKey}` 公开读直链（不带签名，永不过期）。适用于 OSS 公网可达 + 配 CORS，后端不经字节流。
> - 直链模式 OSS 地址 base 走系统设置 `knowhub.file.direct_base_url`（`StorageConfigReader.directBaseUrl` 读取，填 nginx 公网反代域名或 OSS 公网 endpoint；留空回退 yml `storage.endpoint`）。
> - 各业务类型体积上限走系统设置 `knowhub.file.size_limit`（JSON 对象，key=业务类型 code，value=MB 数）、类型白名单走 `knowhub.file.type_whitelist`（JSON 对象，key=业务类型 code，value=逗号分隔），`StorageConfigReader.sizeLimitBytes`/`typeWhitelist` 读取。配置读取收口在 `StorageConfigReader`（同 `BlogConfigReader` 同构），换存储/换阈值仅改本类内部实现，签名与调用方零改动。
> - 配置型设置已于 2026-07-03 从字典迁移到系统设置模块（sys_config），迁移脚本 `sql/knowhub-sys-config-migration.sql`；枚举型字典（file_business_type/file_access/upload_status）保留走字典。

### 文件接口

#### 1. 签发上传令牌

**基本信息：** `POST /file/upload-token`　权限：`knowhub:file:upload`　日志：`@Log(文件对象, INSERT)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`UploadApplyVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| businessType | string | 是 | 业务类型：BLOG_COVER/BLOG_BODY/PROJECT_SRC/PROJECT_PKG/PROJECT_DOC/RESOURCE_FILE/PLUGIN_JAR |
| contentType | string | 是 | MIME 类型，须落在该业务类型的类型白名单内 |
| size | long | 是 | 声明字节数，须 ≤ 该业务类型的体积上限 |
| originalName | string | 否 | 原始文件名（仅展示，不参与 objectKey） |
| access | string | 否 | PUBLIC/PRIVATE，缺省按 businessType 默认值（BLOG_*→PUBLIC，其余→PRIVATE） |
| bizRefId | long | 否 | 业务关联 ID，可空（业务行未建时） |

**响应示例（中转模式）：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "uploadUrl": "/file/proxy-upload/1",
    "objectKey": "blog_body/2026/07/01/uuid.png",
    "objectId": 1,
    "expires": 600
  }
}
```

**响应示例（直链模式）：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "uploadUrl": "https://oss.your-domain.com/knowhub/blog_body/2026/07/01/uuid.png?X-Amz-Signature=...",
    "objectKey": "blog_body/2026/07/01/uuid.png",
    "objectId": 1,
    "expires": 600
  }
}
```

> 后端 insert `file_object(upload_status=PENDING)` 后签发；前端拿 `uploadUrl` 直接 `PUT` 上传（带 `Content-Type` header），传完调 `confirm`。
> **`uploadUrl` 形态由访问模式决定**：中转模式为 `/file/proxy-upload/{objectId}` 同源后端代理接口（前端 PUT 需带 `Token` 头，后端转发字节流写入 OSS）；直链模式为预签名绝对 URL（host 用系统设置 `knowhub.file.direct_base_url`，前端直连 nginx/OSS 不带 Token，需 OSS/nginx 配 CORS）。前端 `utils/upload.ts` 按链接形态（相对/绝对）自动决定带不带 Token，调用方无感。

#### 2. 上传确认

**基本信息：** `POST /file/confirm/{objectId}`　权限：`knowhub:file:upload`　日志：`@Log(文件对象, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体（query string）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| bizRefId | long | 否 | 业务关联 ID，传则一并回填 file_object.biz_ref_id |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 后端 `HeadObject` 取真实 `contentLength`/`contentType`/`etag`，核对类型落白名单、大小不超限，通过则置 `CONFIRMED` 并回填校验值；不符置 `FAILED`。

#### 3. PUBLIC 对象回显

**基本信息：** `GET /file/public/{objectId}`　权限：**无**（公开，`SecurityConfig` 已 `permitAll` 放行 `/file/public/**`）

**请求头：** 无

**请求体：** 无（`objectId` 路径参数）

**响应示例：** HTTP `200 OK`，响应头：

```
Content-Type: image/png
Content-Length: 123456
Cache-Control: public, max-age=604800, immutable
ETag: "d41d8cd98f00b204e9800998ecf8427e"
Accept-Ranges: none
```

响应体为图片字节流（后端 `s3Client.getObject` 拉取后 `StreamingResponseBody` 流式回写，大图不进内存）。

**状态码：**

| 场景 | 状态码 | 说明 |
|------|--------|------|
| 正常回显 | 200 | PUBLIC + CONFIRMED 对象，返回字节流 |
| 元数据行不存在 / 未确认（PENDING/FAILED/GC） | 404 | 空体，对外视作不存在，避免状态枚举探测 |
| 非 PUBLIC 对象 | 403 | 空体，用错接口（PRIVATE 走 `/file/download/{id}`） |
| RustFS 对象实际不存在（元数据与对象不一致） | 404 | 空体，`getObject` 抛 `NoSuchKeyException` 兜底 |
| 其它 S3/网络异常 | 500 | 空体，log.warn |

> 后端中转而非 302 跳 RustFS，好处：① 前端 `<img src="/file/public/{id}">` 同源拉图，无 CORS/跨网段可达性问题；② 不依赖 RustFS 桶策略公开可读；③ 后续迁移 OSS 只改后端 `StorageProperties`/`S3Client` 配置，前端零改动。异常在 Controller 内自吞返回纯状态码空体，**不进 `GlobalExceptionHandler`**（`@RestControllerAdvice` 会包成 `Result` JSON，对 `<img>` 无效）。首版加 `Cache-Control` + `ETag` 强缓存，不做 `If-None-Match` 304（immutable 已让浏览器不发验证请求）。接口不挑类型——"渲染还是下载"取决于前端引用方式：`<img>` 渲染图片、`<a>` 点击下载。压缩包按设计应走 PRIVATE 的 `/file/download/{id}`。

#### 4. 获取下载链接

**基本信息：** `GET /file/download/{objectId}`　权限：`knowhub:file:download`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectId` 路径参数）

**响应示例（中转模式）：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "downloadUrl": "/file/proxy/1",
    "expires": 300,
    "originalName": "x.zip"
  }
}
```

**响应示例（直链模式）：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "downloadUrl": "https://oss.your-domain.com/knowhub/.../x.zip?X-Amz-Signature=...&response-content-disposition=attachment%3Bfilename%3D%22x.zip%22",
    "expires": 300,
    "originalName": "x.zip"
  }
}
```

> PRIVATE 对象鉴权后按访问模式发链接：中转模式填 `/file/proxy/{objectId}`（前端 `fetch` 带 `Token` 取 blob 下载，见接口 5）；直链模式填预签名绝对 URL（带 `attachment;filename`，前端 `window.open` 直连拉取，需 OSS/nginx 配 CORS）。PUBLIC 对象下载也可直接用 `/file/public/{id}`（后端中转回写字节流，`<a>`/`window.open` 直接拉取，无 CORS）。
> **鉴权重载**（2026-08-08）：`FileService.getDownloadUrl/streamDownloadObject` 加 `bizAuthorized` 布尔重载。无 flag 走 owner 闸（仅上传人 OR `knowhub:file:review` 管理员可下 PRIVATE）——保留给`/file/download/{objectId}`这种"用户直选 objectId、文件层无业务上下文"的通用入口；带 `bizAuthorized=true` 跳过 owner 闸——业务模块（资源 `downloadResource`、项目 `downloadFile`/`getFileDownloadUrl`）已在本业务层校验业务可见性（资源 PUBLISHED、项目 `canDownload`），不再要求是文件上传人/文件管理员。PUBLIC 文件两入参对称不鉴权。中转模式业务下载命中 `/file/proxy/{objectId}` 的字节流回写同样靠 `streamDownloadObject(objectId, true)` 放行。

#### 5. 中转下载（后端代理回写字节流）

**基本信息：** `GET /file/proxy/{objectId}`　权限：`knowhub:file:download`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectId` 路径参数）

**响应示例：** HTTP `200 OK`，响应头：

```
Content-Type: application/zip
Content-Length: 123456
Content-Disposition: attachment;filename="x.zip"
Cache-Control: no-cache
Accept-Ranges: none
```

响应体为文件字节流（后端 `s3Client.getObject` 拉取后 `StreamingResponseBody` 流式回写）。PRIVATE 带 `attachment;filename` 强制下载；PUBLIC 走此接口也回写字节流（但通常用 `/file/public/{id}` 无鉴权更合适）。

**状态码：** 同 PUBLIC 回显（404 不存在/未确认、403 非 PUBLIC PRIVATE 无权、500 异常），异常不进 `GlobalExceptionHandler`。

> 中转模式 PRIVATE 下载用此接口。前端因 `window.open` 不带 Token，改用 `fetch` 带 `Token` 头取 blob 再 `a.click()` 触发下载（PRIVATE 频率低，blob 进内存可接受）。详见 `rookie-ui/src/views/knowhub/file/index.vue` `handleDownloadFile`。

#### 6. 后端代理转发上传

**基本信息：** `PUT /file/proxy-upload/{objectId}`　权限：`knowhub:file:upload`　日志：`@Log(文件对象, INSERT)`

**请求头：** `Token: <令牌值>`　`Content-Type: <与申请令牌时一致的 MIME>`

**请求体：** 文件字节流（原始二进制，非 multipart）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 中转模式上传用此接口。前端拿 `applyUploadToken` 返回的 `uploadUrl`（中转模式为 `/file/proxy-upload/{objectId}`）直接 `PUT` 字节流（带 `Token` + `Content-Type`），后端 `s3Client.putObject` 写入 OSS 后走 `confirm` 核对置 `CONFIRMED`。后端经上传字节流（代价是流量过后端），适用于 OSS 内网/不愿配 CORS。前端 `utils/upload.ts` 按链接形态自动带 Token，调用方无感。

#### 7. 取 PUBLIC 回显链接（按访问模式）

**基本信息：** `GET /file/url/{objectId}`　权限：**无**（公开）

**请求头：** 无

**请求体：** 无（`objectId` 路径参数）

**响应示例（中转模式）：**
```json
{"code":200,"msg":"请求成功","data":"/file/public/1"}
```

**响应示例（直链模式）：**
```json
{"code":200,"msg":"请求成功","data":"https://oss.your-domain.com/knowhub/blog_body/2026/07/01/uuid.png"}
```

> 返回 PUBLIC 对象按当前访问模式的回显链接：中转模式 → `/file/public/{id}`（后端中转）；直链模式 → `{directBaseUrl}/{bucket}/{objectKey}`（公开读直链，不带签名，永不过期，依赖 OSS 桶公开可读）。前端上传成功后调此接口取链接回填（`utils/upload.ts`），使前端不关心 OSS 地址、迁移零改动；接口异常时回退 `/file/public/{id}`（中转接口两种模式都可用）。非 PUBLIC 或未确认对象回退 `/file/public/{id}`（访问时由中转接口返 403/404）。

#### 8. 获取文件对象列表

**基本信息：** `GET /file/list`　权限：`knowhub:file:quarry`

**请求头：** `Token: <令牌值>`

**请求体（query string）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页条数，默认 10 |
| businessType | string | 否 | 业务类型过滤 |
| uploadStatus | string | 否 | 上传状态：PENDING/CONFIRMED/FAILED/GC |
| access | string | 否 | 访问语义：PUBLIC/PRIVATE |
| createBy | string | 否 | 上传人过滤 |
| beginTime | datetime | 否 | 创建时间起 |
| endTime | datetime | 否 | 创建时间止 |

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "pageNum": 1, "pageSize": 10, "total": 0, "list": [
      {
        "objectId": 1, "bucket": "knowhub", "objectKey": "blog_body/.../x.png",
        "originalName": "x.png", "contentLength": 204800, "contentType": "image/png",
        "checksum": "etag...", "businessType": "BLOG_BODY", "bizRefId": null,
        "access": "PUBLIC", "uploadStatus": "CONFIRMED",
        "createBy": "admin", "createTime": "2026-07-01 10:00:00"
      }
    ]
  }
}
```

#### 9. 获取文件对象详情

**基本信息：** `GET /file/{objectId}`　权限：`knowhub:file:info`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectId` 路径参数）

**响应示例：** `{"code":200,"msg":"请求成功","data":{...同列表项...}}`

#### 10. 绑定业务关联

**基本信息：** `PUT /file/bind`　权限：`knowhub:file:upload`　日志：`@Log(文件对象, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`BindVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| objectId | long | 是 | 文件对象 ID |
| bizRefId | long | 是 | 业务关联 ID（业务行创建后回填，便于删业务行时级联清文件） |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

#### 11. 批量删除文件对象

**基本信息：** `DELETE /file/{objectIds}`　权限：`knowhub:file:delete`　日志：`@Log(文件对象, DELETE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectIds` 路径参数，逗号分隔）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 软删 `file_object(deleted=1)`，对象本体由 `FileGcTask` 定时 `DeleteObject` 后物理删元数据（异步，支持误删恢复窗口）。
## 资源管理模块

> 路径前缀：`/resource`、`/resource-category`（资源模块在 `com.knowhub` 命名空间，不套 `/sys`）。
> 鉴权：写/审核/发布/撤回/删除/下载类接口挂 `@PreAuthorize('knowhub:resource:*' / 'knowhub:resource:category:*')`，对应 `sys_menu` 中权限键；点赞/收藏/评分仅要求登录（Token 头已自动注入）。
> 后台菜单名"资源管理"，前台展示端待做叫"资源推荐"。
> 资源分两类：FILE 文件（走文件存储模块上传，关联 `file_object_id`）/ LINK 链接（存 `link_url`）。
> 审核流程复用博客那套（状态机+回避+流水表+对账任务），开关 `knowhub.resource.review_enabled`（sys_config，`ResourceConfigReader` 读取）。
> 互动计数（点赞/收藏/评分）不冗余主表，走事实表聚合回填；下载数 `download_count` 仅 FILE 下载 +1。
> 资源分类为自关联树，`resource_category_id=-1` 约定为"其他"（前端硬编码）。

### 资源接口

#### `GET /resource/list` — 资源列表（分页）

**权限**：`knowhub:resource:quarry`

**查询参数**（query string）：`pageNum`/`pageSize`（分页）、`title`（模糊）、`resourceType`（FILE/LINK）、`resourceCategoryId`（-1=其他）、`status`（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED）、`reviewStatus`（NONE/PENDING/APPROVED/REJECTED）、`createBy`（作者用户名）、`beginTime`/`endTime`（创建时间区间）

**响应**：`Result<PageInfo<ResourceVo>>`，`ResourceVo` 字段：resourceId/authorId/resourceType/resourceCategoryId/categoryName(join带出,-1=其他时null)/title/summary/description(列表不带,详情才有)/fileObjectId/originalName/contentLength/contentType(FILE类型join带出)/linkUrl/linkIcon/status/reviewStatus/publishTime/downloadCount/likeCount/collectCount/ratingAvg/ratingCount(互动计数聚合回填)/createBy/authorNickname(join带出)/createTime/updateBy/updateTime。列表不带 hasLiked/hasCollected/myScore/downloadUrl（仅详情回填）。

#### `GET /resource/{resourceId}` — 资源详情

**权限**：`knowhub:resource:info`

**响应**：`Result<ResourceVo>`，比列表多回填：description(大字段)/hasLiked/hasCollected/myScore(当前用户态)/downloadUrl(FILE类型按访问模式回填,中转/file/proxy/{objectId}或预签名，调 `getDownloadUrl(id, true)` 跳过 owner 闸)/originalName/contentLength/contentType。

#### `POST /resource` — 新增资源（草稿）

**权限**：`knowhub:resource:add` | **请求体**：`ResourceVo`（resourceType 必填；FILE 须 fileObjectId；LINK 须 linkUrl；title 必填）

**逻辑**：新建即 DRAFT，author_id=当前userId，resourceCategoryId 缺省 -1。FILE 类落库后调 `PUT /file/bind` 回填 file_object.biz_ref_id=resourceId。

**响应**：`Result<Boolean>`

#### `PUT /resource` — 编辑资源

**权限**：`knowhub:resource:edit` | **请求体**：`ResourceVo`（resourceId 必填）

**状态机前置**：仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 禁止编辑（须先撤回）；PENDING_REVIEW 禁止编辑（审核中）。校验归属（作者本人或管理员）。

**响应**：`Result<Boolean>`

#### `DELETE /resource/{resourceIds}` — 批量删除资源

**权限**：`knowhub:resource:delete` | **路径参数**：`resourceIds` 逗号分隔

**逻辑**：软删 resource；FILE 类级联软删关联 file_object 行（对象本体由 FileGcTask 异步清）。

**响应**：`Result<Boolean>`

#### `PUT /resource/publish/{resourceId}` — 发布资源

**权限**：`knowhub:resource:publish` | **状态机前置**：仅 DRAFT/REJECTED/REVOKED 可发布

**逻辑**：读 `ResourceConfigReader.isReviewEnabled()`——开 → status=PENDING_REVIEW+reviewStatus=PENDING+写流水 SUBMIT/AUTHOR+SET Redis标记 `resource:review:pending-flag`；关 → status=PUBLISHED+publishTime=now+reviewStatus=NONE+写流水 PUBLISH/SYSTEM。校验归属。

**响应**：`Result<Boolean>`

#### `PUT /resource/revoke/{resourceId}` — 撤回资源

**权限**：`knowhub:resource:revoke` | **状态机前置**：仅 PUBLISHED 可撤回

**逻辑**：status=REVOKED+reviewStatus=NONE+写流水 REVOKE/AUTHOR。校验归属。

**响应**：`Result<Boolean>`

#### `PUT /resource/review` — 审核资源

**权限**：`knowhub:resource:review` | **请求体**：`ResourceReviewVo`（resourceId/pass/advice；pass=false 时 advice 必填）

**状态机前置**：仅 PENDING_REVIEW 可审核。**审核员回避**：当前 userId ≠ author_id（作者不能审自己）。主表不存 reviewer/reviewTime/reviewAdvice（走流水表）。

**逻辑**：pass=true → status=PUBLISHED+publishTime=now+reviewStatus=APPROVED+写流水 APPROVE/REVIEWER；pass=false → status=REJECTED+reviewStatus=REJECTED+写流水 REJECT/REVIEWER。

**响应**：`Result<Boolean>`

#### `GET /resource/review-log/{resourceId}` — 资源审核历史

**权限**：`knowhub:resource:reviewLog`

**响应**：`Result<List<ResourceReviewLogVo>>`，按动作时间升序。字段：reviewLogId/resourceId/action(字典 review_action,SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH)/operatorId/operator/operatorNickname(join sys_user带出)/role(AUTHOR/REVIEWER/SYSTEM)/advice/createTime。

#### `PUT /resource/like/{resourceId}?liked=true|false` — 点赞/取消点赞

**请求参数**：`liked`（true 点赞 / false 取消）。登录即可，无 @PreAuthorize。

**逻辑**：toggle，事实表 resource_like insert/delete（UNIQUE(resource_id,user_id) 幂等）。计数不冗余主表，读时聚合 COUNT。

**响应**：`Result<Boolean>`

#### `PUT /resource/collect/{resourceId}?collected=true|false` — 收藏/取消收藏

同点赞结构，事实表 resource_collect。

#### `PUT /resource/rating/{resourceId}?score=1..5` — 评分

**请求参数**：`score`（1-5，越界报错）。登录即可。

**逻辑**：upsert 事实表 resource_rating（UNIQUE 支撑，存在则改分）。评分均值/计数走聚合读时算（详情/列表 fillInteractCounts 回填 ratingAvg/ratingCount）。

**响应**：`Result<Boolean>`

#### `GET /resource/download/{resourceId}` — 获取 FILE 资源下载链接

**权限**：`knowhub:resource:download`

**逻辑**：校验 PUBLISHED + FILE 类型 + fileObjectId 非空；`download_count+1`（原子自增）；调 `FileService.getDownloadUrl(fileObjectId, true)`（bizAuthorized=true 跳过文件底座 owner 闸——资源层已校验 PUBLISHED 业务可见性，登录非上传人/无 `knowhub:file:review` 的普通用户也可下别人上传的 PUBLISHED 资源）取下载链接（中转模式 /file/proxy/{objectId}；直链模式带 attachment;filename 预签名）。LINK 类型不走此接口（前端直接用 linkUrl 外链打开）。资源文件 access 当前固定 PRIVATE（`RESOURCE_FILE` 枚举默认），后期以 L1~L3 等级落地（语义：L1 公开/非 L1 私有），届时 `access` 不再走枚举默认。

**响应**：`Result<String>`（下载链接字符串）

### 资源分类接口

#### `GET /resource-category/tree` — 资源分类树

**权限**：`knowhub:resource:category:quarry`

**响应**：`Result<List<ResourceCategoryTreeVo>>`，全量启用分类组树（parent_id=0 顶级，按 parent_id/sort 排序）。字段：categoryId/parentId/categoryName/sort/status/children。前端约定 -1=其他（不在树内，前端硬编码加"其他"虚拟节点）。

#### `GET /resource-category/{categoryId}` — 分类详情

**权限**：`knowhub:resource:category:quarry` | **响应**：`Result<ResourceCategoryVo>`

#### `POST /resource-category` — 新增分类

**权限**：`knowhub:resource:category:add` | **请求体**：`ResourceCategoryVo`（categoryName 必填，parentId 缺省 0=顶级，sort 缺省 0）

**响应**：`Result<Boolean>`

#### `PUT /resource-category` — 编辑分类

**权限**：`knowhub:resource:category:edit` | **请求体**：`ResourceCategoryVo`（categoryId 必填）

**响应**：`Result<Boolean>`

#### `DELETE /resource-category/{categoryId}` — 删除分类

**权限**：`knowhub:resource:category:delete`

**逻辑**：有子分类拒绝删（提示"先处理子分类"）；无子分类则事务内 `UPDATE resource SET resource_category_id=-1 WHERE resource_category_id=该分类`（挂载资源归"其他"）+ 软删分类行。

**响应**：`Result<Boolean>`

---

## 项目管理模块

> 项目管理偏向归档记录（后续可能融入代码版本管理）。记录项目介绍、相关文档、项目代码/安装包存储（文件可下载，相当于开文件夹统一管理项目内容），展示负责人/参与者/导师。
> 严苛权限分级：**系统权限**（view/download/edit:l1-l3，全局分等级，所有项目）+ **项目内权限**（project_member.can_view/can_download/can_edit，单项目不分等级）+ LEADER 全权。项目分等级（level 1/2/3）对标权限。
> 等级权限由后端 `ProjectPermissionResolver` 一次扫描 `List<Permission>` 取最高等级判定（admin 零特判，同时持有 l1+l2 取 l2）；列表可见性由 SQL 过滤（`level<=userViewLevel OR project_id IN (member can_view=1 子查询)`），详情/下载/编辑二次 `canOp` 校验。
> 审核流程复用博客/资源范式（状态机+回避+流水表+对账任务），ReviewAction 枚举 + review_action 字典(dict_id=25) 复用，不建新字典。审核开关 `knowhub.project.review_enabled` 默认 true 开启。
> 路由前缀 `/project`（knowhub 命名空间，不套 /sys）。

### 项目 CRUD 与审核

#### `GET /project/list` — 获取项目列表

**权限**：`knowhub:project:quarry`（进页面门槛；实际可见性由后端按权限等级+成员过滤）

**请求头**：`Token`

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| title | String | 项目名称模糊 |
| type | String | 项目类型（字典 project_type，当前 COMPETITION） |
| level | Integer | 项目等级 1/2/3 |
| status | String | 项目状态（字典 project_status） |
| reviewStatus | String | 审核状态（字典 review_status） |
| createBy | String | 负责人用户名 |
| beginTime | Date | 创建时间起（yyyy-MM-dd） |
| endTime | Date | 创建时间止（yyyy-MM-dd） |
| pageNum | Integer | 页码 |
| pageSize | Integer | 每页条数 |

**逻辑**：service 层 `ProjectPermissionResolver.resolve()` 取当前用户查看等级 userViewLevel + userId 透传 Mapper；SQL `where deleted=0 and (level<=userViewLevel OR project_id IN (select ... from project_member where user_id=? and can_view=1))`。无系统查看权限者 userViewLevel=0 只走 member 分支 → 只看参与的项目。join sys_user 取 author_nickname。

**响应**：`Result<PageInfo<ProjectVo>>`，ProjectVo 字段：projectId/title/type/level/summary/articleId/authorId/authorNickname/status/reviewStatus/publishTime/createBy/createTime（列表不带 description 大字段，详情接口才查）。

#### `GET /project/{projectId}` — 获取项目详情

**权限**：`knowhub:project:info`

**逻辑**：按主键取未删项目（含 description + join sys_user 取 authorNickname）；二次 `canOp(view)` 校验，无权抛 500"无权查看该项目"；回填当前用户对该项目权限态 canView/canDownload/canEdit/myMemberRole（供前端控制按钮显隐）。

**响应**：`Result<ProjectVo>`（含 description + 权限态字段）

#### `POST /project` — 新增项目

**权限**：`knowhub:project:add`

**请求体**：`ProjectVo`（title/type/level 必填，summary/description/articleId 可选）

**逻辑**：校验 title/type/level（level 1-3）；创建者设为 author_id（=LEADER）；status=DRAFT、reviewStatus=NONE；level 缺省 L1。按 type 配套写子表（COMPETITION 走独立子表接口维护）；创建者默认 LEADER member（can_view/can_download/can_edit 全 1）。

**响应**：`Result<Boolean>`

#### `PUT /project` — 编辑项目

**权限**：`knowhub:project:add`

**请求体**：`ProjectVo`（projectId 必填）

**逻辑**：状态机前置（仅 DRAFT/REJECTED/REVOKED/ARCHIVED 可编辑；PUBLISHED 须先撤回、PENDING_REVIEW 审核中不能改）；`canOp(edit)` 校验；改 level 需自身 edit 等级 >= 新 level（防降级再让别人改）；动态列更新。

**响应**：`Result<Boolean>`

#### `DELETE /project/{projectIds}` — 批量删除项目

**权限**：`knowhub:project:delete`

**逻辑**：每项目校验 LEADER 或 knowhub:project:delete 权限；事务级联软删 member + project_file + file_object 三类（PROJECT_SRC/PKG/DOC softDeleteByBizRef，对象本体由 FileGcTask 回收）+ 物理删类型子表 + 软删主表。

**响应**：`Result<Boolean>`

#### `PUT /project/publish/{projectId}` — 发布项目

**权限**：`knowhub:project:publish`

**逻辑**：状态机前置（仅 DRAFT/REJECTED/REVOKED/ARCHIVED 可发布）；`canOp(edit)` 校验；经审核开关决定 PENDING_REVIEW（写 SUBMIT/AUTHOR 流水 + SET Redis 待审标记）或 PUBLISHED（写 PUBLISH/SYSTEM 流水 + 回填 publishTime）。

**响应**：`Result<Boolean>`

#### `PUT /project/revoke/{projectId}` — 撤回项目

**权限**：`knowhub:project:revoke`

**逻辑**：仅 PUBLISHED 可撤回；`canOp(edit)` 校验；置 REVOKED + 写 REVOKE/AUTHOR 流水。

**响应**：`Result<Boolean>`

#### `PUT /project/review` — 审核项目

**权限**：`knowhub:project:review`

**请求体**：`ProjectReviewVo`（projectId/pass/advice）

| 参数 | 类型 | 说明 |
|---|---|---|
| projectId | Long | 项目ID |
| pass | Boolean | true 通过→PUBLISHED；false 驳回→REJECTED |
| advice | String | 审核意见（驳回必填，通过可选） |

**逻辑**：仅 PENDING_REVIEW 可审核；回避（author_id 比对，负责人不能审自己）；pass=true 置 PUBLISHED+回填 publishTime+写 APPROVE/REVIEWER 流水；pass=false 校验 advice 必填+置 REJECTED+写 REJECT/REVIEWER 流水。

**响应**：`Result<Boolean>`

#### `GET /project/review-log/{projectId}` — 获取项目审核历史

**权限**：`knowhub:project:reviewLog`

**逻辑**：按 projectId 查审核流水（升序），left join sys_user 取 operatorNickname。

**响应**：`Result<List<ProjectReviewLogVo>>`，字段：reviewLogId/projectId/action(字典 review_action)/operatorId/operator/operatorNickname/role/advice/createTime。

### 项目成员管理

#### `GET /project/member/{projectId}` — 成员列表

**权限**：`knowhub:project:member`（需先 canOp(view) 通过）

**逻辑**：join sys_user 取 nick_name/username，按 LEADER→MENTOR→MEMBER 排序。

**响应**：`Result<List<ProjectMemberVo>>`，字段：memberId/projectId/userId/memberRole(字典 project_member_role)/canView/canDownload/canEdit/nickname/username。

#### `POST /project/member` — 新增成员

**权限**：`knowhub:project:member`

**请求体**：`ProjectMemberVo`（projectId/userId/memberRole 必填，can_* 未传按角色给默认：LEADER→1/1/1、MENTOR→1/1/0、MEMBER→1/0/0）

**逻辑**：`canOp(edit)` 校验；同一用户不可重复加入；LEADER 唯一性（新增 LEADER 时原 LEADER 自动降为 MEMBER）；若新成员是 LEADER 同步更新主表 author_id（换负责人）。单点编辑/权限微调用本接口或 PUT。

**响应**：`Result<Boolean>`

#### `POST /project/member/batch/{projectId}` — 批量新增成员（默认 MEMBER，参考通知分组）

**权限**：`knowhub:project:member`

**请求体**：`number[]`（userIds 数组）

**逻辑**：`canOp(edit)` 校验；逐个加入，默认 MEMBER 角色（按角色给默认标志位 MEMBER→1/0/0），已存在的跳过（幂等批量加）。单点改角色/权限标志位走 PUT /project/member。前端添加成员走搜用户子弹窗（调 GET /sys/user/list 搜索）+ 本接口批量加。

**响应**：`Result<Boolean>`

#### `PUT /project/member` — 编辑成员

**权限**：`knowhub:project:member`

**请求体**：`ProjectMemberVo`（memberId 必填）

**逻辑**：`canOp(edit)` 校验；提升为 LEADER 时原 LEADER 降为 MEMBER + 同步主表 author_id；调角色/标志位。

**响应**：`Result<Boolean>`

#### `DELETE /project/member/{memberId}` — 删除成员

**权限**：`knowhub:project:member`

**逻辑**：`canOp(edit)` 校验；LEADER 不可直接删（需先换负责人，抛 500"负责人不可直接删除，请先转移负责人"）；软删。

**响应**：`Result<Boolean>`

### 项目文件树管理（GitHub 式侧边栏）

#### `GET /project/file/tree/{projectId}` — 文件树（树形）

**权限**：`knowhub:project:info`（需 canOp(view) 通过）

**逻辑**：按 projectId 查全部文件节点（扁平带 parentId，join file_object 取元数据），service 层按 parentId 组装为树形。

**响应**：`Result<List<ProjectFileTreeVo>>`，字段：fileId/projectId/parentId/name/isDir(1目录/0文件)/objectId/sort/originalName/contentLength/contentType/businessType(字典 file_business_type)/children(目录才有)。

#### `GET /project/file/list/{projectId}` — 文件列表（扁平）

**权限**：`knowhub:project:info`

**响应**：`Result<List<ProjectFileVo>>`（扁平，无 children）

#### `POST /project/file/folder` — 新建文件夹

**权限**：`knowhub:project:add`（需 canOp(edit) 通过）

**请求体**：`ProjectFileVo`（projectId/name/parentId/sort）

**逻辑**：is_dir=1，object_id=null；`canOp(edit)` 校验。

**响应**：`Result<Boolean>`

#### `POST /project/file/node` — 新增文件节点

**权限**：`knowhub:project:add`（需 canOp(edit) 通过）

**请求体**：`ProjectFileVo`（projectId/name/objectId 必填/parentId/sort）

**逻辑**：is_dir=0，关联 file_object.object_id（前端先走预签名上传流程拿 objectId 再调本接口）；`canOp(edit)` 校验；绑 file_object.biz_ref_id=projectId（级联删依据）。**上传文件 access 按项目等级派生**（前端 `presignedUploadFlow` 透传）：L1→PUBLIC（公开，走 `/file/public` 直链）、L2/L3 及未知等级→PRIVATE（私有，走 `/file/proxy` 中转，下载仍由项目层 `canOp(download)` 鉴权后 `getDownloadUrl(objectId, true)` 跳过文件底座 owner 闸）；与资源模块的"后期再加 L1~L3 权限"方向一致（非 L1 即私有），项目模块先行落地。

**响应**：`Result<Boolean>`

#### `PUT /project/file/node` — 编辑文件节点

**权限**：`knowhub:project:add`（需 canOp(edit) 通过）

**请求体**：`ProjectFileVo`（fileId 必填，部分更新：name/parentId/sort）

**逻辑**：`canOp(edit)` 校验；动态列更新（改名/移动/排序）。

**响应**：`Result<Boolean>`

#### `DELETE /project/file/{fileId}` — 删除文件节点

**权限**：`knowhub:project:add`（需 canOp(edit) 通过）

**逻辑**：`canOp(edit)` 校验；目录递归软删子节点；文件叶子级联软删 file_object（对象本体由 FileGcTask 回收）。

**响应**：`Result<Boolean>`

#### `GET /project/file/download/{fileId}` — 获取文件下载链接

**权限**：`knowhub:project:info`（需 canOp(download) 通过）

**逻辑**：目录不可下载；文件叶子 `canOp(download)` 校验通过后调 fileService.getDownloadUrl 取中转/预签名链接。

**响应**：`Result<String>`（下载链接字符串）

---

### 文章管理模块

文章=章节集合（参考 Vue/Element-Plus 官方文档站：一篇文章是一本"文档书"，章节是其中的"页面"）。系统审核颗粒度到文章（对外发布把关）；章节走文章内部可见性三档（PRIVATE/SEMIPUBLIC/PUBLIC）+ 作者审核（仅半公开）。权限模型轻量：系统级 view/edit:l1-l3 分等级 + 作者归属（author_id 单一所有者，作者全权不看等级/不看 visibility），无成员表/无项目内标志位。

**权限键**：按钮(非等级) knowhub:article:动作 走 @PreAuthorize；等级(view/edit:lN)由后端 ArticlePermissionResolver 扫 perms 取最高等级判定，非框架 hasAuthority。

#### `GET /article/list` — 获取文章列表

**权限**：`knowhub:article:quarry`（进页面门槛；列表可见性由后端 SQL 过滤：level<=userViewLevel OR author_id=userId）

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| pageNum | number | 页码 |
| pageSize | number | 每页条数 |
| title | string | 标题模糊（可选） |
| level | number | 等级 1/2/3（可选） |
| visibility | string | 可见性 PRIVATE/SEMIPUBLIC/PUBLIC（可选） |
| status | string | 状态（可选） |
| reviewStatus | string | 审核状态（可选） |
| createBy | string | 作者用户名（可选） |
| authorId | number | 作者userId过滤，"我的文章"场景（可选） |
| beginTime | string | 创建时间起 yyyy-MM-dd（可选） |
| endTime | string | 创建时间止 yyyy-MM-dd（可选） |

**响应**：`Result<PageInfo<ArticleVo>>`（列表含 authorNickname join 带出，不回填权限态）

#### `GET /article/{articleId}` — 获取文章详情

**权限**：`knowhub:article:info`（二次校验 canOp(view)：作者能看自己文章不看等级）

**响应**：`Result<ArticleVo>`（回填 canView/canEdit/isAuthor 供前端控制按钮显隐）

#### `POST /article` — 新增文章

**权限**：`knowhub:article:add`

**请求体**：`ArticleVo`（title/level/visibility/summary/coverObjectKey；authorId=当前用户由后端写）

**响应**：`Result<Boolean>`

#### `PUT /article` — 编辑文章

**权限**：`knowhub:article:add`（编辑复用 add 权限键，无独立 edit 键）

**逻辑**：PUBLISHED 禁编须先撤回；改 level 升级需自身 edit 等级>=新 level（作者除外）。

**请求体**：`ArticleVo`（articleId 必填）

**响应**：`Result<Boolean>`

#### `DELETE /article/{articleIds}` — 批量删除文章

**权限**：`knowhub:article:delete`（作者或 delete 权限可删；级联软删 chapter + 封面 file_object）

**响应**：`Result<Boolean>`

#### `PUT /article/publish/{articleId}` — 发布文章

**权限**：`knowhub:article:publish`（canOp(edit) 校验）

**逻辑**：审核开关开→PENDING_REVIEW+SET Redis 标记+写流水 SUBMIT；关→PUBLISHED+写流水 PUBLISH。

**响应**：`Result<Boolean>`

#### `PUT /article/revoke/{articleId}` — 撤回文章

**权限**：`knowhub:article:revoke`（仅 PUBLISHED 可撤回；canOp(edit) 校验）

**响应**：`Result<Boolean>`

#### `PUT /article/review` — 审核文章

**权限**：`knowhub:article:review`（仅 PENDING_REVIEW 可审；作者不能审自己 author_id 比对回避）

**请求体**：`ArticleReviewVo`（articleId/pass/advice；pass=false 时 advice 必填）

**响应**：`Result<Boolean>`

#### `GET /article/review-log/{articleId}` — 获取文章审核历史

**权限**：`knowhub:article:reviewLog`

**响应**：`Result<List<ArticleReviewLogVo>>`（按时间升序，含 operatorNickname join 带出）

### 章节管理模块

章节是文章子模块（无独立菜单页，从文章列表"章节"按钮跳二级路由页 /knowhub/article/chapters?articleId=）。章节≈博客，正文走主表不分表。章节提交状态机由文章 visibility + 提交者是否文章作者决定；章节作者审核（仅 SEMIPUBLIC）走 chapter_review_log，不受系统审核开关影响。

**权限键**：按钮 knowhub:chapter:动作 走 @PreAuthorize（挂文章菜单下作隐形 menu_type=3）；章节编辑/审核实际可见性由后端按章节可见性=文章可见性 + 提交者/作者归属判定。

#### `GET /chapter/list` — 获取章节列表（按 articleId 过滤）

**权限**：`knowhub:chapter:quarry`（需能看文章；章节可见性=文章可见性：能看文章即看 PUBLISHED 章节，非 PUBLISHED 仅章节作者/文章作者可见）

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| articleId | number | 所属文章ID（必传） |
| pageNum/pageSize | number | 分页 |
| chapterName | string | 章节名模糊（可选） |
| status | string | 状态（可选） |
| reviewStatus | string | 审核状态（可选） |
| authorId | number | 章节作者userId（可选） |
| beginTime/endTime | string | 创建时间区间 yyyy-MM-dd（可选） |

**响应**：`Result<PageInfo<ChapterVo>>`（列表不带 content 大字段，含 authorNickname/articleTitle/articleVisibility join 带出）

#### `GET /chapter/{chapterId}` — 获取章节详情（含正文 content）

**权限**：`knowhub:chapter:info`（二次校验章节可见性，回填 canEdit/canReview）

**响应**：`Result<ChapterVo>`（带 content 大字段 + join article 带出 articleTitle/articleVisibility/articleLevel）

#### `POST /chapter` — 新增/提交章节

**权限**：`knowhub:chapter:add`

**逻辑**：按 visibility+提交者是否作者决定状态机分支——作者提交任意 visibility 免审 PUBLISHED；非作者 PRIVATE 拒绝；非作者 SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW+写流水 SUBMIT；非作者 PUBLIC 免审 PUBLISHED。

**请求体**：`ChapterVo`（articleId/chapterName/content 必填，sortOrder 可选）

**响应**：`Result<Boolean>`

#### `PUT /chapter` — 编辑章节

**权限**：`knowhub:chapter:add`（编辑复用 add；canEdit 校验：章节作者 OR 文章作者 OR 系统编辑权限够）

**逻辑**：PUBLISHED 禁编须先撤回；PENDING_AUTHOR_REVIEW 审核中不能改。

**请求体**：`ChapterVo`（chapterId 必填）

**响应**：`Result<Boolean>`

#### `DELETE /chapter/{chapterIds}` — 批量删除章节

**权限**：`knowhub:chapter:delete`（章节作者/文章作者或 delete 权限可删）

**响应**：`Result<Boolean>`

#### `PUT /chapter/publish/{chapterId}` — 提交/发布章节

**权限**：`knowhub:chapter:publish`（DRAFT/REJECTED/REVOKED 再提交，按 visibility 决定走不走作者审）

**响应**：`Result<Boolean>`

#### `PUT /chapter/revoke/{chapterId}` — 撤回章节

**权限**：`knowhub:chapter:revoke`（仅 PUBLISHED 可撤回 → REVOKED）

**响应**：`Result<Boolean>`

#### `PUT /chapter/review` — 章节作者审核

**权限**：`knowhub:chapter:review`（仅 PENDING_AUTHOR_REVIEW 可审；审核人=文章作者 OR 系统审权限代审；章节提交者回避）

**请求体**：`ChapterReviewVo`（chapterId/pass/advice；pass=false 时 advice 必填）

**响应**：`Result<Boolean>`

#### `GET /chapter/review-log/{chapterId}` — 获取章节审核历史

**权限**：`knowhub:chapter:reviewLog`

**响应**：`Result<List<ChapterReviewLogVo>>`（按时间升序，仅 SEMIPUBLIC 场景有记录）

### 文章前台门户模块

文章=章节集合（文档站结构）。前台公开读走 `/portal/article/*`（permitAll，无 @PreAuthorize）；读/写物理隔离，点赞/收藏走 `/authoring/article/**`（authenticated 兜底）。前台过滤铁律：所有列表/详情 SQL 一律 `deleted=0 AND status='PUBLISHED' AND level<=userViewLevel`（分级推荐开关 `knowhub.portal.hierarchical.enabled` 默认关→恒 L1，L2/L3 永不下发；开→按登录用户 ArticlePermissionResolver.view 阶梯）。详情/章节正文越级锁态（不抛 403，返 `locked=true`+`lockReason`+正文置空，只给元数据/章节大纲）。推荐两段式：登录用户按 `article_collect∪article_like`+浏览过的文章 tag 反推偏好 tag 召回（collect×3+like×1+view×1）+ Service 层算分 `tag命中×5+收藏×3+点赞×2+浏览×1+时间衰减`；未登录/召回不足走全局热门兜底。搜索覆盖 文章标题/简介（ft_article_title_summary ngram）+ 章节正文（ft_chapter_content ngram，EXISTS 子查询命中章节），命中章节由 `matchedChapters` 标出（前端在卡上展示并支持跳章节阅读页）。标签热度榜复用既有 `/portal/tag/hot`（跨 blog_tag+article_tag，article 分支维度已与 blog 对齐 like×2+collect×3+view）。

#### `GET /portal/article/search` — 前台文章搜索

**权限**：无（permitAll）

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| keyword | string | 全文关键字（命中文章标题/简介或章节正文，ngram 全文索引，不需要登录） |
| tagIds | number[] | 标签 id 列表（多选，走 article_tag join + IN 精确过滤，同时命中全部） |
| authorId | number | 作者过滤（可选） |
| sort | string | RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 RELEVANCE |
| pageNum | number | 页码 |
| pageSize | number | 每页条数 |

**响应**：`Result<PageInfo<ArticlePortalVo>>`（ArticlePortalVo：articleId/authorId/authorNickname/title/summary/coverUrl/level/publishTime/viewCount/likeCount/collectCount/chapterCount/tagIds/tagNames/matchedChapters；matchedChapters 仅 search 接口填充，为命中章节 {chapterId,articleId,chapterName,sortOrder} 列表，每文章至多 3 个）

#### `GET /portal/article/recommend` — 前台文章个性化推荐 feed

**权限**：无（permitAll）；登录用户按偏好 tag 召回，未登录/无行为走全局热门兜底

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| size | number | 召回条数，默认 10 |
| excludeArticleId | number | 排除的文章ID（详情页相关推荐排除当前，feed 可空） |

**响应**：`Result<List<ArticlePortalVo>>`（不分页 feed，不带 matchedChapters）

#### `GET /portal/article/{articleId}` — 前台文章详情

**权限**：无（permitAll）；越级（level>userViewLevel）返 locked=true + lockReason，章节大纲仍下发（只含章节名不泄正文），不计浏览量；达权返 locked=false + chapterList（章节大纲）+ 登录态计章节级浏览量（user_view_history biz_type=ARTICLE）

**路径参数**：articleId

**响应**：`Result<ArticlePortalDetailVo>`（继承 ArticlePortalVo + chapterList:List<ChapterOutlineVo> + locked + lockReason）

#### `GET /portal/article/{articleId}/chapter/{chapterId}` — 前台章节正文

**权限**：无（permitAll）；越级返 locked=true + content=null；达权返章节 markdown 正文 + 登录态计章节浏览量（user_view_history biz_type=CHAPTER）

**路径参数**：articleId, chapterId

**响应**：`Result<ChapterContentVo>`（chapterId/articleId/chapterName/sortOrder/content/locked/lockReason）

#### `GET /portal/article/{articleId}/related` — 详情页相关推荐

**权限**：无（permitAll）

**路径参数**：articleId

**请求参数**：size number（默认 10）

**响应**：`Result<List<ArticlePortalVo>>`（同 tag 文章排除自身按热度+时间排序，无 tag 退化全局热门）

#### `PUT /authoring/article/{articleId}/collect` — 收藏/取消收藏文章

**权限**：`isAuthenticated()`（走 /authoring/** authenticated 兜底，无按钮权限键）

**路径参数**：articleId

**请求参数**：collected boolean（true 收藏, false 取消，缺省 true）

**响应**：`Result<Boolean>`（事务内 upsert article_collect + 主表 collect_count 同步 ±1）

#### `PUT /authoring/article/{articleId}/like` — 点赞/取消点赞文章

**权限**：`isAuthenticated()`

**路径参数**：articleId

**请求参数**：liked boolean（true 点赞, false 取消，缺省 true）

**响应**：`Result<Boolean>`（事务内 upsert article_like + 主表 like_count 同步 ±1）

#### `GET /authoring/article/collect/list` — 我的文章收藏列表

**权限**：`isAuthenticated()`

**请求参数**：pageNum number, pageSize number

**响应**：`Result<PageInfo<ArticlePortalVo>>`（按收藏时间倒序，仅前台可见口径的已发布文章）

### 文章创作模块

文章创作 = 写文章元信息（标题/前言 summary/等级/可见性/封面/标签），**正文不在文章主表**（正文走章节创作）。前台 `/authoring/article/**` 薄封装复用后台 ArticleService（addArticleInfo/editArticleInfo/publishArticle/revokeArticle/getArticleInfo/quarryArticle），读写物理隔离（走 /authoring/** authenticated 兜底，无按钮权限键，登录即可创作）。状态机同博客：新建即 DRAFT，发布按审核开关 `knowhub.article.review_enabled` 决定 PUBLISHED 或 PENDING_REVIEW；PUBLISHED 禁编须先撤回。改 level 升级非作者需自身 edit>=新 level。与「文章管理模块」后台接口的区别仅在路由前缀与权限门槛（前台无 @PreAuthorize 按钮键，authenticated 兜底 + service 层作者归属/状态机校验）。

| 方法 | 路径 | 说明 | 出参 |
|---|---|---|---|
| GET | `/authoring/article/level` | 当前用户文章 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选 | `Result<Integer>` |
| GET | `/authoring/article/list` | 我的文章列表（薄封装 quarryArticle，service 内回填 userId 走 author_id 分支） | `Result<PageInfo<ArticleVo>>`，query: ArticleQuarry |
| GET | `/authoring/article/{articleId}` | 文章编辑回填（复用 getArticleInfo，canOp 防越权，回填 tagIds/tagNames/canEdit/isAuthor） | `Result<ArticleVo>` |
| POST | `/authoring/article` | 新建文章草稿（复用 addArticleInfo，作者=current user，DRAFT；ArticleVo 体，title 必填，summary/level/visibility/coverObjectKey/tagIds 可选） | `Result<Boolean>` |
| PUT | `/authoring/article` | 编辑文章（复用 editArticleInfo；ArticleVo 体带 articleId；PUBLISHED 须先撤回；先删后插标签） | `Result<Boolean>` |
| PUT | `/authoring/article/{articleId}/publish` | 发布文章（复用 publishArticle，按审核开关决定 PUBLISHED/PENDING_REVIEW） | `Result<Boolean>` |
| PUT | `/authoring/article/{articleId}/revoke` | 撤回文章（复用 revokeArticle → REVOKED，仅 PUBLISHED 可撤回） | `Result<Boolean>` |

### 章节创作模块

章节是文章子页面，正文走章节主表；不分等级、可见性随文章、无标签无封面。前台 `/authoring/chapter/**` 薄封装复用后台 ChapterService（quarryChapter/getChapterInfo/submitChapter/editChapterInfo/publishChapter/revokeChapter/deleteChapterInfo/listReviewLog），读写物理隔离（走 /authoring/** authenticated）。章节提交状态机由文章 visibility + 提交者是否文章作者决定（参 ChapterServiceImpl）：作者提交任意 visibility 免审直 PUBLISHED；非作者 PRIVATE 拒、SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW 待作者审、PUBLIC 免审 PUBLISHED。PUBLISHED 禁编须先撤回。前台文章作者可在此走 reviewChapter 审核（仅 SEMIPUBLIC，本批未单独暴露审核接口，复用后台 `/chapter/review`，前台审核 UI 暂后置）。

| 方法 | 路径 | 说明 | 出参 |
|---|---|---|---|
| GET | `/authoring/chapter/list` | 某文章的章节列表（需 query.articleId；service 校验能看该文章） | `Result<PageInfo<ChapterVo>>`，query: ChapterQuarry |
| GET | `/authoring/chapter/{chapterId}` | 章节编辑回填（复用 getChapterInfo，章节可见性=文章可见性防越权，回填 canEdit/canReview） | `Result<ChapterVo>` |
| POST | `/authoring/chapter` | 提交新章节（复用 submitChapter，按 visibility 决定状态机；ChapterVo 体，仅 chapterName/content 必填，sortOrder 可选缺省 0） | `Result<Boolean>` |
| PUT | `/authoring/chapter` | 编辑章节（复用 editChapterInfo；ChapterVo 体带 chapterId；PUBLISHED 须先撤回） | `Result<Boolean>` |
| PUT | `/authoring/chapter/{chapterId}/publish` | 再次提交/发布章节（复用 publishChapter，用于 DRAFT/REJECTED/REVOKED 再提交） | `Result<Boolean>` |
| PUT | `/authoring/chapter/{chapterId}/revoke` | 撤回章节（复用 revokeChapter → REVOKED，仅 PUBLISHED 可撤回） | `Result<Boolean>` |
| PUT | `/authoring/chapter/reorder` | 批量重排章节顺序（长按拖拽持久化；body=`List<ChapterVo>` 每项 {chapterId, sortOrder, articleId}，逐章 canEdit 校验 + 事务，中途越权/不存在回滚） | `Result<Boolean>` |
| DELETE | `/authoring/chapter/{chapterIds}` | 删除章节（复用 deleteChapterInfo，章节作者 OR 文章作者 OR delete 权限；chapterIds 逗号分隔） | `Result<Boolean>` |
| GET | `/authoring/chapter/review-log/{chapterId}` | 章节审核历史（复用 listReviewLog，仅 SEMIPUBLIC 场景有记录） | `Result<List<ChapterReviewLogVo>>` |

### 资源前台门户模块

资源推荐前台门户。读走 `/portal/resource/*`（permitAll，无 @PreAuthorize），写走 `/authoring/resource/**`（authenticated 兜底），读写物理隔离。资源模块差异点（与博客/文章门户对照）：**无 level 等级、无 visibility、无 review_status 前台过滤、无标签体系**——前台铁律仅 `status='PUBLISHED' AND deleted=0`，非 PUBLISHED 资源前台根本不下发（详情查不到返业务码 404）；无 userViewLevel 透传、无越级锁态、无分级推荐开关。推荐 feed 退化为**全局热门兜底**（资源无 tag、无用户偏好源，按 `download_count*3+view_count+like_count*2+collect_count*2` 排序）；"相关推荐"= 同 `resource_category_id` 其它公开资源按热度排（-1=其他类时退化全局热门）。搜索覆盖 `resource(title,summary,description)` 的 FULLTEXT ngram 索引（`ft_resource_title_summary_desc`，`ngram_token_size=2`，同博客口径）。分类筛选用 `resource_category_id` 分类树（`ResourceCategoryService.categoryTree()`，-1=其他前端硬编码），类型筛选 FILE/LINK 辅助维度。侧栏"热门下载榜/最近上传榜"不单独建接口，前端直接复用 search 带 sort=HOT/LATEST + pageSize。

#### `GET /portal/resource/search` — 前台资源搜索

**权限**：无（permitAll）

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| keyword | string | 全文关键字（命中 title/summary/description，ngram 全文索引，BOOLEAN MODE） |
| resourceType | string | FILE / LINK（可选，精确过滤） |
| resourceCategoryId | number | 分类 id（可选，单选精确过滤；-1=其他）。向后兼容字段，多选 `resourceCategoryIds` 优先 |
| resourceCategoryIds | string | 分类 id 多选过滤，逗号分隔串（`?resourceCategoryIds=1,2,3`，-1=其他 作为合法元素参与 IN）。Spring MVC 顺序绑定 + String→List\<Long\> 切分转 Long。不传/空不过滤。前端 paramsSerializer 把数组 join 成逗号串（默认 axios 数组序列化成 `xx[]=1&xx[]=2` Spring POJO 字段不识别末尾 `[]` 会拿空列表） |
| sort | string | RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 HOT。RELEVANCE 有 keyword 走 NATURAL LANGUAGE MODE 相关度，无 keyword 退化为 publish_time desc |
| pageNum | number | 页码 |
| pageSize | number | 每页条数 |

**响应**：`Result<PageInfo<ResourcePortalVo>>`（ResourcePortalVo：resourceId/authorId/authorNickname/resourceType/resourceCategoryId/categoryName/title/summary/linkUrl/linkIcon/fileObjectId/originalName/contentLength/contentType/publishTime/viewCount/downloadCount/likeCount/collectCount/ratingAvg/ratingCount；**列表不 select description 大字段**；like_count/collect_count 主表无冗余列，列表用 inline 子查询回填）

#### `GET /portal/resource/recommend` — 前台资源推荐 feed

**权限**：无（permitAll）；全局热门兜底，无用户偏好源

**请求参数**（query string）：

| 参数 | 类型 | 说明 |
|---|---|---|
| size | number | 召回条数，默认 10 |
| excludeResourceId | number | 排除的资源ID（详情页相关推荐排除当前，feed 可空） |

**响应**：`Result<List<ResourcePortalVo>>`（不分页 feed，按热度排序）

#### `GET /portal/resource/{resourceId}` — 前台资源详情

**权限**：无（permitAll）；登录态计浏览量（`user_view_history` biz_type=RESOURCE，未登录不计）；回填登录态互动态 hasLiked/hasCollected/myScore。详情**不下发** FILE 下载链接（避免 permitAll 区触发 fileService 鉴权）——FILE 下载链接由前端点"下载资源"按钮时调 `/authoring/resource/{id}/download`（isAuthenticated 兜底）现取，`downloadResource` 内调 `getDownloadUrl(id, true)` 跳过 owner 闸（资源层已校验 PUBLISHED），且带 `download_count+1` 业务语义。

**路径参数**：resourceId

**响应**：`Result<ResourcePortalDetailVo>`（继承 ResourcePortalVo + description + hasLiked/hasCollected/myScore；**无 downloadUrl 字段**）；非 PUBLISHED 或不存在返 `Result.error(404,"资源不存在或已下架")`

#### `GET /portal/resource/{resourceId}/related` — 详情页相关推荐

**权限**：无（permitAll）

**路径参数**：resourceId

**请求参数**：size number（默认 10）

**响应**：`Result<List<ResourcePortalVo>>`（同 `resource_category_id` 公开资源排除自身按热度排，-1=其他类时退化全局热门）

#### `GET /portal/resource/category/tree` — 资源分类树

**权限**：无（permitAll）；复用 `ResourceCategoryService.categoryTree()`，供前台列表分类筛选 + 上传表单分类选择

**响应**：`Result<List<ResourceCategoryTreeVo>>`（categoryId/categoryName/children 递归树）

### 资源创作与互动模块

资源创作 = 上传文件资源（FILE，走 `presignedUploadFlow` 直传 `RESOURCE_FILE`/PRIVATE）或登记链接资源（LINK，linkUrl+linkIcon）。前台 `/authoring/resource/**` 薄封装复用后台 `ResourceService`（add/edit/publish/revoke/getResourceInfo/quarryResource/toggleLike/toggleCollect/rateResource/downloadResource），读写物理隔离（走 /authoring/** authenticated 兜底，无按钮权限键，登录即可创作 + 互动任意已发布资源）。状态机：新建即 DRAFT，发布按审核开关 `knowhub.resource.review_enabled` 决定 PUBLISHED 或 PENDING_REVIEW；PUBLISHED/PENDING_REVIEW 禁编辑须先撤回（已发布换源须先 revoke 再 edit，后端已挡，前端按 status 隐藏换文件入口）。与「资源管理模块」后台接口的区别仅在路由前缀与权限门槛。点赞/收藏/评分走事实表（`resource_like`/`resource_collect`/`resource_rating`，PK resource_id+user_id），upsert 写入、计数读时聚合（资源主表仅冗余 view_count/download_count，不冗余互动计数）。

| 方法 | 路径 | 说明 | 出参 |
|---|---|---|---|
| GET | `/authoring/resource/list` | 我的资源列表（薄封装 listMyResources，service 内硬置 authorId=当前用户，返回本人全态含草稿/待审/驳回） | `Result<PageInfo<ResourceVo>>`，query: ResourceQuarry |
| GET | `/authoring/resource/{resourceId}` | 资源编辑回填（薄封装 getResourceForAuthor，归属校验拒非作者，回填互动+下载链接） | `Result<ResourceVo>` |
| POST | `/authoring/resource` | 新建资源草稿（复用 addResourceInfo，作者=current user，DRAFT；ResourceVo 体，resourceType/title 必填，FILE 传 fileObjectId、LINK 传 linkUrl/linkIcon，summary/description/resourceCategoryId 可选） | `Result<Boolean>` |
| PUT | `/authoring/resource` | 编辑资源（复用 editResourceInfo，校验归属+状态机；PUBLISHED/PENDING_REVIEW 须先撤回；ResourceVo 体带 resourceId） | `Result<Boolean>` |
| PUT | `/authoring/resource/{resourceId}/publish` | 发布资源（复用 publishResource，按审核开关决定 PUBLISHED/PENDING_REVIEW） | `Result<Boolean>` |
| PUT | `/authoring/resource/{resourceId}/revoke` | 撤回资源（复用 revokeResource → REVOKED，撤回后可再编辑/换源/再发布） | `Result<Boolean>` |
| GET | `/authoring/resource/{resourceId}/download` | FILE 资源下载链接下发（复用 downloadResource，校验 PUBLISHED+FILE，下载量 +1，返回下载 url 字符串） | `Result<String>` |
| PUT | `/authoring/resource/{resourceId}/like` | 点赞/取消点赞（liked=true/false 缺省 true，事实表 upsert） | `Result<Boolean>` |
| PUT | `/authoring/resource/{resourceId}/collect` | 收藏/取消收藏（collected=true/false 缺省 true，事实表 upsert） | `Result<Boolean>` |
| PUT | `/authoring/resource/{resourceId}/rating` | 资源评分 1-5（一人一资源一条，upsert 事实表，均值读时聚合） | `Result<Boolean>`，query: score |
| GET | `/authoring/resource/collect/list` | 我的资源收藏列表（按收藏时间倒序，仅返回前台可见口径的已发布资源） | `Result<PageInfo<ResourcePortalVo>>`，query: pageNum/pageSize |

### 接口更新日志

#### 2026-08-08 资源推荐前台门户 + 创作接口新增（15 接口）

资源管理模块后台 CRUD/审核/互动早落地（2026-07-06），但前台 portal/authoring 接口全为零（knowhub-ui 资源推荐页 100% mock）。本次补齐资源前台：搜索/推荐/详情/相关推荐/分类树（5 读 permitAll）+ 创作/下载/互动/收藏列表（10 写走 authenticated）。详见上方「资源前台门户模块」「资源创作与互动模块」章节。

- **新增接口（15）**：`GET /portal/resource/search|recommend|{resourceId}|{resourceId}/related|category/tree`（5 读公开）+ `/authoring/resource/` 创作侧 11 写登录（list/{resourceId}/POST/PUT/{resourceId}/publish|revoke|download|like|collect|rating|collect/list）。读写物理隔离（/portal/** permitAll、/authoring/** authenticated）。
- **新增后端 service / mapper**：`ResourcePortalService(Impl)`（search/recommend/getDetail/related/listMyCollected）+ `ResourcePortalMapper(.xml)`（5 套 SQL 全带 `deleted=0 AND status='PUBLISHED'` 前台铁律，search 用 FULLTEXT ngram + inline 子查询回填 like/collect 计数）；`ResourceService` 新增 `listMyResources`/`getResourceForAuthor` 两个薄方法（service 内硬置 authorId + checkOwnerOrAdmin 归属校验），`ResourcePortalService` 新增 `listMyCollected`（照 ArticlePortalService.listMyCollected 范式，recommendHot 交集 + 按收藏时间倒排）。新增 VO `ResourcePortalVo`/`ResourcePortalDetailVo`、入参 `ResourcePortalSearchQuarry`。
- **搜索 = FULLTEXT ngram 索引**（同博客口径）：`sql/knowhub-resource-portal.sql`（幂等 information_schema ALTER）给 `resource(title,summary,description)` 加 `ft_resource_title_summary_desc ... WITH PARSER ngram`；需 MySQL `ngram_token_size=2`。无新菜单/无新字典/无新 sys_config（前台读接口不加开关，对齐博客决策#7）。
- **推荐 = 全局热门兜底 + 同分类相关**：资源无 tag、无用户偏好源（与博客/文章差异点），推荐 feed 退化为热度榜口径（`download_count*3+view_count+like_count*2+collect_count*2`）；详情页"相关推荐"= 同 `resource_category_id` 其它公开资源按热度排（-1=其他类退化全局热门）。
- **资源无 level 等级 / 无越级锁态 / 无分级推荐开关**：与博客/文章门户差异点——前台过滤仅 `status='PUBLISHED' AND deleted=0`，详情查非 PUBLISHED 直接返 404；无 userViewLevel 透传、无 locked 锁态。
- **分类筛选 = resource_category_id 分类树**：放弃 mock 的 WEBSITE/SOFTWARE/SCRIPT/DOCUMENT/TOOL 枚举；前台分类下拉用 `ResourceCategoryService.categoryTree()` 取树，-1=其他前端硬编码追加；resourceType（FILE/LINK）作辅助筛选维度。
- **上传 = 前端直传 presignedUploadFlow（RESOURCE_FILE/PRIVATE）+ 后端两模式自适应**：前端不写死 OSS 地址，复用 `knowhub-ui/src/utils/upload.ts`；上传表单支持保存草稿 + 发布两个按钮；已发布资源不允许换源/换文件——`ResourceServiceImpl.editResourceInfo` 现有状态机已挡（PUBLISHED/PENDING_REVIEW 禁编辑），前端据此隐藏换文件入口（提示"已发布资源请先撤回再换源"）。description 正文配图复用 KhMarkdownEditor（businessType=BLOG_BODY 过渡，与章节正文同口径，后端暂无 RESOURCE_BODY 枚举）。
- **前端**：knowhub-ui 新增 `types/api/knowhub/resource.ts`+`resource-authoring.ts`、`api/knowhub/resource-portal.ts`+`resource-authoring.ts`；`views/resources/index.vue`（搜索+分类树筛选+FILE/LINK 类型筛选+HOT/LATEST 排序+分页，侧栏热门/最近上传榜复用 search）、`views/resource/detail.vue`（详情+登录态点赞/收藏/评分+FILE 下载/LINK 访问+相关推荐）、`views/resource/upload.vue`（上传表单：FILE 直传RESOURCE_FILE/PRIVATE 进度条+checkFileAllowed 预检 / LINK 登记链接 / 分类 cascader / 草稿+发布双按钮 / 编辑回填 / 已发布禁换源）；`components/resource/ResourceCard.vue`（MockResource→ResourcePortalRecord，封面色/图标按 resourceType 派生）；`views/profile/index.vue`（我的资源 tab 真接口 + "上传资源"创建入口）、`views/home/index.vue`（资源推荐网格+热门资源榜改真接口，删 mock/resource import）、`components/layout/AppHeader.vue`（用户下拉加"上传资源"）；路由 `/resource/upload`（requiresAuth，排在 `/resource/:id` 之前防 :id 吃掉 upload）；删除 `src/mock/resource.ts`。
- 校验：knowhub-ui npm run type-check 通过（无资源模块错误，余 6 处为项目模块既有 ViewLevel/accent/DefaultRow 问题，非本次范围）；后端 mvn 编译由用户在已装依赖环境验证。

#### 2026-08-04 章节批量重排接口 + 目录树升级（章节拖拽持久化 + TOC 全层级可滚动）

- 新增 `PUT /authoring/chapter/reorder`（前台章节管理页长按拖拽重排持久化）：body 为 `List<ChapterVo>`，每项 `{chapterId, sortOrder=新 index, articleId}`。
  - 新增 `ChapterMapper.updateSortOrder(chapterId, sortOrder, updateBy)` + XML（专用单列 update，不复用 editChapterInfo 的动态列单条更新）。
  - `ChapterService.reorderChapters` `@Transactional`：校验非空 + 基准 articleId + 全部同 articleId（防跨文章串改）+ 逐章 `canEditChapter`（章节作者 OR 文章作者 OR 系统编辑权限够），任一越权/不存在抛 `ServiceException` 回滚。
  - 前端 `reorderChaptersApi` + `ChapterReorderPayload` 类型；`chapters.vue` HTML5 dnd（draggable + dragstart/dragover/drop），仅 `canEditArticle`（DRAFT/REJECTED/REVOKED/空态）启用拖拽，本地先交换 UI 即时响应，失败重拉回滚。
- `KhContentToc` 升级：`items: string[]` → `items: TocItem[] {level, text}`（level=2/3/4 对齐 ##/###/####），按 level 缩进渲染成 CSDN 风格目录树（h3/h4 字号变小、色变浅）；列表 `max-height: calc(100vh - header - 140px) + overflow-y:auto` 防长正文目录超出卡片。
  - `doc/read.vue` `chapterToc` 与 `blog/detail.vue` `toc` 同步改为正则 `^(#{2,4})\s+(.+)$` 提取全层级；`handleTocSelect` 的 `querySelectorAll` 扩到 `h2,h3,h4`；doc/read h4 补 `scroll-margin-top`。
  - 博客侧栏 `.bd__aside` 补 `max-height + overflow-y:auto`（sticky 列防目录+推荐超视口）。
- **2026-08-04 续**：KhContentToc 由扁平缩进改为**可折叠嵌套树**（buildTree 按 level 嵌套，三角折叠/合并，默认全展开），颜色统一不分层级深浅；卡片加宽（blog 侧栏 280→320px / doc 右栏 220→260px）。章节拖拽放开到 `article.canEdit`（作者即可拖，PUBLISHED 也允许），后端 reorderChapters 逐章 canEditChapter 鉴权不变。

#### 2026-07-31 博客点赞收藏接口迁前台（后台用不到，挪 /authoring/blog/** 与文章范式对齐）

- 后台 `controller/admin/BlogController` 此前含 `PUT /blog/like/{blogId}`、`PUT /blog/collect/{blogId}`（无 `@PreAuthorize`，后台管理页用不到），本次挪到前台 `controller/portal/BlogAuthoringController`（/authoring/blog/** authenticated 兜底），改为：
  - `PUT /authoring/blog/{blogId}/like?liked=true|false`（缺省 true）
  - `PUT /authoring/blog/{blogId}/collect?collected=true|false`（缺省 true）
- 复用同一 `blogService.toggleLike/toggleCollect`，业务不变（`blog_like`/`blog_collect` 明细 + 主表 `like_count`/`collect_count` 同步）。
- 后台 BlogController 删两条接口 + 清理未用的 `RequestParam` import；Tag 描述同步去掉“点赞收藏”。
- 与文章 `/authoring/article/{id}/like|collect` 范式对齐（响应 query 参数风格统一为缺省 true）。
- 前端无影响：前台博客 detail.vue 点赞收藏尚未真正接入（仅占位注释），authoring.ts 未引用旧 `/blog/like|collect`。

#### 2026-07-07 项目管理模块新增（19 接口）

新增项目管理模块全部接口：项目 CRUD 5 个（list/info/add/edit/delete）+ 审核 4 个（publish/revoke/review/review-log）+ 成员管理 4 个（list/add/edit/delete）+ 文件树管理 7 个（tree/list/folder/node/edit/delete/download），共 19 个接口。详见上方「项目管理模块」章节。等级权限(view/download/edit:l13)由后端 ProjectPermissionResolver 取最高等级判定，非框架 hasAuthority；审核流程复用 ReviewAction 枚举 + review_action 字典；文件复用 file_object（PROJECT_SRC/PKG/DOC）+ project_file 树表支撑 GitHub 式侧边栏。

#### 2026-07-08 项目管理模块修订（类型补齐 + 批量加成员 + 弹窗职责分离）

- **项目类型字典补齐**：`sql/knowhub-project-patch.sql` 追加 project_type 的 PRACTICE/OPS（dict_data 121/122），原 knowhub-project.sql 不改、表不动；ProjectType 枚举启用三值。PRACTICE/OPS 无子表（主表 description/summary + 项目文件覆盖所需属性），比赛子表 project_competition 保留。
- **批量加成员接口**：新增 `POST /project/member/batch/{projectId}`，body 为 userIds 数组，默认 MEMBER 角色，已存在跳过（参考通知分组 UX，前端搜用户子弹窗调 GET /sys/user/list 搜索后逐个"加入"）。单点改角色/权限标志位仍走 PUT /project/member。
- **弹窗职责分离**（用户明确要求）：新增/编辑弹窗内嵌"项目信息/团队成员/项目文件"三标签页，改数据全在编辑弹窗；详情弹窗只读（仅展示+下载，下载属查看行为）；审核弹窗只给通过/驳回+意见，不展示其它数据、不承担改数据职责。前端新增 ProjectEditDialog/ProjectMemberAddDialog 组件，index.vue 列表改用独立编辑弹窗（SharedTablePanel 仅展示表格）。
- 校验：mvn -pl knowhub -am compile BUILD SUCCESS；rookie-ui npm run type-check 通过。

#### 2026-07-09 文章管理模块新增（17 接口）

新增文章管理模块全部接口：文章 9 个（list/info/add/edit/delete/publish/revoke/review/review-log）+ 章节 9 个（list/info/submit-add/edit/delete/publish/revoke/review/review-log），共 18 个接口。详见上方「文章管理模块」「章节管理模块」章节。

- **权限**：文章等级权限(view/edit:l1-l3)由后端 ArticlePermissionResolver 扫 perms 取最高等级判定，非框架 hasAuthority；无成员表（轻量：系统级+作者归属，作者全权不看等级/不看 visibility）；无 download（文章无下载）。
- **双重审核流**：文章系统审核照搬博客范式（状态机+回避+流水表+对账任务，开关走 sys_config knowhub.article.review_enabled 默认 true）；章节作者审核仅 SEMIPUBLIC 触发（走 chapter_review_log，不受系统审核开关影响，是 visibility tier 固有机制）。
- **章节提交状态机**：作者提交任意 visibility 免审 PUBLISHED；非作者 PRIVATE 拒绝 / SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW 待作者审 / PUBLIC 免审 PUBLISHED。
- **主表不冗余审核快照**（reviewer/review_time/review_advice 全在流水表），照项目范式（比博客主表更干净）；章节正文不分表（用户拍板，整页 Markdown 列表不带 content 即可）。
- **关联**：project.article_id 单向关联文章，文章侧不反查、不加 project_id。
- 校验：mvn -pl knowhub -am compile BUILD SUCCESS；rookie-ui npm run type-check 通过（修一处 ChapterEditDialog import 路径）。

#### 2026-07-09 博客管理加 L1~L3 等级查询权限（对齐文章模块范式）

博客原只有按钮权限，无等级概念。本次照文章模块范式加等级：blog 表加 `level`(1/2/3)，列表按 `level<=userViewLevel OR author_id=userId` 过滤，操作按 `canOp=userLvl(op)>=level OR author_id==userId` 判定（作者全权不看等级）。review 保持按钮权限（非等级）。

- **新增接口**：无（沿用博客 9 个接口，仅入参/出参加 level + 详情回填权限态；BlogListQuery 加 level 筛选参数；BlogVo 出参加 level + canView/canEdit/isAuthor）。
- **权限**：新增系统等级权限 `knowhub:blog:view:l1/l2/l3` + `knowhub:blog:edit:l1/l2/l3`（menu 159-164），由后端 BlogPermissionResolver 扫 perms 取最高等级判定（非框架 hasAuthority）；无成员表（轻量：系统级+作者归属，作者全权不看等级）；review 保持 knowhub:blog:review 按钮权限（非等级）；编辑仍保留既有 knowhub:blog:edit 独立键（博客历史存量，渐进增强）。
- **SQL**：`sql/knowhub-blog-level-patch.sql`：blog 加 level tinyint NOT NULL DEFAULT 1（存量 L1）+ idx_blog_level；menu 159-164（6 等级权限）；dict blog_level(34)+dict_data 140-142。续编前查 DB MAX(menu_id)=158/MAX(dict_id)=33/MAX(dict_data_id)=139。
- **缓存**：getBlogInfo 缓存命中与未命中两路都调 fillPermissionState 按当前登录用户实时重算 canView/canEdit/isAuthor（不信任缓存里的权限态字段，避免跨用户串态）。
- **修隐坑**：旧 checkOwnerOrAdmin 用 `List<Permission>.contains(String)` 永远 false（List 存的是 Permission 对象非 String），改遍历比 permKey。
- 校验：mvn -pl knowhub -am compile BUILD SUCCESS；rookie-ui npm run type-check 通过。

#### 2026-07-29 文章前台门户模块新增（8 接口）+ 标签热度榜文章分支维度升级

文章模块后台 CRUD/审核早落地（2026-07-09），但前台 portal 接口完全为零（knowhub-ui 文档学习页 100% mock）。本次照博客前台门户范式（2026-07-15）补齐文章前台：搜索/推荐/详情/章节正文/相关推荐（5 读 + permitAll）+ 收藏/点赞 toggle + 我的收藏列表（3 写走 authenticated）。详见上方「文章前台门户模块」章节。

- **新增接口（8）**：`GET /portal/article/search|recommend|{articleId}|{articleId}/chapter/{chapterId}|{articleId}/related`（5 读公开）+ `PUT /authoring/article/{articleId}/collect|like`（2 写登录）+ `GET /authoring/article/collect/list`（1 写登录）。读写物理隔离（/portal/** permitAll、/authoring/** authenticated）。
- **推荐多维打分**：登录用户 `article_collect∪article_like`+浏览过的文章 tag 反推偏好 tag（collect×3+like×1+view×1）召回同 tag 文章，Service 层算 `tag命中×5+收藏×3+点赞×2+浏览×1+时间衰减(每30天−1)`；未登录/无行为/召回不足走全局热门兜底。
- **章节内容搜索命中标出**：`chapter.content` 建 FULLTEXT ngram 索引，搜索走 `MATCH(title,summary) OR EXISTS(MATCH(chapter.content))`，当前页文章回填 `matchedChapters`（命中章节名，前端在卡片标"命中章节：x章"可跳章节阅读页）。
- **分级推荐开关 + 越级锁态复用博客**：开关 `knowhub.portal.hierarchical.enabled` 默认关→恒 L1（二元闸，L2/L3 永不下发）；开→阶梯按登录用户 view 等级。详情/章节正文越级不抛 403，返 `locked=true`+`lockReason`+正文置空，只给元数据/章节大纲，不计浏览量。
- **标签热度榜文章分支维度升级**：`/portal/tag/hot` 的 article 分支由原 `sum(view_count)` 升级为 `sum(like_count*2+collect_count*3+view_count)`，与 blog 分支同维度，跨内容可比（文章本次新增 article_like/article_collect 后维度对齐，文章分支维持 `visibility='PUBLIC'` 过滤防 L2/L3 泄公网）。
- **文章新增收藏/点赞能力**：新建 `article_collect`+`article_like`（照 blog_collect/blog_like，PK article_id+user_id）+ article 主表冗余 `like_count`/`collect_count` 列。
- **底座映射修复**（顺手正向收益）：ArticleMapper.xml/ChapterMapper.xml 的 resultMap 与 SELECT 补 `view_count` 映射（原列已加+被浏览历史递增但 VO 恒 null 的 bug），后台同受益；ArticleVo/Article 实体补 likeCount/collectCount。
- **SQL**：`sql/knowhub-article-portal.sql`（幂等 information_schema）：article_collect/article_like 建表 + article 加 like_count/collect_count 列+索引 + article/chapter 加 FULLTEXT ngram 索引。无新菜单/无新字典/无新 sys_config。
- **前端**：knowhub-ui 新增 `api/knowhub/article.ts`+`types/api/knowhub/article.ts`；`views/docs/index.vue`（搜索+标签云复用 /portal/tag/hot + 推荐侧栏+标签排行+排序+分页）、`views/doc/detail.vue`（详情+章节大纲+越级锁态置灰"开始阅读"）、`views/doc/read.vue`（章节正文+左右目录+越级锁态提示+上下章导航）、`components/doc/DocCard.vue`（MockDoc→ArticlePortalRecord，去 mock 耦合，标命中章节）；路由 `/docs/:id/read` → `/docs/:id/read/:chapterId`。
- 校验：mvn -pl knowhub -am compile BUILD SUCCESS；knowhub-ui npm run type-check 通过。

#### 2026-07-30 文章/章节创作接口新增（薄封装复用后台 service，15 接口）

文档学习前台门户（2026-07-29）已补文章公开读 /portal/article/*。本次补齐前台**创作侧**：文章 + 章节创作接后端，照博客创作 /authoring/blog/** 范式。详见上方「文章创作模块」「章节创作模块」章节。

- **新增接口（15）**：
  - 文章创作 7：`/authoring/article/level|list|{articleId}|POST(新建)|PUT(编辑)|{articleId}/publish|{articleId}/revoke`，全部 `@PreAuthorize("isAuthenticated()")`，薄封装复用 ArticleService（addArticleInfo/editArticleInfo/publishArticle/revokeArticle/getArticleInfo/quarryArticle）。
  - 章节创作 8：`/authoring/chapter/list|{chapterId}|POST(提交)|PUT(编辑)|{chapterId}/publish|{chapterId}/revoke|DELETE {chapterIds}|/review-log/{chapterId}`，薄封装复用 ChapterService（quarryChapter/getChapterInfo/submitChapter/editChapterInfo/publishChapter/revokeChapter/deleteChapterInfo/listReviewLog）。
- **读写物理隔离**：读走 /portal/** permitAll、写走 /authoring/** authenticated 兜底（前已存）。无新 sys_config/菜单/字典。
- **文章创作表单**：标题/前言 summary(v-md-editor 可选)/等级(L1-3 按钮组按 view 等级禁)/可见性(PRIVATE/SEMIPUBLIC/PUBLIC 按钮组)/封面(ARTICLE_COVER 上传)/标签(多选)；**文章主表不存正文**——正文写在章节里。
- **章节创作表单精简**（用户拍板"不要太多杂项"）：仅章节名 + 排序 sortOrder + 正文 markdown；**无**封面/标签/等级/可见性（可见性随文章，等级不分）。
- **修复上一轮封面 SQL bug**：ArticlePortalMapper.xml 的 coverUrl 由 file_object biz_ref_id join 改为直读 `a.cover_object_key` 列（对齐后台 ArticleCoverUploader 既有口径——上传后存 /file/resolve/{objectId} 字符串到 cover_object_key 列，未绑 file_object biz_ref_id）。
- **章节正文配图**：预签名直传 businessType 暂复用 `BLOG_BODY`（PUBLIC 公开读语义同 Markdown 正文插图），后端 FileBusinessType 枚举暂无 CHAPTER_BODY，将来需区分时再补（属正当后续，已在 chapter-edit.vue 注释标记）。
- **前端**：knowhub-ui 新增 `types/api/knowhub/article-authoring.ts`+`api/knowhub/article-authoring.ts`；`views/article/create.vue`（文章创作页，照 blog/create.vue 范式，正文区改"文章前言"v-md-editor+元信息折叠含可见性按钮组+"章节管理"跳转按钮）；`views/article/chapters.vue`（章节管理页 /article/:id/chapters，列表+状态徽标+编辑/发布/撤回/删除/新增章节）；`views/article/chapter-edit.vue`（章节创作/编辑页 /article/:id/chapter/edit?cid=，名+排序+正文精简表单）；`components/article/ArticleCoverUploader.vue`（照 BlogCoverUploader 换 ARTICLE_COVER）；路由 3 条 `/article/create`、`/article/:id/chapters`、`/article/:id/chapter/edit`（均 requiresAuth）；profile「我的文章」tab 接真接口（getMyArticlesApi）+「新建」按钮 +「创作文章」下拉项 to。
- 校验：mvn -pl knowhub -am compile BUILD SUCCESS；knowhub-ui npm run type-check 通过。
