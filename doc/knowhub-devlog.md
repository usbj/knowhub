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

### 22:39 修复封面上传 CORS + 博客正文改 CSDN 风格全屏编辑页

用户反馈：添加博客表单封面上传不生效（文件不上传、无回显）。实测定位根因为 **CORS**——RustFS 端点 `http://100.82.86.85:9000` 与前端 dev origin 不同源，后端预签名返回的 `uploadUrl` 是绝对地址，浏览器 PUT 直传被 CORS 拦截（preflight OPTIONS 返 200 但无 `Access-Control-Allow-*` 头）。`<img>` 回显不受影响（img 不做 CORS 强制校验）。同时应用户要求把博客正文从内联 markdown 编辑器改为 CSDN 风格的独立全屏编辑页，编辑器内插图走和封面一样的预签名直传逻辑。

**后端：预签名 URL 改代理友好相对路径（knowhub 模块，不动 rookie）**
- `knowhub/.../service/impl/FileServiceImpl.java`：新增私有方法 `rewriteUrlForProxy(String)`，把预签名绝对 URL 的 endpoint 前缀替换为 `/rustfs`（如 `http://100.82.86.85:9000/knowhub/.../x.png?X-Amz-...` → `/rustfs/knowhub/.../x.png?X-Amz-...`），桶名/对象 key/查询串原样保留；url 不以配置 endpoint 开头时原样返回（切 OSS/内网直连不受影响）。
- `applyUploadToken` 返回的 `uploadUrl`、`getDownloadUrl` 返回的 `downloadUrl` 经 `rewriteUrlForProxy` 改写；`getPublicUrl`（PUBLIC 回显 302 Location）**不改**——`<img>` 跟随 302 拉 RustFS 绝对地址，无 CORS 问题。
- 效果：前端 PUT/GET 走当前 origin 经 `/rustfs` 代理转发到 RustFS，同源无 CORS，符合「后端不经字节流」的预签名直传设计。

**前端：vite 代理 + 上传组件 + 正文编辑器**
- `rookie-ui/vite.config.ts`：`server.proxy` 追加 `/rustfs` → `http://100.82.86.85:9000`，rewrite 去 `/rustfs` 前缀（dev 同源代理；prod 由 nginx 同名转发，部署配套项）。
- `rookie-ui/src/views/blog/components/BlogCoverUploader.vue`：重写「重新上传」按钮——改用隐藏 `<input type="file" ref>` + 按钮 `@click` 触发 `inputRef.click()`，`onchange` 拿 file 调预签名直传，修复原先裸按钮无文件选择反应的 bug；预览 `<img :src="/file/public/{id}">` 经 `/file` 代理 302 回显。
- `rookie-ui/src/views/blog/components/BlogContentEditor.vue`（新建页面组件）：全屏 `ElDialog` 双栏（左 `v-md-editor mode=edit` + 右 `v-md-preview` 实时预览，CSDN 风格）；监听 `@upload-image` 事件，对每个图片文件调 `presignedUploadFlow({ businessType:'BLOG_BODY', access:'PUBLIC' })`，成功后调 `insertImage({ name, url:'/file/public/{id}' })` 在光标处插入 `![name](/file/public/{id})`（支持工具栏图片按钮/拖拽/粘贴）；本地缓冲编辑、点「保存返回」才回写父层 content，取消则丢弃；`uploadImageConfig` 限图片/≤10MB。
- `rookie-ui/src/views/blog/config.ts`：`content` 字段从 `inputType:'markdown'` 改为 `inputType:'custom'`，表单由 `#field-content` 插槽接管。
- `rookie-ui/src/views/blog/index.vue`：`#field-content` 插槽渲染「编辑正文」按钮 + 正文摘要预览（取前 60 字去 markdown 符号），点击打开 `BlogContentEditor`（`v-model` 绑 `formModel.content`，`handleContentUpdate` 回写）；新增 `contentEditorVisible` 状态、`openContentEditor`/`handleContentUpdate`/`contentPreview`。引入 `ElButton`、`BlogContentEditor`。

**遵守约定**
- 未修改 rookie-ui 任何既有组件（`MarkdownEditor.vue` 保留原状，正文编辑器是**新建**组件直接用全局注册的 v-md-editor/v-md-preview，不改原组件）；`base.css`/`main.css`/router/stores 未动；后端只改 knowhub `FileServiceImpl` 一处 URL 改写 + `getDownloadUrl` 一处，未动 `rookie-*`。
- 正文图片与封面共用同一套 `utils/upload.ts` 的 `presignedUploadFlow`，上传→拿 id→回填 `/file/public/{id}` 逻辑统一。
- v-md-editor 的 `upload-image` 事件签名 `(event, insertImage, files)`，`insertImage({name,url})` 在光标处插入图片 markdown。

**部署配套项（重要）**
- prod nginx 需加 `/rustfs` 转发到 RustFS endpoint（与 `/api`、`/file` 并列）：`location /rustfs/ { proxy_pass http://<rustfs-endpoint>/; }`，否则生产环境浏览器 PUT/GET `/rustfs/...` 会 404。

**校验**
- `./mvnw -q -pl knowhub -am compile` 通过，EXIT=0。
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。

**待用户人工验证**：博客新增/编辑表单——封面选图→进度→上传成功→预览回显；正文点「编辑正文」→全屏双栏→工具栏图片/拖拽/粘贴图片→自动上传并插入 `![](/file/public/{id})`→预览栏显示图片→保存返回→保存博客→详情弹窗正文图片正常渲染。文件管理页上传入口同样恢复可用。`doc/knowhub-api.md` 同步更新 uploadUrl/downloadUrl 改 `/rustfs` 相对路径说明。

### 2026-07-03 PUBLIC 回显改后端中转字节流 + 上传状态链路排查

用户反馈：knowhub 图片回显打不通（上个会话误判为 CORS 改成 /rustfs 代理+302 仍未解决），且文件上传后列表状态不明确。本次定位回显真正根因为鉴权层（非 CORS），改为后端中转字节流方案；并对上传状态链路做排查诊断。

**后端：PUBLIC 回显改后端中转（knowhub 模块）**
- `knowhub/.../pojo/vo/PublicObjectStream.java`（新建）：PUBLIC 对象中转回显载体 DTO，含 contentType/contentLength/etag/`ResponseInputStream<GetObjectResponse>`，stream 生命周期由 controller try-with-resources 管理。
- `knowhub/.../service/FileService.java`：删 `getPublicUrl(Long)`，新增 `streamPublicObject(Long)` 返回 `PublicObjectStream`。
- `knowhub/.../service/impl/FileServiceImpl.java`：新增 `streamPublicObject` 实现（校验 PUBLIC+CONFIRMED，不满足抛 `ServiceException(403/404)`；用已注入的 `s3Client.getObject` 拉字节流，contentType 优先取元数据存的避免 RustFS 默认 octet-stream 裂图，contentLength/etag 取 S3 响应兜底；不加 `@Transactional`）；删 `getPublicUrl` 与 `buildDirectUrl`（`presignGet` 仍被下载用、`rewriteUrlForProxy` 仍被上传/下载用，均保留）。
- `knowhub/.../controller/FileController.java`：`getPublic` 从 `ResponseEntity<Void>`(302) 改写为 `ResponseEntity<StreamingResponseBody>`——try-catch `ServiceException` 按 code 映射 404/403/500，其它 Exception 兜底 404/500，**全部返回纯状态码空体不进 GlobalExceptionHandler**（@RestControllerAdvice 会包成 Result JSON 对 `<img>` 无效）；成功响应带 `Content-Type`/`Content-Length`/`Cache-Control`(7 天 immutable)/`ETag`/`Accept-Ranges:none`，body 内 `try-with-resources` 包 `ResponseInputStream` + `transferTo` 流式拷贝、close 归还 SDK 连接池；加 `Logger` 与相应 import。

