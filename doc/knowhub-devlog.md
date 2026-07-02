# knowhub 专属开发日志

本文件记录 **knowhub 知识库博客系统二次开发** 阶段每次协作完成的代码开发任务。只有产生实际文件或代码变动的任务才记录；探讨规则、项目规划、文档框架搭建等不在此列。

> 说明：上游 `rookie` 后台管理基础框架阶段（用户/角色/菜单/字典/通知/日志等底层模块）的历史开发记录保留在 `doc/devlog.md` 不动。本文件只承接 knowhub 阶段（博客 / 文档 / 项目 / 资源 / 审核等上层业务）新增的开发任务。

**记录约定：**
- 只记录"已完成"的代码开发任务
- 记录文件路径和简要变更描述，不记录具体行号
- 同一轮对话完成的相关任务合并为一个条目
- 时间精确到分钟
- 此约定后续可能变动，以文件内最新说明为准

---

> knowhub 阶段开发尚未开始业务代码落地（`knowhub-blog` 模块 controller/service/mapper/pojo 当前为空架子）。下方从业务首个代码任务起按 `## 日期` → `### 时间-任务简介` → 变更清单 的格式追加。

## 2026-06-30

### 22:06 博客模块首轮落地（文章 CRUD + 受控标签 + 审核/点赞/收藏）

按 `doc/blog/blog-module-design.md` 定稿方案落地博客模块首轮代码，新增文件均在 `knowhub-blog` 模块或 `rookie-common`（仅新增实体，未改既有文件），未修改任何 `rookie-*` 既有代码/配置。

**SQL（新建独立脚本，不动上游 `sql/rookie.sql`）**
- `sql/knowhub-blog.sql`：建 `article`（含 `cover_url`、`review_*`、`view/like/collect_count`、FULLTEXT(ngram) title+content）/ `tag` / `article_tag` / `article_like` / `article_collect` 五表；追加 `sys_menu` 权限行（menu_id 63–78，`blog:article:*` + `blog:tag:*`）；追加 `sys_dict`/`sys_dict_data` 字典：`blog_review_enabled`(审核开关)、`article_status`(文章状态)、`review_status`(审核状态)。

**实体（`com.rookie.common.pojo.entity`，仅新增）**
- `Article.java`、`Tag.java`（ext BaseEntity）；`ArticleTag.java`、`ArticleLike.java`、`ArticleCollect.java`（轻量 POJO，无审计列，仿 `SysNoticeGroupRel`）。

**knowhub-blog 模块代码**
- `enums/`：`ArticleStatus`（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED）、`ReviewStatus`（NONE/PENDING/APPROVED/REJECTED）。
- `pojo/vo/`：`ArticleVo`（含 tagIds/tagNames/hasLiked/hasCollected）、`TagVo`、`ReviewVo`；`pojo/quarry/ArticleQuarry`（keyword+tagIds+status+reviewStatus+时间区间）。
- `config/`：`BlogMapperConfig`（`@MapperScan("com.knowhub.blog.mapper")` 自注册）、`BlogOpenApiConfig`（springdoc `blog` 分组自注册）、`BlogConfigReader`（审核开关读取收口，走 `DictUtil`）。
- `mapper/` + `resources/mapper/blog/*.xml`：Article/Tag/ArticleTag/ArticleLike/ArticleCollect 五套，含 `MATCH...AGAINST IN BOOLEAN MODE` + `article_tag` join `HAVING COUNT=tagIds.size` 多标签同时命中查询。
- `service/` + `impl/`：`ArticleServiceImpl`（缓存读穿透、标签受控校验、先删后插、发布经 `BlogConfigReader.isReviewEnabled()` 分支、审核通过/驳回、点赞/收藏幂等、归属校验、浏览 Redis incr）、`TagServiceImpl`（删除级联清 `article_tag`）。
- `controller/`：`ArticleController`（11 个接口）、`TagController`（5 个接口），仿 `SysNoticeController` 的 `@PreAuthorize`/`@Log`/`@Operation`/`Result.success` 写法。

**校验**
- `./mvnw -q -pl knowhub-blog -am compile` 通过（EXIT=0，blog 与实体 class 均生成）。

