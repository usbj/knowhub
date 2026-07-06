> 日期：2026-06-30
> 状态：思路设计稿（定稿，未落地，未写码）
> 关联模块：knowhub-storage（`com.knowhub.storage`，待新建 Maven 模块）
> 关联文档：doc/knowhub-api.md、doc/knowhub-devlog.md、doc/blog/blog-module-design.md、doc/综合知识库管理系统-项目文档初稿.md、README.dev.md §6.1
> 遵守约定：见 doc/README.dev.md「全局开关落地约定」「rookie 框架代码修改禁令」

# knowhub 文件存储模块设计稿（思路版·定稿）

本文档给出 knowhub 知识库博客系统 **跨业务对象存储底座（RustFS + AWS SDK for Java v2 + 预签名直传）** 的思路设计，覆盖四个方面：存储选型与模型、数据库设计、流程架构、代码架构。该模块为博客配图、项目源码压缩包、项目可执行包、较大 Office 文档、资源模块文件资源、插件市场插件包等所有二进制内容提供统一"上传—校验—存储—取用"通道，技术选型与目标已由 `README.dev.md` §6.1 与项目文档 §6.1 沉淀，本文档只定落地方案，不重复选型论证。

## 0. 设计前提（已与用户对齐）

| 项 | 决定 | 说明 |
|----|------|------|
| 模块定位 | **跨业务底座**，独立 Maven 模块 `knowhub-storage` | 与 `knowhub-blog` 并列，`com.knowhub.storage` 命名空间；博客/项目/资源/插件市场按 `businessType` 复用，不自建多套上传接口 |
| 首版范围 | **完整版**：元数据表 + 预签名上传令牌 + 上传确认 + 预签名下载(公开/私有) + 对象 GC + 类型/大小白名单 | 一次到位，后续业务模块直接复用，不再大改 |
| 对象存储 | **RustFS**（S3 兼容本地自建） | 承载所有二进制/媒体/压缩包/Office 文档 |
| 客户端 | **AWS SDK for Java v2** `S3Client` | endpoint 指 RustFS，`pathStyleAccessEnabled=true`，region 占位值 |
| 上传链路 | **预签名 URL 直传** | 后端校验 contentType/size 后下发 `PutObject` 预签名 URL，前端直传 RustFS，后端不经流文件字节 |
| 正文类长文本 | **仍入 MySQL** | Markdown 博客正文不进对象存储；本模块只管二进制 |
| 库表 | **只存对象元数据** | `bucket`/`objectKey`/`contentLength`/`contentType`/`originalName`/校验值/状态，元数据与对象解耦 |
| 访问语义 | **PUBLIC/PRIVATE 两档** | PUBLIC（博客配图、Markdown 内插图）拼公开读 URL 或短期 GET 预签名；PRIVATE（项目源码包、私有资源）下载经后端鉴权后下发短期 GET 预签名 |
| SSE | **先不开** | 走明文桶跑通再说，对齐 §6.1 |
| 配置落点 | **追加进共享 `application.yml` 的 `storage:` 段** | 用户本轮已解除「rookie 框架代码修改禁令」，允许追加；密钥等敏感值用 `${RUSTFS_*}` 环境变量占位，仓库不落明文 |
| rookie 框架 | **零修改**（除共享 yml 追加一段外） | Mapper 扫描、springdoc 分组走本模块自带配置类自注册（仿 blog 模块套路） |

## 1. 存储选型与模型

### 1.1 内容分流（对齐 §6.1）

```
正文类长文本（Markdown 博客正文、章节文本）  ──►  MySQL（MEDIUMTEXT，复用 blog.content）
二进制/媒体/压缩包/Office/插件 jar           ──►  RustFS 对象存储（本模块统一收口）
```

> 博客正文当前存在 `blog.content`（`longtext`）。§6.1 建议"正文单独建表 `blog_content`，列表查询不带正文"——属博客模块后续优化项，不在本模块范围；本模块只做二进制底座。

### 1.2 业务复用模型

各业务模块上传文件时携带 `businessType`（受控枚举），后端据此：
- 选 `objectKey` 前缀（按业务分目录，便于 RustFS 侧按前缀做生命周期/统计）；
- 套用对应的类型/大小白名单（如源码压缩包放宽体积上限、插件 jar 限定 `.jar`）；
- 决定默认 `access`（博客配图默认 PUBLIC，项目源码包默认 PRIVATE）。

`businessType` 首版枚举（落地为字典 `file_business_type` + Java 枚举 `FileBusinessType`）：

```
BLOG_COVER      博客封面图      access=PUBLIC   类型 图片  上限 5MB
BLOG_BODY       博客正文配图    access=PUBLIC   类型 图片  上限 10MB
PROJECT_SRC     项目源码压缩包  access=PRIVATE  类型 压缩包 上限 500MB
PROJECT_PKG     项目可执行包/安装包 access=PRIVATE 类型 安装包 上限 500MB
PROJECT_DOC     项目大 Office 文档 access=PRIVATE 类型 docx/pptx/xlsx/pdf 上限 100MB
RESOURCE_FILE   资源模块文件资源 access=PRIVATE（资源模块定） 通用 上限 100MB
PLUGIN_JAR      插件市场插件包   access=PRIVATE 类型 .jar 上限 50MB
```