**鉴权放行（rookie-framework，已获用户明确许可）**
- `rookie-framework/.../config/SecurityConfig.java`：`authorizeHttpRequests` 链在 `/login permitAll` 之后、`anyRequest().authenticated()` 之前新增 `.requestMatchers("/file/public/**").permitAll()`。根因：原配置下无 Token 的 `<img>` 请求被 `anyRequest().authenticated()` 兜底拦截、`AuthenticationEntryPointImpl` 返 401 JSON，图片拉不到——这才是回显打不通的真正原因（非 CORS）。`TokenVerifyFilter` 无 Token 时只不设 SecurityContext 不拒绝，permitAll 后无 Token img 请求可直达 controller，**无需动 TokenVerifyFilter/AuthenticationEntryPoint**。

**前端：仅注释清理（零功能改动）**
- `rookie-ui` 内 6 处注释把"302 到 RustFS"改为"后端中转字节流"：`api/knowhub/file.ts`、`views/file/index.vue`(2 处)、`views/file/components/FileDetailDialog.vue`、`views/blog/components/BlogDetailDialog.vue`、`views/blog/components/BlogContentEditor.vue`。`/file/public/{id}` 路径不变，响应从 302 变 200+字节流，`<img>`/`window.open` 自动适配，`buildFilePublicUrl`/vite `/file` proxy/nginx 转发规则均不动。

**上传状态链路排查诊断（任务2，未改代码）**
- 链路：`applyUploadToken`(insert PENDING) → 前端 PUT 直传 RustFS → `confirmUpload`(HeadObject 校验，通过置 CONFIRMED / 不通过置 FAILED)。`upload.ts` 的 `presignedUploadFlow` 在 PUT 成功后无条件调 `confirmUploadApi`，confirm 失败时 `http.ts` 统一弹错 + reject，前端 catch 仅 `console.error`。
- 诊断结论：链路状态机本身正确，**未发现状态设置 bug**。用户反馈的"列表有数据但状态未上传/未通过"最可能来自两种情况：① confirm 失败（网络抖动或 HeadObject 校验不通过）→ 行停在 PENDING(网络失败) 或 FAILED(校验不过)，对象已在 RustFS，用户看到"上传失败"弹窗但列表多了一条 PENDING/FAILED 行；② PENDING 行在 GC 清理前（默认 `pending-ttl-minutes:30`、`gc-interval-minutes:10`）会一直显示"待确认"。
- 待用户确认是否需要改进：前端在 PUT 成功但 confirm 失败时给更明确提示（如"文件已上传但校验未通过"）；或后端 confirm 的 HeadObject content-type 校验对 RustFS 实际返回值做兼容。本次按约定不擅自改上传链路代码。

**遵守约定**
- rookie-framework 仅改 SecurityConfig 一行 permitAll（已获用户明确许可），未动 TokenVerifyFilter/AuthenticationEntryPoint/AccessDeniedHandler；rookie-ui 仅改注释未动功能代码；其余改动均在 knowhub 模块内。
- 未启动 dev server（README.dev.md 约定），仅后端 compile + 前端 type-check。

**校验**
- `./mvnw -q -pl knowhub -am compile` 通过，EXIT=0。
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。

**待用户人工验证**：① `curl -i http://localhost:8080/file/public/{已确认PUBLIC图片id}`（不带 Token）应 200+`Content-Type: image/png`+字节流（非 401 JSON / 非 302）；② 登录态博客新增/编辑表单封面上传→预览回显、正文编辑器插图→预览渲染、详情弹窗封面/正文图片正常；③ 退出登录刷新同页面图片仍渲染（permitAll 生效）；④ `curl` 不存在 id 应 404 空体（非 `{code:500,...}` JSON）；⑤ 上传状态按诊断结论观察 PENDING→CONFIRMED 流转。`doc/knowhub-api.md` 同步更新 `/file/public/{id}` 响应为 200+字节流+状态码表。

### 2026-07-03 文件访问双模式（中转/直链）+ 地址由后端决定

用户诉求：当前前端依赖 vite/nginx `/rustfs` 代理转发到 OSS，迁 OSS 时需改前端代理配置，运维成本高；希望"前端访问 OSS 的地址由后端决定"，且预留后续从字典系统迁到系统设置的空间。本次落地文件访问双模式开关，后端按模式决定发给前端的链接形态，前端永远只认后端给的链接，迁 OSS 只改后端配置（字典 + yml endpoint），前端代码与 nginx 相对路径都不用动。

**后端：模式枚举 + 配置读取收口（knowhub 模块）**
- `knowhub/.../enums/FileAccessMode.java`（新建）：TRANSFER/DIRECT 枚举，code 与字典 `file_access_mode` 的 dict_data_value 一致。
- `knowhub/.../config/StorageConfigReader.java`：新增 `accessMode()`（读字典 `file_access_mode`，默认 TRANSFER）与 `directBaseUrl()`（读字典 `file_direct_base_url`，留空回退 yml endpoint）；加两个字典键常量。同 `BlogConfigReader` 同构——后续迁系统设置表时仅改本类内部实现（直接读设置值而非遍历字典列表），签名与调用方零改动。

**后端：链接按模式发 + 中转/代理接口（knowhub 模块）**
- `knowhub/.../service/FileService.java`：新增 `streamDownloadObject`（中转下载，PUBLIC+PRIVATE）、`proxyUpload`（后端代理转发上传）、`getPublicAccessUrl`（按模式返回 PUBLIC 回显链接）。
- `knowhub/.../service/impl/FileServiceImpl.java`：
  - `applyUploadToken` 的 uploadUrl 按模式：TRANSFER→`/file/proxy-upload/{objectId}`；DIRECT→预签名绝对 URL（host 用 directBaseUrl，`rewriteHostToDirect`）。
  - `getDownloadUrl` 的 downloadUrl 按模式：TRANSFER→`/file/proxy/{objectId}`；DIRECT→预签名绝对 URL。
  - 新增 `streamDownloadObject`（校验 CONFIRMED + PRIVATE 鉴权，s3Client.getObject 拉流，带 attachment;filename）。
  - 新增 `proxyUpload`（接收 InputStream，s3Client.putObject 写 OSS，再走 confirm 核对置 CONFIRMED）。
  - 新增 `getPublicAccessUrl`（TRANSFER→`/file/public/{id}`；DIRECT→`{directBaseUrl}/{bucket}/{objectKey}` 公开读直链）。
  - `rewriteUrlForProxy` 改名 `rewriteHostToDirect`（直链模式用：把预签名 URL host 替换为 directBaseUrl）。
- `knowhub/.../pojo/vo/PublicObjectStream.java`：加 `contentDisposition` 字段（PRIVATE 中转下载带 attachment;filename；PUBLIC 回显为 null）。
- `knowhub/.../controller/FileController.java`：新增 `GET /file/proxy/{id}`（中转下载，权限 knowhub:file:download，带 attachment 头）、`PUT /file/proxy-upload/{objectId}`（代理转发上传，权限 knowhub:file:upload，接 InputStream）、`GET /file/url/{id}`（取 PUBLIC 回显链接，无鉴权）；import jakarta.servlet.http.HttpServletRequest。

**前端：适配双模式链接（rookie-ui，不改既有组件）**
- `rookie-ui/src/utils/upload.ts`：`putToPresignedUrl` 按链接形态（相对/绝对）自动决定带不带 Token（相对=中转模式带 Token；绝对=直链模式不带 Token）；PUBLIC 上传成功后调 `/file/url/{id}` 取按模式回显链接（异常回退 `/file/public/{id}`）；文件头注释更新双模式约定。
- `rookie-ui/src/api/knowhub/file.ts`：新增 `getPublicAccessUrlApi`（GET /file/url/{id}）。
- `rookie-ui/src/views/file/index.vue`：`handleDownloadFile` 按链接形态分流（绝对 URL→window.open；相对路径→fetch 带 Token 取 blob 再 a.click() 下载，从 Content-Disposition 取文件名）；import USER_TOKEN_STORAGE_KEY。
- `rookie-ui/src/types/api/knowhub/file.ts`：UploadTokenRecord/DownloadRecord 注释更新双模式链接形态。
- `rookie-ui/vite.config.ts`：删除已无用的 `/rustfs` 代理（双模式下都不再使用，中转走 `/file/proxy-*`、直链走绝对 URL）；`/file` 代理注释更新。
- `rookie-ui/src/views/blog/components/BlogCoverUploader.vue`、`BlogContentEditor.vue`：注释把"直传 PUT 走 /rustfs"更新为"上传目标由后端按访问模式决定"。

