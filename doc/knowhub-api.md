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

- 文章：`GET /blog/list`、`GET /blog/{blogId}`、`POST /blog`、`PUT /blog`、`DELETE /blog/{blogIds}`、`PUT /blog/publish/{blogId}`、`PUT /blog/revoke/{blogId}`、`PUT /blog/review`、`PUT /blog/like/{blogId}`、`PUT /blog/collect/{blogId}`
- 标签：`GET /tag/list`、`GET /tag/{tagId}`、`POST /tag`、`PUT /tag`、`DELETE /tag/{tagIds}`

说明：发布接口受全局审核开关 `blog_review_enabled`（字典）控制，开关开则发布进入 `PENDING_REVIEW` 待审、由 `PUT /blog/review` 通过/驳回；开关关则直接 `PUBLISHED`。点赞/收藏接口仅要求登录，未挂 `@PreAuthorize`。详见下方「博客模块」章节。

### 2026-07-01 文件存储模块落地（预签名直传 + PUBLIC 回显 + PRIVATE 下载 + 对象 GC）

本次随 knowhub 文件存储底座落地，新增文件对象管理接口共 8 个：

- 上传令牌/确认：`POST /file/upload-token`、`POST /file/confirm/{objectId}`
- 回显/下载：`GET /file/public/{objectId}`（302 重定向，无鉴权）、`GET /file/download/{objectId}`
- 管理：`GET /file/list`、`GET /file/{objectId}`、`PUT /file/bind`、`DELETE /file/{objectIds}`

说明：上传走预签名直传——后端校验 contentType/size 后下发 `PutObject` 预签名 URL，前端直传 RustFS，后端不经流文件字节；`confirm` 用 `HeadObject` 核对真实值后置 `CONFIRMED`。PUBLIC 对象走 `/file/public/{id}` 302 重定向到 RustFS（供 Markdown `<img>` 直接引用，靠 vite proxy / nginx 转发 `/file`）；PRIVATE 对象走 `/file/download/{id}` 鉴权后下发短期 GET 预签名（带 `attachment;filename` 强制下载）。超时 PENDING 与软删对象由 `FileGcTask` 定时 GC。详见下方「文件存储模块」章节。

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

> 新建即草稿（status=DRAFT，reviewStatus=NONE）。

#### 4. 编辑博客文章

**基本信息：** `PUT /blog`　权限：`knowhub:blog:edit`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`BlogVo`）：** 同"添加"，`blogId` 必填；标签先删后插重建。

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 仅作者本人或具备 `knowhub:blog:review` 权限者可编辑他人文章。

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

> 受审核开关 `blog_review_enabled` 控制：开关关→直接 `PUBLISHED` 并写 publish_time；开关开→`PENDING_REVIEW` + review_status=PENDING，待 `PUT /blog/review` 处理。

#### 7. 撤回博客文章

**基本信息：** `PUT /blog/revoke/{blogId}`　权限：`knowhub:blog:revoke`　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 状态置 `REVOKED`；可经编辑回 DRAFT 后再发布。

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

> 通过：status=PUBLISHED、publish_time=now、review_status=APPROVED、reviewer/review_time 写入。
> 驳回：status=REJECTED、review_status=REJECTED、review_advice 写入。

#### 9. 点赞 / 取消点赞

**基本信息：** `PUT /blog/like/{blogId}?liked={true|false}`　权限：登录即可　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`liked` query 参数）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> `liked=true` 插入 `blog_like` 明细并 `like_count+1`；`false` 删除明细并 `-1`。幂等。

#### 10. 收藏 / 取消收藏