> 上限/白名单值落字典可后台改，读取收口到 `StorageConfigReader`（见 §5.2），业务侧不直接读字典——与 `BlogConfigReader` 同构，换存储/换阈值时只改 Reader 内部。

### 1.3 objectKey 规约

```
objectKey = {businessType小写}/{yyyy/MM/dd}/{uuid32}.{原始扩展名}
例：blog_body/2026/06/30/a1b2c3d4e5f6...png
```

- 前缀按业务分目录，便于 RustFS 侧按前缀统计/生命周期。
- 日期分层避免单目录对象过多。
- uuid32（Hutool `IdUtil.fastSimpleUUID()`）防碰撞、防遍历，不暴露原始文件名。
- 原始扩展名保留以方便 RustFS 侧推断 content-type（仍以预签名时声明的 contentType 为准）。

### 1.4 访问语义与取用

| access | 取用接口 | 后端返回 | 前端引用方式 | 鉴权 |
|--------|----------|----------|--------------|------|
| PUBLIC | `GET /file/public/{objectId}` | **302 重定向**到 RustFS 公开读 URL（或预签名 GET） | Markdown 正文 `<img src="/file/public/123">`、封面 `<img>` | 无（公开桶直拼则无有效期；预签名则自带签名） |
| PRIVATE | `GET /file/download/{objectId}` | JSON `{downloadUrl: "https://rustfs.../x.zip?签名"}` | 前端拿 `downloadUrl` 跳转/拉取 | 走 `@PreAuthorize('knowhub:file:download')` + 业务可见性 |

> **PUBLIC 走 302 重定向、PRIVATE 走预签名下载字符串**——这是已与用户对齐的方案 A。后端两种情况下都只返回 URL、不碰文件字节，对齐 §6.1"后端不经流文件字节"。
>
> **PUBLIC 预签名 GET 有效期**：若 RustFS 桶设公开读，则 302 直拼 `{endpoint}/{bucket}/{objectKey}` 无有效期；若桶非公开读，则 302 指向短期 GET 预签名（默认 5min 可配）。由 `StorageConfigReader.publicBucketReadable()` 决定走哪条。
>
> **PRIVATE 下载文件名**：后端签预签名时带 `ResponseContentDisposition=attachment;filename={originalName}`，强制浏览器下载并指定文件名（压缩包/Office 文档场景必需）；预签名有效期默认 5min 可配。

### 1.5 方案 A 的回显链路（已确认可行）

Markdown 预览用 `@kangc/v-md-editor` 的 `v-md-preview`（`rookie-ui/src/components/MarkdownPreview.vue`），`![图](url)` 渲染成 `<img src="url">`，浏览器对 `<img src>` 必然发请求，且默认跟随 302 重定向去 RustFS 拉图字节并渲染。链路：

```
正文 content 字符串：![架构图](/file/public/123)
   │ v-md-preview 渲染
   ▼
<img src="/file/public/123">  ──浏览器发请求──►  /file/public/123
   │ （<img> 不走 axios，靠 vite proxy / nginx 转发到后端，见 §1.6）
   ▼
后端 FileController.getPublic(objectId)
   │ 查 file_object(access=PUBLIC) → 拼真实 URL
   ▼
302 Location: https://rustfs-host:9000/knowhub/blog_body/.../x.png
   │ 浏览器自动跟随 302
   ▼
RustFS 返回图片字节  ──►  <img> 渲染成功
```

- `<img>` 默认不带 `crossorigin`，RustFS 公开桶**无需配 CORS** 即可加载（只有 canvas 读像素才需要 CORS）。
- 接口本身是"dumb 的 302"，不挑文件类型："渲染还是下载"取决于**前端引用方式**而非接口：
  - `<img src="/file/public/123">` → 仅图片能渲染，压缩包会裂图（img 只接受图片字节）。
  - `<a href="/file/public/123">下载源码</a>` 用户点击 → 浏览器导航到 302 → RustFS 返回 `application/zip` → 不能内联预览 → **自动下载**。
- **压缩包按设计是 PRIVATE，应走 `/file/download/{id}`**（见上表），享受鉴权 + 强制下载文件名 + 下载计数 + 业务可见性。只有"确实想公开下载"的场景才用 `/file/public/{id}` + `<a>`。

### 1.6 前端路径约定与 vite proxy（已落地）

`<img src="/file/public/123">` 是相对路径，浏览器请求当前 origin（dev 是 `http://localhost:5173`，prod 是站点域名）。**`<img src>` 不走 axios，axios 的 `baseURL=/api` 对它完全不生效**，必须靠代理把 `/file` 转发到后端，否则 dev 环境 404。

已在 `rookie-ui/vite.config.ts` 的 `server.proxy` 追加 `/file` 代理（不带 rewrite，保留 `/file` 前缀传给后端）：

```ts
'/file': {
  target: 'http://localhost:8080',
  changeOrigin: true,
},
```

- **dev**：浏览器请求 `http://localhost:5173/file/public/123` → vite 代理转发到 `http://localhost:8080/file/public/123` → 后端 302。
- **prod**：由 nginx（或同等反向代理）把 `/file` 转发到后端，与 `/api` 转发并列。**这是部署侧需配套的一条规则**，落地时同步给运维。
- 正文存**干净相对路径** `/file/public/{id}`，不带 `/api` 前缀、不带 endpoint 域名——换 OSS 时正文零改动（只改后端 `StorageProperties.endpoint` + 302 拼出来的真实 URL）。