**字典 SQL（新建独立增量脚本 sql/knowhub-storage-dual-mode.sql，不改原 knowhub-storage.sql）**
- 原因：knowhub-storage.sql 已在环境运行过，改原文件再跑会主键冲突；故新建增量脚本，已部署环境直接跑即可。
- 新增 sys_dict：id=20 `file_access_mode`（文件访问模式）、id=21 `file_direct_base_url`（直链模式 OSS 地址 base）。INSERT IGNORE 防重跑冲突。
- 新增 sys_dict_data：id=88/89 `file_access_mode`（transfer 中转/direct 直链，transfer 默认）、id=90 `file_direct_base_url`（占位值 `http://100.82.86.85:9000`，公网部署改为可达的 nginx/OSS 公网地址）。INSERT IGNORE 防重跑冲突。
- 更新 file_access 字典数据项 remark：`302 回显`→`后端中转回显`、`鉴权预签名下载`→`/file/download/{id} 或 /file/proxy/{id} 鉴权下载`（按 dict_key+dict_data_value 定位的 UPDATE，重跑同值幂等）。

**遵守约定**
- 未修改 rookie-ui 任何既有组件（BaseCard/SharedTablePanel 等）、base.css/main.css、router/stores；后端改动全在 knowhub 模块内；rookie-framework 本次未动（SecurityConfig 上轮已 permitAll 放行 /file/public/**，本轮新增的 /file/proxy-upload、/file/proxy 走各自 @PreAuthorize 鉴权，/file/url/{id} 走 anyRequest().authenticated() 兜底——前端调时带 Token，无鉴权需求问题留待用户确认是否也 permitAll）。
- 配置读取收口在 StorageConfigReader，预留系统设置迁移空间（同 BlogConfigReader）。
- 未启动 dev server（README.dev.md 约定），仅后端 compile + 前端 type-check。

**校验**
- `./mvnw -q -pl knowhub -am compile` 通过，EXIT=0。
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。

**待用户人工验证**：① 中转模式（默认）：封面上传→PUT /file/proxy-upload/{id} 带 Token→回显 /file/public/{id}；PRIVATE 下载→fetch /file/proxy/{id} 带 Token 取 blob 下载。② 切直链模式（字典 file_access_mode 改 direct + 配 file_direct_base_url 为可达 nginx/OSS 地址 + OSS 配 CORS）：上传→PUT 预签名绝对 URL 不带 Token；下载→window.open 预签名绝对 URL；回显→/file/url/{id} 返回直链。③ 迁 OSS 只改 yml storage.endpoint + 字典 file_direct_base_url，前端与 nginx 零改动。`doc/knowhub-api.md` 同步更新双模式说明 + 新增接口 5/6/7（中转下载/代理上传/取回显链接）。

## 2026-07-03

### 20:30 配置型设置从字典迁移到系统设置模块（sys_config）

rookie 层 commit `e03af35` 新增了独立的系统设置模块（`SysConfig`/`SysConfigUtil`/`SysConfigWarmUpRunner`/`/sys/system-config` 接口 + 前端管理页，对标字典系统，专门承载"后台可改、全站生效"的键值型配置）。knowhub 的两个配置读取收口类 `BlogConfigReader`、`StorageConfigReader` 此前走字典（`DictUtil`）读取 5 个配置型字典键，代码注释里已预留"将来迁系统设置仅改本类内部实现、签名零改动"——本次落实迁移，5 个配置项出现在「系统设置」管理页统一维护，业务调用方（`BlogServiceImpl`/`FileServiceImpl`）零改动。

**配置项映射**（5 条 sys_config，`is_system=1`/`status=1`，受内置项保护禁删/禁改键与类型/禁停用）：
- `blog_review_enabled` → `knowhub.blog.review_enabled`（BOOLEAN，初始 `false`）
- `file_access_mode` → `knowhub.file.access_mode`（STRING，初始 `transfer`）
- `file_direct_base_url` → `knowhub.file.direct_base_url`（STRING，初始 `http://100.82.86.85:9000`）
- `file_size_limit`（原 7 行字典）→ `knowhub.file.size_limit`（JSON 对象，key=业务类型 code，value=MB 数）
- `file_type_whitelist`（原 7 行字典）→ `knowhub.file.type_whitelist`（JSON 对象，key=业务类型 code，value=逗号分隔白名单）

**后端（仅改两个 ConfigReader 内部实现，签名不变）**
- `knowhub/src/main/java/com/knowhub/config/BlogConfigReader.java`：删 `DictUtil`/`SysDictData` import 改 `SysConfigUtil`；`DICT_KEY_REVIEW_ENABLED` → `CONFIG_KEY_REVIEW_ENABLED="knowhub.blog.review_enabled"`；`isReviewEnabled()` 改为 `SysConfigUtil.getBoolean(..., false)`（缓存缺失/停用/类型不符回落 false，与原字典缺失默认关闭语义一致）。
- `knowhub/src/main/java/com/knowhub/config/StorageConfigReader.java`：4 个 `DICT_KEY_*` → `CONFIG_KEY_*`；`accessMode()` 走 `SysConfigUtil.getString` + `FileAccessMode.ofCode`；`directBaseUrl()` 走 `SysConfigUtil.getString(...,null)`，空则回落 yml endpoint；`sizeLimitBytes`/`typeWhitelist` 走 `SysConfigUtil.getObject(..., Map.class, emptyMap)`，按 type.code 取值——数值统一 `((Number)v).longValue()` 取值防 Hutool 反序列化为 Integer/Long/BigDecimal 的 `ClassCastException`，白名单空串/缺失回落空列表（不限制）。`isContentTypeAllowed`/`publicBucketReadable`/`getProperties` 不动。

**SQL（新建独立脚本，不动上游 `sql/sys_config.sql`）**
- `sql/knowhub-sys-config-migration.sql`：① 插入 5 条 sys_config（config_id 4~8，`ON DUPLICATE KEY UPDATE` 幂等，初始值与原字典默认对齐）；② 清理 5 个孤儿配置型字典（`sys_dict_data` + `sys_dict` 按 dict_key DELETE，幂等）。**枚举型字典 `file_business_type`/`file_access`/`upload_status` 保留不动**（前端下拉 + 后端校验共用，与配置型用途不同）。前置依赖：需先跑 `sql/sys_config.sql` 建表。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置（遵守 `doc/README.dev.md`「rookie 框架代码修改禁令」）；改动全在 knowhub 模块内 + 新建 sql 脚本。
- 业务调用方 `BlogServiceImpl`/`FileServiceImpl` 零改动（方法签名不变）。
- 前端无感：这 5 个字典 key 未被 rookie-ui 的 useDict/下拉/DictTag 当渲染源（grep 确认），系统设置页已支持 JSON 类型 textarea 编辑，新设置项自动出现在列表里。
- `doc/README.dev.md`「全局开关落地约定」同步更新为"系统设置模块已就绪，全局开关/单值配置走 sys_config；多值枚举下拉仍走字典"。

**校验**
- `mvn -q -pl knowhub -am -DskipTests compile` 通过，EXIT=0（BlogConfigReader/StorageConfigReader class 均重新生成）。
- 未启动 dev server，未跑 SQL（由用户在合适时机执行 + 业务验证）。

**待用户人工验证**：① 跑 `sql/sys_config.sql`（若未跑）→ 跑 `sql/knowhub-sys-config-migration.sql` → `select config_key,... from sys_config;` 应 8 条（3 rookie 内置 + 5 knowhub）；孤儿字典 dict_key 查询应为空，枚举型 3 条仍在。② 启动后端看日志"系统设置缓存预热完成，共加载 8 条启用设置项"。③ admin 进「系统设置」页（确认 `sql/sys_config.sql` 第 124-128 行 admin 授权菜单 93-99 的注释已手动放开，否则看不到该页）能看到 5 条 knowhub 项，JSON 项可 textarea 编辑。④ 业务：`knowhub.blog.review_enabled=true` 发布走审核、改回 false 直通；改 `knowhub.file.size_limit` JSON 里 BLOG_COVER 上限后上传校验即时变化；切 `knowhub.file.access_mode=direct` 配 direct_base_url 后链接形态切直链。⑤ 缓存生效：设置页编辑保存即时生效（editSysConfig 写库后立即 setConfig 重写 Redis），绕过接口直接改 DB 才需点「刷新缓存」(POST /sys/system-config/refresh 清空重预热)。

### 21:40 博客/标签/文件管理页前端目录迁入 views/knowhub/

把 knowhub 三个后台管理页从前端散落位置统一归到 `views/knowhub/` 下，与后端模块命名（`knowhub-blog` 等）和权限键前缀（`knowhub:*`）对齐，结构更清晰。标签从原 `blog/tag` 提到与 blog/file 平级。

**前端目录迁移（git mv 重命名，保留历史；页面内 `./` 相对引用不变）**
- `rookie-ui/src/views/blog/` → `rookie-ui/src/views/knowhub/blog/`（`index.vue`、`config.ts`、`components/BlogReviewDialog`/`BlogDetailDialog`/`BlogCoverUploader`/`BlogContentEditor`）
- `rookie-ui/src/views/blog/tag/` → `rookie-ui/src/views/knowhub/tag/`（`index.vue`、`config.ts`，从 blog 下提至平级）
- `rookie-ui/src/views/file/` → `rookie-ui/src/views/knowhub/file/`（`index.vue`、`config.ts`、`components/FileDetailDialog`）
- 11 个文件均以 git `R`（rename）状态移动，旧 `views/blog`/`views/file` 空目录已删；页面内部全是同目录 `./` 引用，迁移后无需改 import。

**SQL 菜单 path（组件定位）同步**——`menu.path` 字段供 `dynamicRoutes.ts` 的 `resolveViewComponent` 匹配 `views/{path}/index.vue`，目录迁了必须同步，否则路由命中占位页：
- `sql/knowhub-blog.sql` menu 64 文章：`/blog/index` → `/knowhub/blog/index`；menu 73 标签：`/blog/tag/index` → `/knowhub/tag/index`（顶部注释树同步）。
- `sql/knowhub-storage.sql` menu 79 文件：`/file/index` → `/knowhub/file/index`。
- **菜单 `route` 字段（路由路径 `blog`/`tag`/`file`，浏览器地址栏）不动**——只改组件定位 `path`，不改地址，用户书签/收藏不受影响。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置；改动全在 rookie-ui 的 knowhub 业务视图层 + knowhub sql 脚本。
- `dynamicRoutes.ts` 的 `import.meta.glob('../views/**/*.vue')` 天然覆盖新路径，无需改路由注册逻辑。