**遵守约定**
- 未修改 `rookie-framework/ApplicationConfig`、`rookie-admin/application.yml`（以本模块 `BlogMapperConfig`/`BlogOpenApiConfig` 自注册替代），未动 `sql/rookie.sql`；遵守 `doc/README.dev.md`「rookie 框架代码修改禁令」。
- 审核开关走字典、读取收口于 `BlogConfigReader`，遵守「全局开关落地约定」。

### 22:49 博客模块命名调整：article → blog（方案B，两段式权限键）

应模块语义"博客(blog)"本身即文章统称，将"文章"实体从 `article` 统一改为 `blog`，权限键由三段式 `blog:article:*`/`blog:tag:*` 降为两段式 `blog:*`/`tag:*`，避免 `blog:blog` 冗余。改动均为替换/重命名，无逻辑变更。

**SQL** `sql/knowhub-blog.sql`
- 表名：`article`→`blog`（主键 `article_id`→`blog_id`）、`article_tag`→`blog_tag`、`article_like`→`blog_like`、`article_collect`→`blog_collect`；索引/键名同步 `idx_blog_*`、`ft_blog_title_content`。
- 字典：`article_status`→`blog_status`（dict_id 13 不变，dict_key 改名）；`blog_review_enabled`、`review_status` 不变。
- `sys_menu`：权限键改为两段式 `blog:quarry/info/add/edit/delete/publish/revoke/review`（menu_id 65–72）与 `tag:quarry/...(74–78)`；目录 `blog`(63) + 文章管理页(64,perm_key 置空) + 标签管理页(73,perm_key=`tag`)。

**实体** `com.rookie.common.pojo.entity`
- 删 `Article/ArticleTag/ArticleLike/ArticleCollect`，新增 `Blog/BlogTag/BlogLike/BlogCollect`（字段 `blogId`，余同）。

**knowhub-blog 模块**
- 删 `ArticleStatus/ArticleVo/ArticleQuarry/ArticleService(+Impl)/ArticleController` 及 4 个 `Article*Mapper` 与对应 XML；
- 新增 `BlogStatus/BlogVo/BlogQuarry/BlogService(+Impl)/BlogController` 与 `BlogMapper/BlogTagMapper/BlogLikeMapper/BlogCollectMapper` + 对应 XML。
- `ReviewVo.articleId`→`blogId`，`ReviewStatus` 注释 `article.review_status`→`blog.review_status`。
- `TagController`/`TagServiceImpl` 权限键与引用改为两段式 `tag:*` 与 `BlogTagMapper.deleteBlogTagByTagId`；`BlogConfigReader` 注释 `ArticleServiceImpl`→`BlogServiceImpl`。
- 路由 `/article`→`/blog`；缓存 key `blog:article:detail/view`→`blog:detail/view`。

**校验**
- `./mvnw -q -pl knowhub-blog -am compile` 通过（EXIT=0，无残留 Article 符号与 class）。

### 23:14 权限键改三段式并加项目前缀 knowhub

将博客模块权限键由两段式 `blog:*`/`tag:*` 改为三段式 `knowhub:blog:*`/`knowhub:tag:*`，与上游 `system:notice:*` 命名规则对齐，`knowhub` 前缀区分二开新增与上游原有。

**改动**
- `sql/knowhub-blog.sql`：`sys_menu` 行 perm_key 改为 `knowhub`/`knowhub:blog`/`knowhub:tag`/`knowhub:blog:quarry|...`/`knowhub:tag:quarry|...`；目录(63) perm_key=`knowhub`，文章页(64) `knowhub:blog`，标签页(73) `knowhub:tag`。
- `BlogController`/`TagController`：`@PreAuthorize` 全部改为 `hasAuthority('knowhub:blog:*')` / `hasAuthority('knowhub:tag:*')`；`BlogServiceImpl.checkOwnerOrAdmin` 管理员判定串改为 `knowhub:blog:review`。
- `doc/knowhub-api.md`、`doc/blog/blog-module-design.md`：权限键相关表述同步三段式。
- 路由 `/blog`、`/tag` 与表名/类名/缓存 key **不变**。

**校验**
- `./mvnw -q -pl knowhub-blog -am compile` 通过（EXIT=0）。

### 21:54 knowhub 业务实体从 rookie-common 迁回 knowhub-blog

遵守新立规则「knowhub 业务产物不得放入 rookie 模块」：将博客实体从 `rookie-common` 移到本模块 `knowhub-blog` 内，rookie 模块不再承载任何 knowhub 业务内容。