## 2. 数据库设计

引擎/字符集沿用仓库：`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`。审计列统一 `create_by/create_time/update_by/update_time` + 软删 `deleted`(tinyint)，与 `blog`/`tag` 一致。时间列 `DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`。

> DDL + `sys_menu` 权限行 + 白名单字典，统一建在 `sql/knowhub-storage.sql`（独立文件，不改动上游 `sql/rookie.sql`，也不改动已落地的 `sql/knowhub-blog.sql`）。

### 2.1 文件对象元数据表 `file_object`

```sql
DROP TABLE IF EXISTS `file_object`;
CREATE TABLE `file_object` (
  `object_id`      bigint NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `bucket`         varchar(64)  NOT NULL COMMENT '桶名（RustFS bucket）',
  `object_key`     varchar(512) NOT NULL COMMENT '对象 key（业务前缀/日期/uuid.扩展名）',
  `original_name`  varchar(255) DEFAULT NULL COMMENT '原始文件名（用户上传时的名字，仅展示）',
  `content_length` bigint       DEFAULT NULL COMMENT '对象字节数（HeadObject 确认后回填）',
  `content_type`   varchar(128) DEFAULT NULL COMMENT 'MIME 类型（上传时声明，确认时以 HeadObject 为准）',
  `checksum`       varchar(128) DEFAULT NULL COMMENT '校验值（ETag / SHA256，确认时回填）',
  -- 业务归属
  `business_type`  varchar(32)  NOT NULL COMMENT '业务类型：BLOG_COVER/BLOG_BODY/PROJECT_SRC/...（见 §1.2）',
  `biz_ref_id`     bigint       DEFAULT NULL COMMENT '业务关联 ID（可空：上传时业务行可能还没建，确认后再回填）',
  `access`         varchar(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT '访问语义：PUBLIC公开 PRIVATE私有',
  -- 上传状态机
  `upload_status`  varchar(16)  NOT NULL DEFAULT 'PENDING'
                   COMMENT '上传状态：PENDING待确认 CONFIRMED已确认 FAILED失败 GC待回收',
  -- 通用审计
  `create_by`      varchar(64)  NOT NULL COMMENT '上传人(用户名)',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64)  NOT NULL COMMENT '更新人',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`object_id`),
  UNIQUE KEY `uk_bucket_object_key` (`bucket`, `object_key`),
  KEY `idx_file_biz` (`business_type`, `biz_ref_id`),
  KEY `idx_file_status` (`upload_status`),
  KEY `idx_file_create_time` (`create_time`),
  KEY `idx_file_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件对象元数据（RustFS 对象索引，只存元数据）';
```

设计要点：
- `bucket + object_key` 唯一，即"一个对象一行元数据"。
- `biz_ref_id` 可空：上传令牌签发时业务行（如博客）可能尚未创建；业务落库后再调 `/file/bind` 回填关联（或 confirm 时一并回填）。
- `upload_status` 四态机（见 §3.2）：PENDING（签了令牌、前端还没传完）→ CONFIRMED（HeadObject 核对通过）；失败或长期未确认 → FAILED/GC，定时任务清对象并删/标元数据行。
- 软删 `deleted=1` 仅标记元数据；对象本体由 GC 流程异步 `DeleteObject`，不与软删同步强一致（见 §3.4）。

### 2.2 菜单与权限（`sys_menu`）

仿博客模块三层结构（目录→页面→按钮），权限键 `knowhub:file:*`，与上游 `system:*` 对齐、`knowhub` 前缀区分。菜单行写进 `sql/knowhub-storage.sql`：

```
knowhub:file:quarry    文件元数据查询（列表）
knowhub:file:info      文件详情
knowhub:file:upload    上传令牌签发（前端预签名直传前置）
knowhub:file:download  预签名下载（私有对象取用）
knowhub:file:delete    文件删除（软删元数据 + GC 对象）
knowhub:file:review    文件审核/治理（违规下架，对齐 §6 内容治理）
```

> `menu_id` 接 `sql/knowhub-blog.sql` 之后（blog 用到 78），从 79 起续编；父目录挂在已有 `knowhub`（menu_id 63）下新建"文件管理"页(menu_type=2)。具体编号落地时与现网对齐。

### 2.3 字典（`sys_dict` / `sys_dict_data`）

> **2026-07-03 更新**：本节描述的 `file_size_limit` / `file_type_whitelist` 配置型字典已迁移到系统设置模块（`sys_config`），见 `sql/knowhub-sys-config-migration.sql` 与 `doc/README.dev.md`「全局开关 / 配置项落地约定」。下文为原始设计叙述，保留作历史参考；当前实现以 `StorageConfigReader` 走 `SysConfigUtil` 读 `knowhub.file.size_limit` / `knowhub.file.type_whitelist`（JSON 对象）为准。枚举型字典 `file_business_type` 仍走字典。

白名单与阈值落字典可后台改，读取收口 `StorageConfigReader`（§5.2）：

- `file_business_type`：§1.2 的业务类型枚举（label/value/sort，供前端下拉与后端校验共用）。
- `file_size_limit`：各业务类型的体积上限（dict_data_value 存字节数，dict_key 取业务类型，如 `file_size_limit:PROJECT_SRC`）。**或** 复用一条字典 `file_size_limit`，label=业务类型、value=MB 数——二选一，落地时定，建议后者更省表行。
- `file_type_whitelist`：各业务类型的扩展名/MIME 白名单（同上按业务类型分项，value 存逗号分隔的扩展名或 MIME）。

> 字典 SQL 两表备注字段拼写：`sys_dict.remake` / `sys_dict_data.remark`（沿用 blog 模块踩过的点）；`dict_id`/`dict_data_id` 接 blog 之后续编（blog 用到 14/60），从 15/61 起。

## 3. 流程架构

### 3.1 分层流程总览

```
HTTP 请求 + Token（TokenVerifyFilter 已有，JWT 校验）
   │
   ▼