**校验**
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0，迁移无 import 断裂。
- 未启动 dev server（README.dev.md 约定）。

**待用户人工验证**：① 已部署环境需在 `sys_menu` 表把 menu 64/73/79 的 `path` 改为 `/knowhub/...`（或重跑对应 sql 脚本的该行）后清前端缓存重新登录，否则侧边栏点文章/标签/文件会落到占位页。② 三个页面功能走查一遍（列表/筛选/CRUD/上传/审核/详情）确认无回归。

### 22:05 正文编辑器启用「上传本地图片」按钮（替换原仅"添加链接"）

用户反馈：博客正文全屏编辑器（`BlogContentEditor`）工具栏的「插入图片」点开只有「添加图片链接」子项，缺"选本地文件上传"入口。

**根因**：v-md-editor@2.3.18 内置 `image` 工具组本就有两个子项——`image-link`（添加图片链接）与 `upload-image`（上传本地图片，点击调 `$refs.uploadFile.upload()` 选图 → `emitUploadImage` 触发 `@upload-image` 事件），但组件默认 `disabledMenus=['image/upload-image']` 把上传子项禁用了，所以只看到"添加链接"。而 `BlogContentEditor` 早已接好 `@upload-image="handleUploadImage"`（BLOG_BODY + PUBLIC 预签名直传 + `insertImage` 光标处插入 `![name](/file/public/{id})` 并即时回显），上传逻辑完整，只是按钮被默认禁用没暴露出来。

**改动（`rookie-ui/src/views/knowhub/blog/components/BlogContentEditor.vue`，1 行 + 注释）**
- `<v-md-editor>` 加 `:disabled-menus="[]"`，清空默认禁用列表 → 启用「上传本地图片」子项。工具栏「插入图片」分组现在同时有「添加图片链接」「上传本地图片」两个子项。
- 复用已传的 `:upload-image-config="{ accept:'image/*', maxFileSize:10*1024*1024 }"`——按钮 / 拖拽 / 粘贴三条路径共用同一套 accept 与体积上限（10MB，与 BLOG_BODY 配置一致），无需新增上传逻辑。
- 顶部注释更正：原来写"工具栏图片按钮触发 upload-image"与默认禁用事实不符，改为准确描述「上传本地图片」子项 + 拖拽 + 粘贴均触发 upload-image，并注明 `disabled-menus` 启用原因。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置；只动 knowhub 业务组件。
- 不改 `@upload-image` 处理器、不改预签名直传流程、不改 `utils/upload.ts`——纯启用被默认禁用的内置按钮，复用既有上传链路。

**校验**
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。
- 未启动 dev server（README.dev.md 约定）。

**待用户人工验证**：进博客新增/编辑 →「编辑正文」→ 工具栏「插入图片」分组下点「上传本地图片」→ 选图 → 上传完成后光标处插入 `![文件名](/file/public/{id})` 并在左栏编辑区/右栏预览即时回显；拖拽图片到编辑区、粘贴图片同样走上传链路。上传中 footer 显示「图片上传中…」且「保存返回」按钮禁用。

## 2026-07-04

### 2026-07-04 07:00 修复表单输入回弹 + 中转上传 confirm 404 + confirm 参数传递 bug

用户反馈三个问题：① 博客正文编辑后标题输入框"打了字失焦后消失回弹旧值"；② 中转模式上传文件前端一直卡在"上传中"，最后 confirm 报 `对象未上传或不存在`（HeadObject 404）；③ `confirmUploadApi` 的 bizRefId 没作为 query 参数传。

**① 表单输入回弹（SharedFormPanel.vue，根因：structuredClone 对 reactive 嵌套数组抛错）**
- 根因：`updateFieldValue` 用 `structuredClone(toRaw(props.modelValue))` 深拷贝。`toRaw` 只去 modelValue 外层 reactive proxy，**嵌套字段 `tagIds`（数组）仍是 reactive proxy**。`structuredClone` 遍历到嵌套 proxy 抛 `[object Array] could not be cloned`，导致 `updateFieldValue` 抛错、`emit('update:modelValue')` 不执行、父层 `formModel` 不更新；native input value 已变（显示新内容），失焦时 ElInput 受控同步 native value ← 旧 modelValue，表现"打了字失焦后消失"。preview 实测：ElInput 正确 emit `update:modelValue`，但 SharedFormPanel 的 `updateFieldValue` 内 `structuredClone` 抛错（patch 该函数抓到 err），SharedFormPanel 未再 emit，父层 formModel.title 始终旧值。
- 修复：`structuredClone(toRaw(...))` 改为 `JSON.parse(JSON.stringify(props.modelValue))`。表单数据都是可 JSON 序列化的（字符串/数字/数组/普通对象），JSON 方案对 reactive proxy 安全（`JSON.stringify` 读 proxy 真实值）且深拷贝嵌套结构。同步删 `toRaw` import（不再使用）。
- 影响面：SharedFormPanel 是公共组件，本修复同时治好用户/博客/通知/标签等所有用它的表单的输入回弹隐患。
- preview 实测验证：真实键盘输入 + IME composition 事件序列输入 + 失焦，`formModel.title` 均正确更新、不再回弹；正文编辑器输入保存后再输标题也正常。

