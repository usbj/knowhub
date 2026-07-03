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