**改动**
- 删除 `rookie-common/.../pojo/entity/` 下的 `Blog.java`、`BlogTag.java`、`BlogLike.java`、`BlogCollect.java`、`Tag.java`；该目录现仅保留 `SysNotice/SysUser/...` 等框架实体。
- 新建 `knowhub-blog/.../pojo/entity/{Blog,BlogTag,BlogLike,BlogCollect,Tag}.java`，`package com.knowhub.blog.pojo.entity`，仍 `extends com.rookie.common.pojo.BaseEntity`（引用框架基类，非在框架新增内容）。
- 7 个 java 文件（5 mapper + 2 service.impl）与 5 个 mapper XML 的实体 import / `type=` / `parameterType=` 全部由 `com.rookie.common.pojo.entity.*` 改为 `com.knowhub.blog.pojo.entity.*`。

**规则落地（doc/README.dev.md）**
- 「rookie 框架代码修改禁令」下新增子节「knowhub 业务产物不得放入 rookie 模块」：实体/DTO/Mapper/Service/Controller/配置一律归对应 knowhub 模块自身包；业务实体可引用框架 `BaseEntity` 但不得向 `rookie-*` 新增任何类/接口/配置；并给出违规示例与判定口径。
- `doc/blog/blog-module-design.md` §4.1 包结构补 `pojo/entity/` 子目录、§4.1 实体放置说明改为"放本模块"、§6 落地清单同步。

**校验**
- `./mvnw -q -pl knowhub-blog -am compile` 通过（EXIT=0）；`rookie-common` target 下无 Blog*/Tag 实体 class，`knowhub-blog` target 下 `com/knowhub/blog/pojo/entity/` 五个实体 class 齐全。
## 2026-07-01

### 15:20 knowhub-blog 模块改名为 knowhub + 包名 com.knowhub.blog→com.knowhub

将承载 knowhub 全部业务的单一模块从 `knowhub-blog` 改名为 `knowhub`，包名去掉 `blog` 中段，统一归 `com.knowhub` 顶层包，为后续文件存储/项目/资源等业务并入同一模块铺平。

**改动**
- 模块目录 `knowhub-blog/` → `knowhub/`，`artifactId` `knowhub-blog` → `knowhub`；根 `pom.xml` `<modules>` 同步。
- 全部 java 文件 `package com.knowhub.blog.*` → `com.knowhub.*`，import 同步；mapper XML `namespace`/`type=`/`parameterType=` 由 `com.knowhub.blog.*` 改为 `com.knowhub.*`；mapper xml 目录 `resources/mapper/blog/` 保留（文件名不变，仅命名空间改）。
- `BlogMapperConfig` 的 `@MapperScan("com.knowhub.blog.mapper")` → `@MapperScan("com.knowhub.mapper")`；`BlogOpenApiConfig` 的 `packagesToScan("com.knowhub.blog.controller")` → `com.knowhub.controller`，`group("blog")` → `group("knowhub")`。两处注释更新为"knowhub 业务统一扫描"。
- `rookie-admin/pom.xml` 依赖 `knowhub-blog` → `knowhub`；`RustFsConnectTest` 等测试类 import 同步。

**校验**
- `./mvnw -q -pl knowhub -am compile` 通过（EXIT=0）；`./mvnw -q -pl rookie-admin -am compile` 通过（EXIT=0）。

### 16:40 文件存储模块落地（RustFS + AWS SDK v2 + 预签名直传 + 对象 GC）

按 `doc/storage/file-storage-module-design.md` 定稿方案落地文件存储底座首轮代码，承载博客配图/项目源码包/资源文件/插件 jar 等所有二进制内容的统一"上传—校验—存储—取用"通道。代码全部在 `knowhub` 模块内 `com.knowhub.*` 顶层包，未修改任何 `rookie-*` 既有代码/配置（仅追加共享 `application.yml` 的 `storage:` 段，用户本轮已解除禁令）。