**② 中转模式上传 confirm 404（FileServiceImpl.proxyUpload，根因：流式读取 + contentLength 不稳）**
- 根因：`proxyUpload` 用 `RequestBody.fromInputStream(request.getInputStream(), request.getContentLengthLong())` 流式写 OSS。经 vite proxy 转发后 `request.getContentLengthLong()` 可能返回 -1（chunked 传输），且 `request.getInputStream()` 经 `RequestCachingFilter` 的 `ContentCachingRequestWrapper` 包装，流式读取与 AWS SDK 分块配合不稳，导致 putObject 写入空/截断对象 → 内部/前端 confirm HeadObject 404。
- 修复：`proxyUpload` 先 `in.readAllBytes()` 把请求体全量读入 byte[]，再用 `RequestBody.fromBytes(bytes)` 写入，`contentLength` 用 `bytes.length`（实际读到）。contentType 优先用请求头声明的，缺失回退元数据存的。彻底回避流式读取与 contentLength=-1 的坑（图片等小文件进内存可接受，大文件再走流式优化）。
- 同时去掉 `proxyUpload` 末尾的内部 `confirmUpload(objectId, null)` 调用：留给前端统一调 `POST /file/confirm/{id}` 走 HeadObject 核对并置 CONFIRMED。否则中转模式下后端内部 confirm 已置 CONFIRMED，前端三步流程第 3 步会因"状态非待确认"报错，与直链模式流程不一致。proxyUpload 现仅写 OSS 返回 true，对象已落 OSS，前端 confirm 时 HeadObject 即可命中。

**③ confirmUploadApi 参数传递 bug（api/knowhub/file.ts）**
- 根因：`post('/file/confirm/${id}', { params: { bizRefId } })` —— `post` 的第二个参数是 `data`（请求体）而非 config，`{params:...}` 被当 body 发出，bizRefId 没作为 query 参数传到后端 `@RequestParam bizRefId`。
- 修复：改为 `post('/file/confirm/${id}', undefined, { params: { bizRefId } })`，第三个参数才是 config，params 正确作为 query 附加到 URL。

**直链模式上传失败（配置问题，非代码 bug）**
- 用户实测：直链模式 PUT 到 `http://100.82.86.85:9000/...` 失败。`100.82.86.85:9000` 是内网 OSS 地址，公网浏览器不可达，且预签名 PUT 还需 OSS CORS 放行浏览器 origin。这是 `knowhub.file.direct_base_url` 配置问题（应配 nginx 公网反代域名，不是内网 OSS endpoint），非代码 bug。建议：公网部署把 `direct_base_url` 改为 nginx 公网反代域名，或保持 `access_mode=transfer` 走中转。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置；改动在 knowhub 模块（FileServiceImpl）+ rookie-ui 公共组件（SharedFormPanel）+ knowhub api（file.ts）。
- SharedFormPanel 是 rookie-ui 公共组件，本修为修 bug（输入回弹），非扩展功能。

**校验**
- `mvn -q -pl knowhub -am -DskipTests compile` 通过，EXIT=0。
- `rookie-ui` `npm run type-check`（vue-tsc --build）通过，EXIT=0。
- preview 启动 dev server 实测标题输入：真实键盘 + IME + 失焦 + 正文编辑后均正常更新 formModel，不再回弹。

**待用户人工验证**：① 重启后端（FileServiceImpl 改动需重新编译启动）→ 中转模式上传图片/文件，确认不再卡在"上传中"、confirm 成功、对象可回显。② 博客新增/编辑：正文编辑后输标题、失焦、再编辑，确认标题不回弹；其他用 SharedFormPanel 的表单（用户/通知/标签）也测一遍输入。③ 直链模式若需用，把 `knowhub.file.direct_base_url` 配为 nginx 公网反代域名后再测。

### 17:20 中转上传写坏字节根因 — request.getInputStream() 在 filter chain 中被上游消费，改用 @RequestBody byte[] 读取

接上一个条目「② 中转上传 confirm 404」：当时把 `proxyUpload` 从 `RequestBody.fromInputStream(in, contentLength)` 改为 `in.readAllBytes()` + `RequestBody.fromBytes(bytes)` 并去掉内部 confirm。改完后"上传不再卡住、confirm 成功、对象 CONFIRMED"，**但回显出来是裂图**，OSS 里上传的对象本体打不开。本次定位真因并彻底修复。

**现象（实测取证）**
- 后端 `/file/public/{id}` 实测返回 HTTP 200、`Content-Type` 正确、`Content-Length` 与 DB `content_length` 完全一致（29558 / 457598 / 461733 等都精确匹配），说明回显链路（`streamPublicObject` → `s3Client.getObject` → `transferTo`）**忠实地把 OSS 里的字节原样吐出**，回显本身无瑕疵。
- 但回写字节的头都不是合法图片头：JPEG 应 `ff d8 ff`，实测却是 `28 eb 07 1a 35 ef fd 72`；PNG 应 `89 50 4e 47 0d 0a 1a 0a`，实测却 `e1 9c c1 d0 ...`。尾反而是合法的 `ff d9`（JPEG EOI）/ `49 45 4e 44 ae 42 60 82`（PNG IEND）。
- 多次上传不同图，**`readAllBytes` 读到的字节数恒比请求头声明 `Content-Length` 少 4135 字节**（33693→29558、434833→430698、461733...每次都缺 4135），且头部那串垃圾字节 `28 eb 07 1a...` 在两次上传间**完全一致**。

**根因**
- 缺的恰好是 body 的**前 4135 字节**，尾还在 → controller 拿到的是 `[body[4135:]]` 中后段。`s3Client.putObject(fromBytes)` 忠实写出这残缺字节 → OSS 对象 size 对（写进的就是 `bytes.length`）但**内容是 body 中段、缺头** → 浏览器按 image/jpeg/png 解码必然失败 → 裂图。`confirmUpload` 的 HeadObject 只校验 size/contentType，size 对就放行，所以"上传成功 + CONFIRMED" 但对象是坏的。
- 上一个条目把根因归咎于"流式读取 + contentLength=-1 不稳"**是错的**：`readAllBytes` 已经回避了流式坑，却仍读残缺，因为字节在到达 `request.getInputStream()` 时**就已经被上游 filter 消费了前段**。`RequestCachingFilter`（`ContentCachingRequestWrapper`）+ `@Log` 切面（`isSaveRequestData=true`，`buildOperLog` 在 `proceed` 前调 `getContentAsByteArray`）+ Spring Security 多层 wrapper 的组合下，原始 servlet 流被某层消费了固定前段（具体哪个 filter 在框架/Spring 自带层，未深追；4135 字节固定指向同构请求的固定前置消耗）。
- 旧版（9bc2a9a）用 `fromInputStream(in, contentLength)`，SDK 期望读声明长度但流已残缺，读到 EOF 抛异常 → 旧版表象是"上传失败"；改成 `readAllBytes`+`fromBytes` 后残缺字节被静默写进 OSS → 表象变成"成功但坏图"。**根因一直在读取路径，不在写入逻辑，也不在回显。**

**修复（FileController.proxyUpload，knowhub 模块内 1 处）**
- controller 签名加 `@RequestBody(required = false) byte[] body`，用 Spring MVC 的 `ByteArrayHttpMessageConverter` 一次性读 body 为 `byte[]`，再包 `ByteArrayInputStream` 传给 service。`@RequestBody` 由 DispatcherServlet 在 controller 之前读 body，此时 body 尚未被任何 filter 后置消费，拿到的是完整原始字节（实测 body.len=461733=declaredCL，head=`89 50 4e 47 0d 0a 1a 0a` PNG 签名 + IHDR，正常）。
- `body==null` 兜底回退 `request.getInputStream()`（@RequestBody converter 未匹配时的保底，正常不会走到）。
- service `proxyUpload` 逻辑不变（`readAllBytes` + `fromBytes` + 不内部 confirm），仅修正注释里"wrapper 流式读不稳"的错误前提为"上游 filter 消费 stream，由 controller @RequestBody 绕开"。
- 业务调用方、前端 `presignedUploadFlow`、`/file/proxy-upload` 接口签名对外不变，零改动。

**为什么直链模式也裂图**
- 直链模式 `<img>` 直连 `http://100.82.86.85:9000/{bucket}/{objectKey}`，匿名 GET 实测返回 **403**（OSS 桶未配公开匿名读策略，`publicBucketReadable=true` 只是后端语义标志）。`100.82.86.85:9000` 是内网 OSS endpoint，公网浏览器也不可达。所以直链模式裂图是**配置问题**（桶未公开读 + direct_base_url 是内网地址），非代码 bug。需用直链模式时：把桶配成公开读、`knowhub.file.direct_base_url` 改为 nginx 公网反代域名。中转模式现在已可用，推荐保持 `access_mode=transfer`。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置（根因虽在框架 filter chain，但未改框架，而是在 knowhub controller 层用 `@RequestBody` 绕开）。
- 改动只在 knowhub 模块（FileController.proxyUpload 签名 + 注释、FileServiceImpl.proxyUpload 注释）。