Controller    @PreAuthorize 鉴权 + @Log 操作日志
   │  入参: UploadApplyVo / 路径参数(objectId) / FileObjectVo / FileQuarry
   ▼
Service (interface + impl)   事务边界 @Transactional(仅写元数据)
   │  白名单校验、objectKey 生成、S3Client 预签名签发/HeadObject/DeleteObject、元数据 CRUD
   │  阈值/白名单经 StorageConfigReader 读
   ▼
S3Client (AWS SDK v2)  ──预签名──►  RustFS 对象桶
   │
   ▼
Mapper (@Mapper) + *Mapper.xml   纯 MyBatis，PageHelper 分页
   │
   ▼
MySQL(knowhub 库) file_object 表  +  Redis(可选：上传令牌短期缓存/限流计数)
```

> S3Client 调用不进事务；事务只包元数据写。预签名签发是纯本地计算（不触网），HeadObject/DeleteObject 触网但放在事务外，失败时仅把元数据行置 FAILED/GC，由定时任务兜底。

### 3.2 上传状态机

```
POST /file/upload-token
   │ 校验通过、insert file_object(upload_status=PENDING)、签发 PutObject 预签名
   ▼
┌──────────┐   前端 PUT 直传成功 + POST /file/confirm/{objectId}    ┌───────────┐
│  PENDING │ ──────────────────────────────────────────────────►  │ CONFIRMED  │
│  待确认  │                                                       │  已确认    │
└────┬─────┘                                                       └─────┬─────┘
     │ 前端未确认超时(默认 30min，可配)                                  │ delete(软删)
     │ 或 HeadObject 校验不通过                                        ▼
     ▼                                                            ┌───────────┐