**SQL（新建独立脚本，不动上游 `sql/rookie.sql` 与已落地 `sql/knowhub-blog.sql`）**
- `sql/knowhub-storage.sql`：建 `file_object`（含 `bucket`/`object_key` 唯一键、`business_type`+`biz_ref_id`/`upload_status`/`create_time`/`deleted` 索引，四态 `upload_status`）；追加 `sys_menu` 权限行（menu_id 79–85，三段式 `knowhub:file:quarry/info/upload/download/delete/review`，挂在 knowhub 目录 63 下的文件管理页 79）；追加 `sys_dict`/`sys_dict_data`：`file_business_type`(7 业务类型)/`file_access`(PUBLIC/PRIVATE)/`upload_status`(PENDING/CONFIRMED/FAILED/GC) 三个枚举字典 + `file_size_limit`/`file_type_whitelist` 两个配置型字典（label=业务类型 code，value=MB数/逗号分隔类型）。编号续 blog 之后从 79/15/61 起。**已用 mysql CLI 直接执行，建表+7菜单+5字典类型+27字典数据全部入库。**

**knowhub 模块代码（`com.knowhub.*` 顶层包）**
- `pojo/entity/FileObject.java`（extends BaseEntity，手写 getter/setter）；`enums/FileBusinessType`(7项,带默认access)/`FileAccess`/`UploadStatus`。
- `pojo/vo/UploadApplyVo`/`UploadTokenVo`/`DownloadVo`/`FileObjectVo`/`BindVo`；`pojo/quarry/FileQuarry`。
- `config/StorageProperties`（@ConfigurationProperties(prefix=storage) 收口连接参数与阈值）、`S3ClientConfig`（构造 S3Client + S3Presigner Bean，pathStyle=true）、`StorageConfigReader`（业务侧唯一入口：sizeLimitBytes/typeWhitelist/isContentTypeAllowed/publicBucketReadable，内部走字典，业务侧禁直接 DictUtil）、`SchedulingConfig`（@EnableScheduling，框架未开，本模块自带）。
- `mapper/FileObjectMapper` + `resources/mapper/storage/FileObjectMapper.xml`：动态列 insert、列表过滤查询、按主键查、动态列 update（confirm 回填/状态变更/bind/软删）、`findExpiredPending`/`findSoftDeleted` GC 扫描、`physicalDeleteFileObject`、`softDeleteByBizRef`。
- `service/FileService` + `impl/FileServiceImpl`：applyUploadToken（白名单+上限校验→生成 objectKey→insert PENDING→签 PutObject 预签名）、confirmUpload（HeadObject 核对真实值→CONFIRMED，不符置 FAILED）、getPublicUrl（公开桶直拼/预签名 GET）、getDownloadUrl（鉴权+签 GET 预签名带 attachment;filename）、quarryFile/getFileObjectInfo/bindBizRef/deleteFileObjects、gc（扫超时 PENDING + 软删行 → DeleteObject + 物理删）。当前用户提取、checkOwnerOrAdmin 仿 BlogServiceImpl。
- `controller/FileController`（8 个接口）：`POST /file/upload-token`、`POST /file/confirm/{id}`、`GET /file/public/{id}`（302，无鉴权）、`GET /file/download/{id}`、`GET /file/list`、`GET /file/{id}`、`PUT /file/bind`、`DELETE /file/{ids}`，仿 BlogController 的 @PreAuthorize/@Log/@Operation/Result.success 写法。
- `task/FileGcTask`：@Scheduled(fixedDelayString SpEL 读 storage.gc-interval-minutes，initialDelay 60s)，调 fileService.gc()。

**配置（rookie-admin/application.yml 追加，用户已解除禁令）**
- 末尾追加 `storage:` 段：endpoint/region/access-key/secret-key/bucket/path-style-access + upload/download 预签名有效期 + pending-ttl/gc-interval + public-bucket-readable，敏感值 `${ENV:默认}` 占位。

**前端配套**
- `rookie-ui/vite.config.ts` 的 `server.proxy` 追加 `/file` 代理（target 后端，不带 rewrite），解决 `<img src="/file/public/123">` 不走 axios 在 dev 环境 404 的问题；prod 需 nginx 同名转发（部署配套项）。

**SDK 依赖踩点**
- `software.amazon.awssdk:s3`（版本由根 pom 的 `bom:2.25.27` 统一管理）已含 `S3Presigner` 预签名类，**不需要** `s3-presigner` 独立 artifact（该 artifact 在 2.25.27 BOM 中不存在，单独声明会报版本缺失）。
- `presignGetObject` 的 `getObjectRequest(Consumer<GetObjectRequest.Builder>)` 是 Consumer，不能 return build()，写法为纯副作用配置 builder。