**校验**
- `mvn -q -pl knowhub -am -DskipTests compile` 通过，EXIT=0。
- 临时诊断日志（`[DIAG proxyUpload-entry/preRead/body]`、`[DIAG proxyUpload]`）已全部清除。
- 后端实测 `PUT /file/proxy-upload/{id}` 中转上传一张 PNG → `body.len=461733=declaredCL`、head 为合法 PNG 签名 → 写入 OSS 对象正确 → `/file/public/{id}` 回显正常显示。用户确认能正常回显。

**待用户人工验证**：① 历史用旧 bug 写坏的对象（objectId=16/19/22/23/24 等）**无法修复**（OSS 里字节已坏），需删除这些 file_object 行让 GC 清理 OSS 对象。② 新上传的图用中转模式走完整流程（申请令牌→PUT proxy-upload→confirm→/file/public 回显），确认列表/详情/封面/正文插图都能正常显示。③ 直链模式若需用，先配桶公开读 + direct_base_url 公网域名。

### 2026-07-04 双模式回显落地 — 新增 /file/resolve/{id} 解析接口，上传回填统一存稳定引用

**背景**：此前"双模式"名存实亡。`presignedUploadFlow` 上传时调 `getPublicAccessUrl(objectId)` 拿到**按当时访问模式**算出的真实链接（中转→`/file/public/{id}`，直链→`{directBaseUrl}/{bucket}/{objectKey}`），直接回填到 `coverUrl`/正文 markdown 并**原样落库**。渲染时 `<img :src="blog.coverUrl">` 直接用库里存的字符串，**不再调后端解析** → 库里存的是"上传那一刻的模式对应的链接"，被固化 → 切模式后历史数据不跟着切，双模式对存量失效。用户最初设计意图是"库里统一存稳定解析引用，渲染时命中接口由后端按当前模式动态分发"，本次落实回来。

**方案（与用户确认）**：新增后端纯分发接口 `GET /file/resolve/{objectId}`，两种模式都只 302、不吐字节；上传后 `coverUrl`/正文 markdown/文件预览统一存 `/file/resolve/{id}` 稳定引用，模式分发收到 resolve 接口里，切模式历史数据自动跟着切。

**改动（knowhub 模块 2 处 + 前端 3 处）**
- `FileController`：新增 `@GetMapping("/resolve/{objectId}")`，`permitAll` 无鉴权（与 `/file/public/**` 同，供 markdown `<img>` 直引），不加 `@Log`（高频回显，记日志刷屏且无业务意义）。返回 `ResponseEntity<Void>` + `HttpStatus.FOUND`(302) + `Location` 头；校验失败（service 返 null / ServiceException）映射 404/403 纯状态码空体，**绝不冒泡 `GlobalExceptionHandler`**（`@RestControllerAdvice` 会包成 Result JSON，对 `<img>` 无效）。调 `fileService.resolvePublicUrl(objectId)` 拿 Location。
- `FileService` / `FileServiceImpl`：
  - 新增 `resolvePublicUrl(Long objectId)`：查元数据→校验 `access=PUBLIC`+`uploadStatus=CONFIRMED`（不通过返 null，由 Controller 映射 403/404）→按 `accessMode()` 分发：`TRANSFER` 返 `/file/public/{objectId}`（相对路径作 302 Location，浏览器按当前页 origin 解析同源命中 vite/nginx 代理）；`DIRECT` 桶公开读（`publicBucketReadable=true`）返 `{directBaseUrl}/{bucket}/{objectKey}`（不带签名永久有效），桶私有返 `rewriteHostToDirect(presignGet(fileObject,null).url())`（带签 GET 预签名，私有对象的兜底）。
  - 改造 `getPublicAccessUrl`：**统一返回 `/file/resolve/{objectId}`**，不再按模式分。语义从"按模式真实链接"变为"稳定解析引用"；对象不存在/不可访问的分支也返此引用（由 resolve 接口映射 403/404，不抛异常避免影响 VO 填充整页）。详情接口顺带返回的 `coverUrl/previewUrl` 自动变成 `/file/resolve/{id}`，博客/文件 service 字段填充零改动（透传 `getPublicAccessUrl` 返回值，无二次改写）。
- 前端 `api/knowhub/file.ts`：新增 `buildFileResolveUrl = (id) => '/file/resolve/${id}'`，与 `buildFilePublicUrl` 并列（后者保留作直连中转字节流接口的底层拼装）。
- 前端 `utils/upload.ts`：`presignedUploadFlow` 第 4 步 `publicUrl` 一律 `buildFileResolveUrl(token.objectId)`，**删除调 `getPublicAccessUrlApi` 的逻辑**（模式分发收到 resolve 接口，上传回填不再需要后端按模式给链接）；import 同步。`BlogCoverUploader`/`BlogContentEditor` 只消费 `result.publicUrl`，零改动自动拿到 resolve 地址。
- 前端 `FileDetailDialog.vue`：`previewUrl` 从 `buildFilePublicUrl` 改为 `buildFileResolveUrl`，import + 注释同步。

**不需要改**：`/file/public/{objectId}` 接口与 `streamPublicObject`（保留作字节流出口，resolve 中转分支 302 到它）；博客/文件 service 字段填充；`rookie-admin` yml、`StorageProperties`、桶策略；系统设置项 `knowhub.file.access_mode`/`direct_base_url`/`publicBucketReadable`（复用现有）。

**关于直链上传传不成功（本次不处理）**：直链模式上传 PUT 的是绝对 URL 指向 `directBaseUrl`，当前是内网 `100.82.86.85:9000`，浏览器到不了 → `xhr.onerror` → 进度跳一下就没、元数据卡 PENDING。与跨域无关（网络不可达，非 CORS）。`directBaseUrl` 是 `sys_config` 设置项，系统设置页改值即时生效。解法二选一留作后续：①配公网 nginx 反代 OSS、`directBaseUrl` 填公网域名；②开发期 vite 加 `/oss`→`http://100.82.86.85:9000` 代理、`directBaseUrl` 填 `/oss`（需 `rewriteHostToDirect` 能吐相对路径、302 Location 相对路径浏览器按当前页 origin 解析，可行性单独验证）。本次只做回显 resolve 接口，上传直链模式单独留口子。

**遵守约定**：未修改任何 `rookie-*` 代码/配置；改动只在 knowhub 模块（FileController/FileService/FileServiceImpl）+ rookie-ui 的 knowhub 二开文件（api/utils/views/knowhub）。

**校验**：`mvn -q -pl knowhub -am -DskipTests compile` 通过，EXIT=0。

**待用户人工验证**：① 启动后端，中转模式（`access_mode=transfer`）浏览器直访 `GET /file/resolve/{已确认的PUBLIC id}` → 应 302、Location: `/file/public/{id}`，浏览器跟随后显示图片。② 切直链模式（系统设置页改 `access_mode=direct`+配可达 `directBaseUrl`/开发期 vite `/oss` 代理+桶公开读）→ 同 id 应 302、Location: `{directBaseUrl}/{bucket}/{objectKey}`，显示图片。③ 博客封面上传后查 `cover_url` 库值应为 `/file/resolve/{id}`；切模式后**同一记录**回显自动跟着切（双模式生效关键点）。④ 正文插图 markdown 存 `![](/file/resolve/{id})`、文件详情预览 `<img src="/file/resolve/{id}">` 均正常。⑤ 历史 `cover_url=/file/public/{id}` 旧记录回显仍可用（`/file/public` 接口保留），只是不享受模式切换——测试数据可弃，不写迁移脚本。

### 2026-07-04 博客审核流水模块落地（审核历史 + 状态机 + 回避 + author_id）