┌──────────┐  定时任务扫描 → DeleteObject 清对象 + 标元数据         │  GC 待回收 │ → 物理删元数据
│  FAILED  │ ─────────────────────────────────────────────────►   └───────────┘
│  失败    │
└──────────┘
```

- PENDING→CONFIRMED：前端传完调 confirm，后端 `HeadObject` 取真实 `contentLength`/`contentType`/`etag`，与 PENDING 行声明值核对（长度误差容许 0、type 必须落在白名单内），通过则置 CONFIRMED 并回填校验值。
- PENDING→FAILED：confirm 时 HeadObject 不通过（对象不存在 / 类型不符 / 超限）；或定时任务发现 PENDING 超 TTL 仍未确认。
- 任何状态→GC：删除（软删）后，定时任务对 `deleted=1` 的行 `DeleteObject` 并物理删元数据行（或保留行仅置 GC 便于审计，落地时定）。

### 3.3 各操作主流程

**上传令牌签发 Apply** — `POST /file/upload-token`，入参 `UploadApplyVo(businessType, contentType, size, originalName, access?)`：
1. `StorageConfigReader` 取该 `businessType` 的类型白名单 + 体积上限。
2. 校验 `contentType ∈ 白名单`、`size ≤ 上限`（提前拦，避免签了令牌传超限文件）。
3. 生成 `objectKey`（§1.3），`access` 缺省按 `businessType` 默认值。
4. `file_object` insert 一行 `upload_status=PENDING`，记录声明值（`create_by`=当前用户）。
5. `S3Client` 签发 `PutObject` 预签名 URL（带 `Content-Type`、`Content-Length` 条件约束，有效期默认 10min 可配）。
6. 返回 `UploadTokenVo(uploadUrl, objectKey, objectId, expires)`。

**上传确认 Confirm** — `POST /file/confirm/{objectId}`：
1. 查 `file_object`，须为 PENDING 且属于当前用户（或管理员）。
2. `HeadObject` 取真实 `contentLength`/`contentType`/`etag`。
3. 核对：长度==声明、type∈白名单；不符置 FAILED 并返回失败码。
4. 通过则回填 `contentLength`/`contentType`/`checksum=etag`，置 CONFIRMED。
5. 可选：入参带 `bizRefId` 一并回填业务关联。

**PUBLIC 回显 Public** — `GET /file/public/{objectId}`（无鉴权，给 `<img>`/`<a>` 直接引用）：
1. 查 `file_object`，须 CONFIRMED 且 `access=PUBLIC` 且未软删。
2. 拼真实 URL：`publicBucketReadable=true` → `{endpoint}/{bucket}/{objectKey}`；`=false` → 签短期 GET 预签名。
3. 返回 `302` + `Location` 头（`ResponseEntity<Void>`，**不返回 body**）。浏览器/`<img>` 自动跟随。
> 这个接口是 dumb 的 302，不挑文件类型；"渲染还是下载"取决于前端用 `<img>` 还是 `<a>`（见 §1.5）。压缩包若误用 `<img>` 会裂图。

**PRIVATE 下载 Download** — `GET /file/download/{objectId}`（鉴权，返回预签名 URL 字符串）：
1. 查 `file_object`，须 CONFIRMED 且 `access=PRIVATE` 且未软删。
2. `@PreAuthorize('knowhub:file:download')` 已挡未登录；再按 `businessType`+`bizRefId` 校验业务可见性（首版最简：上传人/管理员可见；业务模块接入时细化回调）。
3. 签发短期 GET 预签名，带 `ResponseContentDisposition=attachment;filename={originalName}`（强制下载 + 指定文件名）。
4. 返回 `Result<DownloadVo{downloadUrl, expires}>`，前端拿 `downloadUrl` 跳转/拉取。
> 压缩包、Office 文档、插件 jar 走此接口；后端只返回 URL，不返回字节。

**删除 Delete** — `DELETE /file/{objectIds}`（批量，逗号分隔）：
1. 软删 `file_object`（`deleted=1`，记 `update_by`）。
2. 对象本体不立即删，由 GC 定时任务对 `deleted=1` 行 `DeleteObject` 后清元数据（异步，避免删除接口触网阻塞、且支持"误删恢复窗口"）。
3. 失效相关缓存（若有）。

**查询列表 Quarry** — `GET /file/list`，`FileQuarry(businessType, uploadStatus, access, createBy, 时间区间, pageNum/pageSize)`：`PageUtil.startPage()` → mapper → `packagedPageInfo` + `copyPageInfo(FileObjectVo.class)`，仿博客列表三段式。

**查询详情 Info** — `GET /file/{objectId}`：查库回填 VO，带 `access`/`uploadStatus`/校验值等。

**绑定业务关联 Bind（可选，首版可不做）** — `PUT /file/bind`，入参 `(objectId, bizRefId)`：业务行创建后回填 `biz_ref_id`，便于"删业务行时级联清文件"。若 confirm 时已带 `bizRefId` 则可省。

### 3.4 对象 GC（定时任务）

- 定时扫描 `upload_status=PENDING AND create_time < now()-TTL` → `DeleteObject` + 置 GC/物理删。
- 定时扫描 `deleted=1` 的行 → `DeleteObject` + 物理删元数据（或保留审计）。
- 定时任务用 Spring `@Scheduled`（项目已有异步配置 `rookie-framework`，定时任务扩展点后续插件市场 §10.5 也覆盖）；首版可在本模块写一个 `@Component` + `@Scheduled(fixedDelay=...)` 的 `FileGcTask`，频率默认 10min 可配。
- GC 失败重试：`DeleteObject` 失败则保留行、下次再扫，记日志（对齐错误日志体系）。

### 3.5 缓存策略（首版可极简）

| 场景 | Key | 行为 |
|------|-----|------|
| 上传令牌短期缓存（可选） | `file:token:{objectId}` | 存令牌签名参数，confirm 时核对，TTL 同令牌有效期 |
| 上传限流计数（可选） | `file:upload:count:{userId}:{day}` | 防滥用，每日上传次数上限（字典可配） |
| PUBLIC 直拼 URL | 无缓存 | URL 由 endpoint+bucket+objectKey 直拼，无状态 |

> 首版缓存可不做，令牌签名参数本就在 `file_object` 行里；限流等用户量上来再加。所有 key 经 `RedisCache` 自动加 `rookie:framework:` 前缀（已配置）。

## 4. 代码架构

### 4.1 模块骨架（`knowhub-storage` 模块内）

```
knowhub-storage/src/main/java/com/knowhub/storage/
├── config/
│   ├── StorageMapperConfig.java          @MapperScan("com.knowhub.storage.mapper")，仿 BlogMapperConfig
│   ├── StorageOpenApiConfig.java         GroupedOpenApi "storage"，仿 BlogOpenApiConfig
│   ├── StorageProperties.java            @ConfigurationProperties(prefix="storage")，RustFS 连接参数收口
│   ├── S3ClientConfig.java               构造 S3Client Bean（pathStyle=true，region 占位）
│   └── StorageConfigReader.java          业务侧唯一入口：桶名/大小上限/类型白名单/令牌有效期，内部走字典或 Properties
├── controller/
│   └── FileController.java               上传令牌/确认/PUBLIC 回显(302)/PRIVATE 下载/删除/列表/详情
├── service/
│   ├── FileService.java                  (interface)
│   └── impl/FileServiceImpl.java
├── mapper/
│   └── FileObjectMapper.java
├── pojo/
│   ├── vo/
│   │   ├── UploadApplyVo.java            (businessType, contentType, size, originalName, access?)
│   │   ├── UploadTokenVo.java            (uploadUrl, objectKey, objectId, expires)
│   │   ├── DownloadVo.java               (downloadUrl, expires)  ← PRIVATE 下载返回
│   │   └── FileObjectVo.java             (元数据回显)
│   └── quarry/
│       └── FileQuarry.java               (businessType, uploadStatus, access, createBy, 时间区间, pageNum/pageSize)
├── enums/
│   ├── FileBusinessType.java             (BLOG_COVER/BLOG_BODY/PROJECT_SRC/PROJECT_PKG/PROJECT_DOC/RESOURCE_FILE/PLUGIN_JAR)
│   ├── FileAccess.java                   (PUBLIC/PRIVATE)
│   ├── UploadStatus.java                 (PENDING/CONFIRMED/FAILED/GC)
│   └── task/
│       └── FileGcTask.java               (@Scheduled 对象 GC)
└── resources/
    └── mapper/storage/
        └── FileObjectMapper.xml