**校验**
- `./mvnw -q -pl rookie-admin -am compile` 通过（EXIT=0，knowhub + admin class 均生成）。
- `RustFsConnectTest#testFullPresignFlow` 先期跑通：建桶→签 PutObject 预签名→JDK HttpURLConnection 模拟前端 PUT 直传→HeadObject 核对→签 GET 预签名下载→SDK 直传/直读→清理，全链路 ✅。

**遵守约定**
- 未修改 `rookie-framework`/`rookie-system`/`rookie-common` 任何既有代码/配置（@MapperScan/springdoc/@EnableScheduling 全走 knowhub 模块自带配置类自注册，框架未开 @EnableScheduling 由本模块 SchedulingConfig 开启）；未动 `sql/rookie.sql`/`sql/knowhub-blog.sql`。
- 白名单/上限走字典、读取收口于 `StorageConfigReader`，业务侧禁直接 `DictUtil`，遵守「全局开关落地约定」。
- 字典 SQL 两表备注字段拼写：`sys_dict.remake` / `sys_dict_data.remark`（沿用 blog 踩点）。

## 2026-07-02

### 19:02 knowhub 博客/标签/文件三模块前端管理页落地

后端三模块（博客文章 /blog、受控标签 /tag、文件存储 /file）此前已落地，本次按现有系统管理页（notice/dict/dict-data）的页面风格补齐对应前端管理页，复用 rookie-ui 既有公共组件，不改任何原组件。路由由后端 `sys_menu` 树驱动，组件靠菜单 `path` 匹配 `src/views`，无需手写路由注册。

**类型与接口（新建 knowhub 模块目录）**
- `rookie-ui/src/types/api/knowhub/{blog,tag,file}.ts`：对齐后端 BlogVo/TagVo/FileObjectVo 及 BlogQuarry/FileQuarry/UploadApplyVo/UploadTokenVo/DownloadVo/BindVo/ReviewVo；TagRecord.status 为 number（0禁1启），标签列表不分页返回 `TagRecord[]`。
- `rookie-ui/src/api/knowhub/{blog,tag,file}.ts`：博客 8 个方法（getBlogPage/getBlogDetail/createBlog/updateBlog/deleteBlogs/publishBlog/revokeBlog/reviewBlog，toggleLike/toggleCollect 属读者侧不挂）、标签 5 个（getTagList 不分页返 `ApiResult<TagRecord[]>`/getTagDetail/createTag/updateTag/deleteTags）、文件 8 个（applyUploadToken/confirmUpload/getDownloadUrl/getFilePage/getFileDetail/bindBizRef/deleteFileObjects + buildFilePublicUrl 拼相对路径不调后端）。

**预签名直传工具与公共组件**
- `rookie-ui/src/utils/upload.ts`（新建，不触既有 utils）：`presignedUploadFlow` 封装「申请令牌→PUT 直传 RustFS→confirm」三步，直传用原生 XMLHttpRequest 支持 onprogress，带 Content-Type/Content-Length、不带 Token（预签名自带鉴权）；PUBLIC 回填 `/file/public/{objectId}` 供 <img> 直引；导出 buildFilePublicUrl 复用。
- `rookie-ui/src/components/FileUploadButton.vue`（新建公共组件）：文件管理页「上传文件」测试入口，弹窗选业务类型(file_business_type 字典)/访问语义(file_access 字典，按业务类型默认值回填)/选文件+进度，调 presignedUploadFlow，成功 emit uploaded。标注为**临时测试入口，后期可整块移除**，与父页仅通过 uploaded 事件耦合。

**权限常量**
- `rookie-ui/src/constants/systemPermissions.ts`：追加 `blog`(create/edit/delete/publish/revoke/review/info)、`tag`(create/edit/delete/info)、`file`(upload/download/delete/info) 三组双值数组，对齐 `sys_menu` 中 `knowhub:blog:*`/`knowhub:tag:*`/`knowhub:file:*` perm_key。SQL 里 `knowhub:file:review` 无后端对应接口，前端不挂该按钮。