博客审核雏形已落地（主表审核字段 + publish/review/revoke + 审核开关 `knowhub.blog.review_enabled`），但审核结果只覆盖主表只存最后一次、无历史可溯，且无状态机校验、无审核员回避、无用户ID稳定锁定。本次按 `doc/blog/blog-review-flow-design.md` 定稿方案补审核流水表 + blog 加 author_id + 审核动作字典 + 状态机/回避/流水写入 + 审核历史接口，并用「前后台审核记录展示」代替通知闭环（rookie 当前只有分组通知、无个人通知通道）。

**SQL（新建独立脚本 `sql/knowhub-blog-review-log.sql`，不动 knowhub-blog.sql）**
- 建 `blog_review_log` 流水表：`review_log_id`(PK，不用 log_id 避免与 sys_oper_log 操作日志混淆)/`blog_id`/`action`(SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH)/`operator_id`(userId 稳定锁定)/`operator`(username 快照)/`role`(AUTHOR/REVIEWER/SYSTEM 按动作类型定，非系统角色)/`advice`/`create_time` + 索引 `idx_brl_blog_time`(前台按文章查时间线)、`idx_brl_operator_time`(后台按审核员查记录)。只追加不改不删，记全量历史；主表审核字段保留为「当前快照」。只记动作不记状态前后（from/to_status 去掉，action 隐含转移语义）。
- `ALTER blog ADD author_id`（bigint，作者 userId，与 create_by(username) 互补，前台展示昵称 join sys_user 稳定）+ 按 create_by(username) 回填 user_id（`UPDATE blog JOIN sys_user ON create_by=username SET author_id=user_id`）；用 information_schema 判列存在再 ALTER，幂等。
- 新增字典 `blog_review_action`（5 值，dict_id=22，dict_data_id 91-95，INSERT IGNORE 幂等），供前端 DictTag 渲染中文。

**后端数据层（knowhub 模块，全 `com.knowhub.*`）**
- `enums/ReviewAction.java`（新建）：5 动作枚举，每个带 role（SUBMIT/REVOKE→AUTHOR、APPROVE/REJECT→REVIEWER、PUBLISH→SYSTEM）。
- `pojo/entity/BlogReviewLog.java`（新建）：流水实体，含 operatorNickname 字段（非表字段，由 listByBlogId left join sys_user 带出 nick_name 承接）；不继承 BaseEntity（流水无 updateBy/updateTime）。
- `mapper/BlogReviewLogMapper.java` + `resources/mapper/blog/BlogReviewLogMapper.xml`（新建）：`insertReviewLog`（动态 advice）+ `listByBlogId`（left join sys_user 带昵称，按 create_time 升序，LEFT JOIN 确保用户删/改名时流水行不丢）。
- `pojo/vo/ReviewLogVo.java`（新建）：流水出参，含 operatorNickname。
- `pojo/entity/Blog.java`：加 authorId 字段 + getter/setter + 构造器参数。
- `pojo/vo/BlogVo.java`：加 authorId 字段 + getter/setter。
- `resources/mapper/blog/BlogMapper.xml`：resultMap 加 author_id 映射、addBlog insert 加 author_id 列与值（动态 if）。

**后端业务层（knowhub 模块）**
- `service/BlogService.java`：加 `listReviewLog(Long blogId)` 接口；editBlogInfo/publishBlog/revokeBlog 注释补状态机/回避说明。
- `service/impl/BlogServiceImpl.java`：
  - 注入 `BlogReviewLogMapper`。
  - `addBlogInfo`：写入 `blog.setAuthorId(userInfo.getUserId())`。
  - `editBlogInfo`：加 PUBLISHED 禁止编辑校验（"已发布文章请先撤回再编辑"，防绕过审核改已发布内容）。
  - `publishBlog`：加状态机前置校验（仅 DRAFT/REJECTED/REVOKED 可发布，PUBLISHED/PENDING_REVIEW 报错）；按开关写流水（SUBMIT/AUTHOR 或 PUBLISH/SYSTEM）。
  - `revokeBlog`：加状态机前置校验（仅 PUBLISHED 可撤回）；reviewStatus 清 NONE；写流水 REVOKE/AUTHOR。
  - `reviewBlog`：加状态机前置校验（仅 PENDING_REVIEW 可审核）+ 审核员回避（`exist.getAuthorId().equals(userInfo.getUserId())` 报"不能审核自己提交的文章"）；写流水 APPROVE/REJECT + REVIEWER。
  - 新增 `listReviewLog`：调 mapper 查流水，BeanUtil 拷贝到 ReviewLogVo（operatorNickname 字段名一致自动带出）。
  - 新增私有 `writeReviewLog(blogId, action, operator, advice)`：写流水，失败 catch 吞异常仅 log（状态优先、历史容错，不阻断主流程）。
  - 新增私有 `notifyReviewResult(blog, action, advice)`：**预留空实现**，待 rookie 支持个人通知后接入 SysNoticeService，签名零改动；当前前后台展示已代替通知闭环。
- `controller/BlogController.java`：新增 `GET /blog/review-log/{blogId}`（权限 `knowhub:blog:info`），返 `List<ReviewLogVo>`。

**前端（rookie-ui，不改既有组件）**
- `api/knowhub/blog.ts`：加 `getReviewLogApi(blogId)` → `get<ApiResult<ReviewLogRecord[]>>`。
- `types/api/knowhub/blog.ts`：加 `ReviewLogRecord` 接口；`BlogRecord` 加 `authorId?: number`。
- `views/knowhub/blog/components/BlogDetailDialog.vue`：加「审核历史」折叠区——watch visible+blogId 拉取 getReviewLogApi，ElCollapse + 时间线（DictTag 渲染 blog_review_action + operatorNickname/时间 + advice），无历史时 ElEmpty 占位；深浅模式用 --rookie-* 变量。

**文档**
- 新建 `doc/blog/blog-review-flow-design.md`（审核流程设计定稿：表/状态机/数据流向/展示闭环/落点/扩展余地）。
- 新建 `doc/blog/blog-front-review-display.md`（前台审核时间线展示功能细节补充，待前台开发落地）。
- `doc/knowhub-api.md`：更新日志加审核流水条目；博客模块加 8.1 review-log 接口详情；发布/撤回/审核/编辑/新增接口说明补状态机/回避/author_id 提示。

**遵守约定**
- 未修改任何 `rookie-*` 代码/配置（遵守 `doc/README.dev.md`「rookie 框架代码修改禁令」）；改动全在 knowhub 模块 + rookie-ui knowhub 二开文件 + 新建 sql/doc。
- 未动 Blog 既有字段（只加 author_id）、未动既有字典（只新增 blog_review_action）、未动 BlogMapper.xml 现有查询 SQL（只加 author_id 映射与 insert 列）。
- 通知预留 `notifyReviewResult` 空方法，待 rookie 支持个人通知后接入，签名零改动。

**校验**
- `mvn -q -pl knowhub -am -DskipTests compile` 通过，EXIT=0。
- `rookie-ui npm run type-check`（vue-tsc --build）通过，EXIT=0。
- 未启动 dev server（README.dev.md 约定），未跑 SQL（由用户在合适时机执行 + 业务验证）。

**待用户人工验证**：① 跑 `sql/knowhub-blog-review-log.sql` → `DESC blog` 应含 author_id；`SELECT COUNT(*) FROM blog WHERE author_id IS NULL AND deleted=0` 应为 0（现有行已回填，若 >0 说明有 create_by 对应的 sys_user 已删/改名，需人工核对）；字典 blog_review_action 应 5 行。② 启动后端，开启审核开关（系统设置 `knowhub.blog.review_enabled=true`）：作者发布文章→待审→审核员审核（通过/驳回）→查 `blog_review_log` 应有 SUBMIT + APPROVE/REJECT 流水；驳回后作者编辑（此时 status=REJECTED 合法）→再发布→回待审，流水新增一行 SUBMIT。③ 状态校验：对已发布文章调 `/blog/review` 应报"仅待审核文章可审核"；对草稿调 `/blog/revoke` 应报"仅已发布文章可撤回"；编辑已发布文章应报"已发布文章请先撤回再编辑"；对已发布文章调 `/blog/publish` 应报"已发布，无需重复发布"。④ 回避：作者尝试审核自己文章应报"不能审核自己提交的文章"。⑤ 后台详情弹窗「审核历史」折叠区展示时间线（动作标签 + 操作人昵称 + 时间 + 意见）。⑥ 关闭审核开关发布应直通 PUBLISHED，流水记 PUBLISH/SYSTEM。前台审核时间线展示待前台开发落地，详见 `doc/blog/blog-front-review-display.md`。