```

**实体放置**：`FileObject` 放 `com.knowhub.storage.pojo.entity`（与 blog 模块 `entity.pojo.com.knowhub.Blog` 同套路，模块内自治），`extends com.rookie.common.pojo.BaseEntity`。项目**不用 Lombok**，全手写 getter/setter（仿 `Blog.java`）。

**模块依赖**：`knowhub-storage/pom.xml` 仿 `knowhub-blog/pom.xml`，依赖 `rookie-framework`（传递引 common+system）；额外引 AWS SDK v2：

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
<!-- 预签名需要 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3-presigner</artifactId>
</dependency>
```

版本由根 `pom.xml` 的 `<dependencyManagement>` 统一管（AWS SDK v2 用 BOM `software.amazon.awssdk:bom`，落地时在根 pom 加 BOM import + 版本属性，**根 pom 改 dependencyManagement 属"新增依赖声明"非改框架行为，需用户确认是否算触禁令——建议同样走"追加"而非改既有**）。

> 根 `pom.xml` 的 `dependencyManagement` 是聚合工程的依赖声明中枢，往里追加 AWS SDK BOM 与 blog 已追加的 `knowhub-blog` 依赖同性质（都是新增模块所需声明），不改动既有框架依赖。落地时若你希望根 pom 也零改，可在 `knowhub-storage/pom.xml` 内自带版本号声明，但失去统一版本管理。**建议追加进根 pom dependencyManagement，并就此单独跟你确认。**

**聚合注册**：根 `pom.xml` 的 `<modules>` 追加 `<module>knowhub-storage</module>`（blog 已在列）；`rookie-admin/pom.xml` 需显式追加对 `knowhub-storage` 的 `<dependency>`（核实见下），启动类 `KnowhubApplication` 的 `scanBasePackages={"com.rookie","com.knowhub"}` 已覆盖 `com.knowhub`，**无需改启动类**。

> **已核实 blog 接入方式**：`rookie-admin/pom.xml` 显式 `<dependency>` 引 `knowhub-blog`（groupId `com.knowhub`），`KnowhubApplication` 的 `scanBasePackages={"com.rookie","com.knowhub"}` 扫到 `com.knowhub.blog`。`knowhub-storage` 照抄即可——`rookie-admin/pom.xml` 追加 `<dependency>` 引 `knowhub-storage`，启动类零改。设计稿原本的"待确认项 1"已落地为结论。

### 4.2 分层要点（沿用 blog 模块约定）

- **Controller**：路径 `/file`（不套 `/sys`，因 storage 在 `com.knowhub` 命名空间）。`@Tag`/`@Operation`(springdoc) 描述。写操作加 `@Log(title="文件对象", businessType=...)`，读操作不加。方法 `@PreAuthorize('knowhub:file:quarry/info/upload/download/delete/review')`。请求头 `Token` 无 Bearer 前缀。
- **Service**：接口只暴露 DTO，不暴露实体。实现 `@Service`，写元数据方法 `@Transactional`，失败 `throw new ServiceException(500, "...", e.getMessage())`。DTO↔Entity 用 Hutool `BeanUtil.toBean`。
- **列表分页三段式**：`PageUtil.startPage()` → mapper 查 → `PageUtil.packagedPageInfo` → `copyPageInfo(page, FileObjectVo.class)`。
- **当前用户**：`(UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()`，调 `.getUserId()`/`.getUsername()`（项目无 SecurityUtils 封装，仿 `BlogServiceImpl.currentUser()`）。
- **返回**：统一 `Result.success(data)`。
- **Mapper + XML**：接口加 `@Mapper`，XML 放模块内 `resources/mapper/storage/*Mapper.xml`，被现有 `mybatis.mapperLocations: classpath*:mapper/**/*Mapper.xml` 自动扫描。**全限定类名**引用类型（项目未配 type-aliases）。纯 MyBatis（不引 MyBatis-Plus），分页全靠 PageHelper。
- **insert 动态列**：XML insert 用 `<trim>`+`<if>` 动态列模式（对齐 README.dev「Mapper XML insert 均改为动态列」约定），避免漏传有默认值的列时插空值报错。

### 4.3 模块自注册（**零修改 rookie 框架**，仿 blog 套路）

`KnowhubApplication`（`scanBasePackages={"com.rookie","com.knowhub"}`）已扫 `com.knowhub`，自带 `@Configuration` 会被扫到：