**基本信息：** `PUT /blog/collect/{blogId}?collected={true|false}`　权限：登录即可　日志：`@Log(博客文章, UPDATE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`collected` query 参数）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 与点赞对称，操作 `blog_collect` 与 `collect_count`。

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
> 鉴权：写/下载/管理类接口挂 `@PreAuthorize('knowhub:file:*')`，对应 `sys_menu` 中权限键；PUBLIC 回显接口 `GET /file/public/{id}` **无鉴权**（供 `<img>`/`<a>` 直接引用）。
> 预签名直传：上传不经后端字节流——`POST /file/upload-token` 签发 `PutObject` 预签名 URL，前端直传 RustFS，`POST /file/confirm/{id}` 用 `HeadObject` 核对。
> 前端路径：`<img src="/file/public/123">` 是相对路径，不走 axios，靠 vite proxy / nginx 转发 `/file` 到后端（已在 `rookie-ui/vite.config.ts` 加 `/file` 代理）。

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

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "uploadUrl": "https://rustfs-host:9000/knowhub/blog_body/2026/07/01/uuid.png?X-Amz-Signature=...",
    "objectKey": "blog_body/2026/07/01/uuid.png",
    "objectId": 1,
    "expires": 600
  }
}
```

> 后端 insert `file_object(upload_status=PENDING)` 后签发；前端拿 `uploadUrl` 直接 `PUT` 直传 RustFS（带 `Content-Type`/`Content-Length` header），传完调 `confirm`。

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

**基本信息：** `GET /file/public/{objectId}`　权限：**无**（公开）

**请求头：** 无

**请求体：** 无（`objectId` 路径参数）

**响应示例：** HTTP `302 Found`，响应头 `Location: https://rustfs-host:9000/knowhub/blog_body/.../x.png`，无响应体。

> 浏览器/`<img>` 自动跟随 302 去 RustFS 拉图渲染。接口 dumb 不挑类型——"渲染还是下载"取决于前端引用方式：`<img>` 渲染图片、`<a>` 点击下载。压缩包按设计应走 PRIVATE 的 `/file/download/{id}`。

#### 4. 获取下载预签名 URL

**基本信息：** `GET /file/download/{objectId}`　权限：`knowhub:file:download`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectId` 路径参数）

**响应示例：**
```json
{
  "code": 200, "msg": "请求成功",
  "data": {
    "downloadUrl": "https://rustfs-host:9000/knowhub/.../x.zip?X-Amz-Signature=...&response-content-disposition=attachment%3Bfilename%3D%22x.zip%22",
    "expires": 300,
    "originalName": "x.zip"
  }
}
```

> PRIVATE 对象鉴权（首版最简：上传人/管理员可见）+ 业务可见性后签发短期 GET 预签名（默认 5min 可配），带 `attachment;filename` 强制下载并指定文件名。前端拿 `downloadUrl` 跳转/拉取，后端不返回字节。

#### 5. 获取文件对象列表

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

#### 6. 获取文件对象详情

**基本信息：** `GET /file/{objectId}`　权限：`knowhub:file:info`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectId` 路径参数）

**响应示例：** `{"code":200,"msg":"请求成功","data":{...同列表项...}}`

#### 7. 绑定业务关联

**基本信息：** `PUT /file/bind`　权限：`knowhub:file:upload`　日志：`@Log(文件对象, UPDATE)`

**请求头：** `Token: <令牌值>`　`Content-Type: application/json`

**请求体（`BindVo`）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| objectId | long | 是 | 文件对象 ID |
| bizRefId | long | 是 | 业务关联 ID（业务行创建后回填，便于删业务行时级联清文件） |

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

#### 8. 批量删除文件对象

**基本信息：** `DELETE /file/{objectIds}`　权限：`knowhub:file:delete`　日志：`@Log(文件对象, DELETE)`

**请求头：** `Token: <令牌值>`

**请求体：** 无（`objectIds` 路径参数，逗号分隔）

**响应示例：** `{"code":200,"msg":"请求成功","data":true}`

> 软删 `file_object(deleted=1)`，对象本体由 `FileGcTask` 定时 `DeleteObject` 后物理删元数据（异步，支持误删恢复窗口）。