### 2026-07-04 待审状态按钮语义调整 + 审核弹窗展示博客内容

博客审核流水模块落地后，待审核(PENDING_REVIEW)状态下列表行仍显示「编辑」「发布」按钮，但二者均会被后端状态机拦截（编辑报错、发布报"审核中请勿重复提交"）；审核弹窗此前仅展示 blogId 让审核员盲审，缺标题/封面/正文参考。本次按"待审时发布按钮改为审核按钮、编辑入口改为内容展示"诉求调整。

**后端（knowhub 模块）**
- `BlogServiceImpl.editBlogInfo`：状态机前置校验从「仅禁 PUBLISHED」扩展为「仅 DRAFT/REJECTED/REVOKED 可编辑」，新增 PENDING_REVIEW 禁止编辑分支，报"审核中文章不能编辑，如需修改请先驳回或撤回后操作"。原因：审核员审的是提交时的快照，作者此时改动会污染审核依据，须先驳回或撤回才能改。

**前端 index.vue（按钮 visible 调整）**
- 「编辑」按钮：`visible` 从无（始终显示）改为 `!['PUBLISHED','PENDING_REVIEW'].includes(status)`，待审时隐藏。
- 「发布」按钮：`visible` 从 `status !== 'PUBLISHED'` 改为 `!['PUBLISHED','PENDING_REVIEW'].includes(status)`，待审时隐藏（避免重复提交，待审行由「审核」按钮接管）。
- 「审核」按钮：保持 `status === 'PENDING_REVIEW'` 时显示，待审行唯一动作按钮。
- 「撤回」按钮：保持仅 PUBLISHED 显示不变。
- `openReviewDialog` 由同步改异步：先调 `getBlogDetailApi` 拉取博客详情（标题/封面/正文），写入 `reviewBlog` ref 后再开弹窗；新增 `reviewLoading` ref 控制拉取期间的提交禁用。
- 模板 `BlogReviewDialog` 加 `:blog="reviewBlog"` `:loading="reviewLoading"` 两个 props 透传。

**前端 BlogReviewDialog.vue（审核弹窗展示博客内容）**
- props 加 `blog: BlogRecord | null` 与 `loading?: boolean`。
- 弹窗宽度 480→760px，表单上方增「博客内容预览区」：标题(h2) + 封面图(有则展示,max-height 260px) + 摘要(有则展示,主色左边框) + 正文(MarkdownPreview 只读渲染,max-height 320px 可滚)。
- `blog` 为空且非 loading 时用 ElEmpty 占位"未加载到博客内容"。
- `canSubmit` 计算加入 `!props.loading` 守卫，拉取期间禁用提交。
- 样式用 --rookie-* 变量适配深浅模式（预览区弱底+边框,封面圆角裁剪,摘要主色左边框）。

**遵守约定**：未修改任何 `rookie-*` 代码/配置；改动在 knowhub 模块（BlogServiceImpl）+ rookie-ui knowhub 二开文件（index.vue + BlogReviewDialog.vue）。

**校验**：`mvn -q -pl knowhub -am -DskipTests compile` 通过；`rookie-ui npm run type-check` 通过。

**待用户人工验证**：① 待审文章在列表行的按钮应为「审核 / 详情 / 删除」，不再有编辑/发布；草稿/驳回/撤回行显示编辑/发布；已发布行显示撤回。② 点「审核」弹窗应展示博客标题+封面+正文（MarkdownPreview 渲染），审核员可参考决策；拉取期间「确认提交」禁用。③ 待审文章调编辑接口（如直接 PUT /blog）应报"审核中文章不能编辑，如需修改请先驳回或撤回后操作"。

### 2026-07-06 审核开关切换遗留 PENDING_REVIEW 对账定时任务

博客审核流水模块落地后，发现一个边界漏洞：管理员把审核开关（`sys_config[knowhub.blog.review_enabled]`）从开切到关后，仍处于 PENDING_REVIEW 的遗留文章无人收口——作者编辑/再发布/撤回均被状态机拒绝（保护审核快照与队列语义），审核员也未必手动批，稿件会"卡死"在待审态。本次用定时任务被动收口，不监听系统设置保存动作（`SysConfigController.editSysConfig` 在 rookie-system 通用 key-value 接口，无法可靠区分"这次保存恰好是 review_enabled"，且改 rookie 不可行）。

**方案**：作者发布进 PENDING_REVIEW 时 SET Redis 待审标记（不计数仅标记存在性，无过期）；定时任务每 5 分钟跑一次，审核开关开 → return，标记不存在 → return（零扫表），标记存在 → 批量放行遗留稿 → 清标记。

**后端（knowhub 模块）**
- `BlogMapper`+`BlogMapper.xml`：增 `listPendingReviewIds()`，`select blog_id from blog where status='PENDING_REVIEW' and deleted=0`，对账专用单一查询。
- `BlogService`+`BlogServiceImpl`：增 `int reconcilePendingReview()`。查 list → 逐条转 PUBLISHED + reviewStatus=APPROVED + reviewer='system' + publishTime=now，逐条写 `PUBLISH/SYSTEM` 流水（advice="审核关闭后定时任务自动放行"），逐条 `evictDetail` 清详情缓存。**无 @Transactional**，单条失败 try-catch 跳过不阻塞其它稿（对齐 writeReviewLog "状态优先、历史容错"哲学，一条坏数据不该回滚已放行的其它稿）。返回放行条数。新增私有 `systemOperator()` 构造 userId=0/username=system 的 UserInfo 供写流水。
- `BlogServiceImpl.publishBlog`：审核开关开分支进入 PENDING_REVIEW 时，`redisTemplate.opsForValue().set(baseKey + CACHE_PENDING_FLAG, "1")` 置待审标记（无 expire）。新增常量 `CACHE_PENDING_FLAG = "blog:review:pending-flag"`。
- `task/BlogReviewReconcileTask.java`（新建）：`@Component` + `@Scheduled(fixedDelayString = "#{${knowhub.blog.reconcile-interval-minutes:5} * 60 * 1000}", initialDelay = 60000)`。逻辑：读 `blogConfigReader.isReviewEnabled()` 开 → return；读 Redis flag 不存在 → return；存在 → 调 `blogService.reconcilePendingReview()` → `redisTemplate.delete(flagKey)` 清标记；放行条数 >0 记 info 日志。异常 try-catch 仅 log 不中断（对齐 FileGcTask 风格）。依赖 knowhub 自带 `SchedulingConfig`(@EnableScheduling)，不碰 rookie。

**配置（rookie-admin/application.yml 追加，用户已解除禁令）**
- 加 `knowhub.blog.reconcile-interval-minutes: 5`（对账任务扫描间隔，分钟，默认 5）。

**遵守约定**：未修改任何 `rookie-*` 模块代码/配置（application.yml 是 rookie-admin 的，用户已解除禁令）；@EnableScheduling 走 knowhub 自带 SchedulingConfig；流水复用 ReviewAction.PUBLISH/SYSTEM 不新增 action；不改 publish/edit/revoke 对 PENDING_REVIEW 的拒绝语义（定时任务收口已足够，作者最多等 5 分钟）。

**边界**：① 开关切关→开期间未放行的 PENDING_REVIEW 留在队列等人工审（正确：重新开审核 = 要重新人工审）；② flag 假阳（稿已被审核员手动批但 flag 未清）→ 定时任务多扫一次空表、清 flag，可接受。

**校验**：`mvn -q -pl knowhub -am compile` 通过（EXIT=0）。

**待用户人工验证**：① 开审核状态下发布文章 → 进 PENDING_REVIEW，Redis 出现 `rookie:framework:blog:review:pending-flag=1`；② 关审核开关，等 ≤5 分钟，遗留待审文章应全部变 PUBLISHED，流水出现 PUBLISH/SYSTEM + advice="审核关闭后定时任务自动放行"，Redis flag 被清；③ 审核员在定时任务跑之前手动批了某篇，flag 仍在，下次定时任务扫到空表、清 flag，无副作用。