1. **Mapper 自注册** — `StorageMapperConfig`：`@Configuration @MapperScan("com.knowhub.storage.mapper")`，与框架 `@MapperScan("com.rookie.**.mapper")` 叠加生效。
2. **springdoc 分组自注册** — `StorageOpenApiConfig`：`@Bean GroupedOpenApi storageGroup()`，`packagesToScan("com.knowhub.storage.controller")`。
3. **S3Client Bean** — `S3ClientConfig`：读 `StorageProperties` 构造 `S3Client`（`S3Configuration.builder().pathStyleAccessEnabled(true).build()`）+ `S3Presigner`，`@Bean` 暴露。

> 这三处是"新增本模块文件"，非改动 rookie 既有代码/配置，符合禁令。

### 4.4 S3Client 构造要点（AWS SDK v2 + RustFS）

```java
// com.knowhub.storage.config.S3ClientConfig —— 草式，落地时补全
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))   // http://rustfs-host:9000
                .region(Region.of(props.getRegion()))                 // 任意占位，如 "us-east-1"
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s -> s.pathStyleAccessEnabled(true))  // RustFS 必须
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s -> s.pathStyleAccessEnabled(true))
                .build();
    }
}
```

预签名签发（签发是纯本地计算，不触网）：

```java
// PutObject 预签名（上传令牌）
PutObjectRequest putReq = PutObjectRequest.builder()
        .bucket(bucket).key(objectKey).contentType(contentType).build();
PresignedPutObjectRequest pre = s3Presigner.presignPutObject(p -> p
        .putObjectRequest(putReq)
        .signatureDuration(Duration.ofMinutes(props.getUploadExpireMinutes())));
String uploadUrl = pre.url().toString();

// HeadObject 确认
HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
        .bucket(bucket).key(objectKey).build());
long realLen = head.contentLength();
String realType = head.contentType();
String etag = head.eTag();

// 预签名 GET（下载）
PresignedGetObjectRequest preGet = s3Presigner.presignGetObject(p -> p
        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
        .signatureDuration(Duration.ofMinutes(props.getDownloadExpireMinutes())));
String downloadUrl = preGet.url().toString();

// 删除（GC）
s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
```

## 5. 配置收口与 `StorageConfigReader`

### 5.1 `application.yml` 追加 `storage:` 段（用户已解除禁令）

在 `rookie-admin/src/main/resources/application.yml` 末尾追加（与现有 `redis:`/`spring:`/`mybatis:` 并列）：

```yaml
# 对象存储（RustFS / S3 兼容）配置，见 knowhub-storage 模块
storage:
  endpoint: ${RUSTFS_ENDPOINT:http://localhost:9000}
  region: ${RUSTFS_REGION:us-east-1}
  access-key: ${RUSTFS_ACCESS_KEY:minioadmin}
  secret-key: ${RUSTFS_SECRET_KEY:minioadmin}
  bucket: ${RUSTFS_BUCKET:knowhub}
  path-style-access: true          # RustFS 必须
  upload-expire-minutes: 10        # 上传令牌有效期
  download-expire-minutes: 5       # 下载预签名有效期
  pending-ttl-minutes: 30          # PENDING 未确认 GC 阈值
  gc-interval-minutes: 10          # GC 定时扫描间隔
  public-bucket-readable: true     # PUBLIC 对象是否直拼公开读 URL（false 则也走预签名 GET）
```

> 敏感值（access-key/secret-key）一律 `${ENV:默认}` 占位，真实值由环境变量注入，仓库不落明文。默认值 `minioadmin` 仅本地联调用，生产覆盖。

### 5.2 读取收口到 `StorageConfigReader` + `StorageProperties`

`StorageProperties`（`@ConfigurationProperties(prefix="storage")`）只承载**连接参数 + 阈值**（endpoint/key/桶/有效期/GC 间隔）；**类型白名单、各业务体积上限**走字典（可后台改），收口到 `StorageConfigReader`：

```java
// com.knowhub.storage.config.StorageConfigReader —— 业务侧唯一"知道白名单/上限从哪来"的地方
@Component
public class StorageConfigReader {

    public static final String DICT_KEY_SIZE_LIMIT = "file_size_limit";
    public static final String DICT_KEY_TYPE_WHITELIST = "file_type_whitelist";

    /** 取某业务类型的体积上限（字节），缺省回退到 Properties 里的全局上限 */
    public long sizeLimit(FileBusinessType type) { /* 走 DictUtil，缺省回退 */ }

    /** 取某业务类型的类型白名单（扩展名/MIME 列表） */
    public List<String> typeWhitelist(FileBusinessType type) { /* 走 DictUtil */ }

    /** PUBLIC 对象是否直拼公开读 URL */
    public boolean publicBucketReadable() { /* 走 Properties */ }
    // 将来换系统设置表：只改本类内部实现，签名与调用方零改动
}
```

- `FileServiceImpl` 只调 `storageConfigReader.sizeLimit(type)` / `typeWhitelist(type)`，**业务侧绝不直接 `DictUtil`**——与 `BlogConfigReader` 同构。
- 编辑白名单字典后走 `/sys/dist/data` 的 edit 接口自带 `DictUtil.setDictData` 刷缓存（复用现成，见 blog 模块踩点）。

### 5.3 将来切系统设置表的影响面

- 连接参数耦合点在 `StorageProperties`；白名单/上限耦合点在 `StorageConfigReader`；换存储/换阈值只改各自内部，`FileServiceImpl` 等调用方零改动。

## 6. 落地清单（思路定稿后执行）