**博客文章管理页 `rookie-ui/src/views/blog/`**
- `index.vue` + `config.ts`：列表/筛选（title/keyword/tagIds 多选/status 字典 blog_status/reviewStatus 字典 review_status/createBy/日期区间）/分页/新增/编辑/发布（status≠PUBLISHED 可见）/撤回（status=PUBLISHED 可见）/审核（status=PENDING_REVIEW 可见，独立审核弹窗收 pass/advice，驳回 advice 必填）/删除/只读详情。tagIds 在筛选与表单均用 `#field-tagIds` 插槽接管为 ElSelect multiple；coverUrl 用 `#field-coverUrl` 插槽接管为封面上传组件；content 走 SharedFormPanel 内置的 markdown 输入。表格不展示封面列（SharedTablePanel 单元格不支持 custom 插槽、不改原组件），封面仅在表单上传与详情弹窗展示。
- `components/BlogCoverUploader.vue`（页面组件）：封面上传，固定 businessType=BLOG_COVER/access=PUBLIC，预签名直传后回填 coverUrl；已有封面展示预览+重新上传/清除。
- `components/BlogDetailDialog.vue`（页面组件）：详情只读弹窗，复用 MarkdownPreview 渲染正文 + DictTag 渲染状态/审核状态 + 封面预览 + 元信息（作者/时间/标签/统计/审核信息），仿通知详情排版。
- `components/BlogReviewDialog.vue`（页面组件）：审核弹窗，单选通过/驳回 + 审核意见文本域，驳回必填，emit submit({blogId,pass,advice})。

**博客标签管理页 `rookie-ui/src/views/blog/tag/`**
- `index.vue` + `config.ts`：标签列表**不分页**（后端返全量 List），不传 pagination 给 SharedTablePanel（组件 total=0 不渲染分页条）；CRUD（add/edit/delete），status 为 int 用静态 options 0禁用/1启用（与 dict 页口径一致），sort 用 number 输入。照 dict/index.vue 结构。

**文件管理页 `rookie-ui/src/views/file/`**
- `index.vue` + `config.ts`：列表/筛选（businessType/uploadStatus/access 三字典 + createBy/日期区间）/分页/下载/详情/删除。顶部工具栏放 FileUploadButton（测试入口，uploaded 后刷新列表）。文件无编辑表单，SharedTablePanel 不传 form-visible。下载：PUBLIC 对象直接 window.open(/file/public/{id}) 走 302；PRIVATE 对象调 getDownloadUrlApi 拿预签名 downloadUrl 后 window.open。contentLength 用本地 formatFileSize 友好化 KB/MB。bizRefId 空值展示占位。
- `components/FileDetailDialog.vue`（页面组件）：详情只读弹窗，ElDescriptions 展示元数据 + DictTag 渲染 businessType/access/uploadStatus；PUBLIC 图片对象展示 /file/public/{id} 缩略图预览。

**遵守约定**
- 未修改 rookie-ui 任何既有组件（BaseCard/SearchFilterPanel/SharedTablePanel/SharedFormPanel/DictTag/MarkdownEditor/MarkdownPreview 等）、`base.css`/`main.css`、router、stores、utils 既有文件；仅 `systemPermissions.ts` 在既有结构内追加常量。新工具 `utils/upload.ts` 为新建文件不触既有 utils。
- 字典（blog_status/review_status/file_business_type/file_access/upload_status）登录后由 `stores/dict.ts` 自动预加载，页面配 dictKey 即用；封面对象走 `/file/public/{id}` 相对路径，dev 环境 `/file` 代理已在 vite.config.ts 配好。
- 未启动 dev server（README.dev.md 约定），仅做 type-check。
- 新增浮层（上传弹窗/审核弹窗/详情弹窗）均用 base.css 的 --rookie-* 变量适配深浅模式。

**校验**
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。
- 路由落位：菜单 path `/blog/index`→`views/blog/index.vue`、`/blog/tag/index`→`views/blog/tag/index.vue`、`/file/index`→`views/file/index.vue`，由 dynamicRoutes.ts 自动匹配，无需手写路由。

**待用户人工验证**：登录 admin → 侧边栏「博客管理」下文章/标签/文件三页走查列表/筛选/新增/编辑/删除/发布/撤回/审核/封面上传/文件上传/下载/详情，深浅模式各看一遍。本次无后端接口变更，`doc/knowhub-api.md` 无需更新。