**新建（全部在本模块或 `sql/`，不动 rookie 框架）：**
- `knowhub-storage` 模块全套：controller(1) / service+impl / config(5) / mapper(1) / pojo(vo,quarry) / enums(3) / task(1) / `resources/mapper/storage/*.xml`
- 实体（放 `com.knowhub.storage.pojo.entity`，模块内自治，仿 blog 模块）：`FileObject`
- `sql/knowhub-storage.sql`：`file_object` 表 DDL + `sys_menu` 权限行（`knowhub:file:*`）+ `file_business_type`/`file_size_limit`/`file_type_whitelist` 字典

**需用户确认的"追加"项（非改框架行为，但触及禁令文件）：**
- 根 `pom.xml` `<dependencyManagement>` 追加 AWS SDK v2 BOM + `s3`/`s3-presigner` 声明
- 根 `pom.xml` `<modules>` 追加 `<module>knowhub-storage</module>`
- `rookie-admin/pom.xml` 追加对 `knowhub-storage` 的依赖（确认 blog 当前是如何被 admin 引入的——见下方待确认）
- `rookie-admin/src/main/resources/application.yml` 追加 `storage:` 段（用户已解除禁令）

**零修改之处（遵守禁令）：**
- 不改 `rookie-framework`（用 `StorageMapperConfig`/`StorageOpenApiConfig` 自注册）
- 不改 `rookie-system`、不改 `rookie-common` 既有代码/配置（实体仅新增文件）
- 不改 `sql/rookie.sql`、不改 `sql/knowhub-blog.sql`

**待确认项（落地前必须核实）：**
1. **`knowhub-storage` 接入 admin 的方式已明确**（照抄 blog）：`rookie-admin/pom.xml` 显式 `<dependency>` 引 `knowhub-storage` + 根 `pom.xml` `<modules>` 加 `<module>knowhub-storage</module>`；启动类 `scanBasePackages` 已覆盖，零改。原"待确认"已落地为结论，保留此处为落地动作备忘。
2. **AWS SDK v2 版本**：当前为 `2.x`（建议取最新稳定版，如 `2.29.x`），落地时定。
3. **RustFS 桶公开读策略**：`public-bucket-readable` 取决于 RustFS 桶策略配置，需与你本地 RustFS 部署对齐。
4. **业务可见性校验回调**：PRIVATE 下载的"业务可见性"首版做最简（上传人/管理员可见），后续业务模块接入时再细化回调接口。
5. **根 `pom.xml` `dependencyManagement` 追加 AWS SDK BOM**：属"新增依赖声明"，不改动既有框架依赖，但触及禁令文件（根 pom），落地时需单独跟你确认这一次追加。

**文档同步（按 doc 约定）：**
- `doc/knowhub-api.md`：按四子节格式补接口。上传令牌/确认/PUBLIC 回显(302)/PRIVATE 下载/删除/列表/详情 ≈ 7 个，满足"5–8/模块"门限；追记「接口更新日志」一条
- `doc/knowhub-devlog.md`：仅当**实际代码落地**后追记（设计稿不入 devlog）

**前端配套（已落地一项，落地代码时再核对）：**
- `rookie-ui/vite.config.ts` 已追加 `/file` 代理（§1.6）——**当前工作区已改**
- 部署侧 nginx 需配套 `/file` 转发规则（与 `/api` 并列），落地时同步给运维
- 博客编辑器插图流程需配合：选图→`POST /file/upload-token` 拿预签名 URL→前端直传 RustFS→`POST /file/confirm` 拿 objectId→把 `/file/public/{objectId}` 插入正文→保存（前端任务，落博客配图时做）

**易漏点速查（落地务必覆盖）：**
1. 预签名 `PutObject` 带 `Content-Type`/`Content-Length` 条件约束，防前端改类型/超限直传（§3.3 Apply）
2. confirm 时 `HeadObject` 核对真实值，不能信前端声明（§3.3 Confirm）
3. PENDING 超时 GC + 软删对象 GC 双定时任务，`DeleteObject` 失败保留重试（§3.4）
4. `StorageConfigReader` 收口白名单/上限，业务侧禁直接 `DictUtil`（§5.2）
5. 字典 SQL 两表备注字段拼写：`sys_dict.remake` / `sys_dict_data.remark`；controller 前缀 `/sys/dist`（沿用 blog 踩点）
6. S3Client `pathStyleAccessEnabled(true)` 必须，否则 RustFS path-style 路由 404（§4.4）
7. 实体 `FileObject` 放 `com.knowhub.storage.pojo.entity`（模块内自治，仿 blog），`extends com.rookie.common.pojo.BaseEntity`（§4.1）
8. XML insert 用 `<trim>`+`<if>` 动态列，避免漏传默认列插空值（§4.2）
9. `/file/public/{id}` 返回 **302**（非 200 + body），Controller 用 `ResponseEntity<Void>` + `Location` 头，别写成返回 JSON（§1.4、§1.5）
10. `<img src="/file/public/123">` 不走 axios，靠 vite proxy / nginx 转发；部署必须配 `/file` 转发规则（§1.6）
11. PRIVATE 下载预签名带 `ResponseContentDisposition=attachment;filename=`，否则浏览器可能内联预览而非下载（§1.4）
