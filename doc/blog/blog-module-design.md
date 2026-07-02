> 日期：2026-06-30
> 状态：思路设计稿（已定稿，未落地，未写码）
> 关联模块：knowhub-blog（`com.knowhub.blog`，当前为空架子）
> 关联文档：doc/knowhub-api.md、doc/knowhub-devlog.md、doc/综合知识库管理系统-项目文档初稿.md、doc/README.dev.md
> 遵守约定：见 doc/README.dev.md「全局开关落地约定」「rookie 框架代码修改禁令」

# knowhub 博客模块设计稿（思路版·定稿）

本文档给出 knowhub 知识库博客系统 **博客文章 CRUD + 受控标签 + 可配审核开关** 模块的思路设计，覆盖三个方面：流程架构、数据库设计、代码架构。

## 0. 设计前提（已与用户对齐）

| 项 | 决定 | 说明 |
|----|------|------|
| 范围 | 文章 CRUD + 标签关联读写 + 最简审核 | 标签受控、标准多对多 |
| 封面图 | **要，非必填** | `cover_url` 可空 |
| 分类(Category) | **不要** | 组织维度只用标签 |
| 置顶(is_top) | **不要** | 公告那套，知识库不该全局置顶 |
| 标签模型 | **受控标签 + 标准多对多** | 管理员维护标签集，作者发文时从中选，读者按标签筛选/搜索；文章-标签只走中间表 `blog_tag`，主表不冗余标签字段 |
| 审核开关 | **字典全局开关 `blog_review_enabled`** | 走 `sys_dict`，读取收口到 `BlogConfigReader`，见 §5；开关关→直接发布，开→待管理员审核 |
| 审核形态 | **最简一步审核** | 列表看待审、一键通过/驳回，不做多级审核人/审核流 |
| 检索方案 | **MySQL FULLTEXT** | title/content 建 fulltext(ngram)，标签走精确 IN 查询 |
| 用户端计数 | **浏览 + 点赞 + 收藏** | 浏览走 Redis 原子计数，点赞/收藏走明细表 |
| rookie 框架 | **零修改** | 不动 `rookie-*` 任何代码/配置，所需扩展（Mapper 扫描、springdoc 分组）一律在本模块自带配置类内自注册（见 §4.3） |

## 1. 模型与组织维度

### 1.1 受控标签链路

```
管理员维护受控标签集(tag 表)  ──select──►  作者发文时从中选标签
                                            ▼
                                 读者按标签筛选文章 / 全文搜索
```

"受控"体现在两点（业务层约束，表结构无特殊要求）：
- 标签表只有**管理员**能增删改（Controller 鉴权 `knowhub:tag:*`）。
- 文章选标签时，提交的 tagId 必须落在当前受控集里——Service 校验，非法 tagId 直接报错。

文章与标签是**标准多对多**，只通过中间表 `blog_tag` 维护，主表不存任何标签字段。文章当前有哪些标签 = `SELECT tag_id FROM blog_tag WHERE blog_id = ?`。

### 1.2 审核与开关的关系

审核是**全局开关**控制，开关读自字典：
```
作者点发布 publish
   │
   ▼
BlogConfigReader.isReviewEnabled()  ←  读 sys_dict['blog_review_enabled']
   │
   ├─ false(开关关) → status 直接置 PUBLISHED，写 publish_time
   └─ true(开关开)  → status 置 PENDING_REVIEW，等管理员审核
                     │
                     ├─ 管理员通过 → PUBLISHED
                     └─ 管理员驳回 → REJECTED（作者可改回 DRAFT 再发）
```

## 2. 数据库设计

引擎/字符集沿用仓库：`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`。审计列统一 `create_by/create_time/update_by/update_time` + 软删 `deleted`(tinyint)，与 `sys_notice` 一致。时间列 `DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`。

> 五表 DDL + `sys_menu` 权限行 + `blog_review_enabled` 字典数据，统一建在 `sql/knowhub-blog.sql`（独立文件，不改动上游 `sql/rookie.sql`，避免触碰上游种子）。

### 2.1 文章主表 `blog`

```sql
DROP TABLE IF EXISTS `blog`;
CREATE TABLE `blog` (
  `blog_id`     bigint NOT NULL AUTO_INCREMENT COMMENT '文章主键',
  `title`          varchar(200) NOT NULL COMMENT '标题',
  `content`        longtext NOT NULL COMMENT '正文（Markdown/HTML）',
  `summary`        varchar(500) DEFAULT NULL COMMENT '摘要（可空，可由正文截取）',
  `cover_url`      varchar(255) DEFAULT NULL COMMENT '封面图地址（非必填）',
  -- 状态
  `status`         varchar(32) NOT NULL DEFAULT 'DRAFT'
                   COMMENT '状态：DRAFT草稿 PUBLISHED已发布 REVOKED已撤回 PENDING_REVIEW待审核 REJECTED已驳回',
  `publish_time`   datetime DEFAULT NULL COMMENT '发布时间',
  -- 计数
  `view_count`     bigint NOT NULL DEFAULT '0' COMMENT '浏览量(Redis 异步落库)',
  `like_count`     bigint NOT NULL DEFAULT '0' COMMENT '点赞量(冗余，以 blog_like 为准)',
  `collect_count`  bigint NOT NULL DEFAULT '0' COMMENT '收藏量(冗余，以 blog_collect 为准)',
  -- 审核（开关关时不用；开关开时填充）
  `review_status`  varchar(32) DEFAULT NULL COMMENT '审核状态：NONE无 PENDING待审 APPROVED通过 REJECTED驳回',
  `reviewer`       varchar(64) DEFAULT NULL COMMENT '审核人',
  `review_time`    datetime DEFAULT NULL COMMENT '审核时间',
  `review_advice`  varchar(500) DEFAULT NULL COMMENT '审核意见',
  -- 通用
  `create_by`      varchar(64) NOT NULL COMMENT '创建人(作者)',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      varchar(64) NOT NULL COMMENT '更新人',
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        tinyint NOT NULL DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`blog_id`),
  KEY `idx_blog_status` (`status`),
  KEY `idx_blog_publish_time` (`publish_time`),
  KEY `idx_blog_deleted` (`deleted`),
  KEY `idx_blog_review_status` (`review_status`),
  FULLTEXT KEY `ft_blog_title_content` (`title`,`content`)  -- 需 ngram 分词（见 2.7）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章主表';
```

> `review_*` 四列开关关时建空不用，开关开时填充；避免后续 `ALTER TABLE`。

### 2.2 受控标签表 `tag`

```sql
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `tag_id`      bigint NOT NULL AUTO_INCREMENT COMMENT '标签主键',
  `tag_name`    varchar(64) NOT NULL COMMENT '标签名',
  `description` varchar(255) DEFAULT NULL COMMENT '标签说明（可选）',
  `sort`        int NOT NULL DEFAULT '0' COMMENT '排序',
  `status`      tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1启用（禁用后不可被新文章选用）',
  `create_by`   varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`   varchar(64) NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`, `deleted`),
  KEY `idx_tag_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控标签表（管理员维护）';
```

### 2.3 文章-标签关联表 `blog_tag`（多对多中间表）

```sql
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag` (
  `blog_id` bigint NOT NULL COMMENT '文章ID',
  `tag_id`     bigint NOT NULL COMMENT '标签ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `tag_id`),
  KEY `idx_at_tag` (`tag_id`),
  KEY `idx_at_article` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章-标签关联表（多对多）';
```

编辑文章时标签"先删后插"重建关联。无审计列/软删，关联重建即可。

### 2.4 点赞明细表 `blog_like`

```sql
DROP TABLE IF EXISTS `blog_like`;
CREATE TABLE `blog_like` (
  `blog_id` bigint NOT NULL,
  `user_id`    bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `user_id`),
  KEY `idx_like_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章点赞明细';
```

点赞 = insert；取消 = delete；`article.like_count` 冗余同步 `+1/-1`，以明细表为准可定期校准。

### 2.5 收藏明细表 `blog_collect`

```sql
DROP TABLE IF EXISTS `blog_collect`;
CREATE TABLE `blog_collect` (
  `blog_id` bigint NOT NULL,
  `user_id`    bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`blog_id`, `user_id`),
  KEY `idx_collect_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章收藏明细';
```

结构与点赞对称。

### 2.6 FULLTEXT 与中文分词

MySQL 8 原生 FULLTEXT 对中文支持差，需 `ngram` 分词器：

```sql
SET GLOBAL ngram_token_size = 2;
-- 或在 my.cnf [mysqld] 加: ngram_token_size=2
ALTER TABLE `blog` ADD FULLTEXT KEY `ft_blog_title_content` (`title`,`content`) WITH PARSER ngram;
```

检索（boolean mode，可带标签过滤）：
```sql
SELECT * FROM article
WHERE MATCH(title, content) AGAINST(#{keyword} IN BOOLEAN MODE)
  AND deleted = 0 AND status = 'PUBLISHED';
```

**标签不进 fulltext**：标签受控、数量少，走 `blog_tag join ... WHERE tag_id IN (...)` 精确查 + 二级索引。"搜索 + 标签筛选"是 fulltext 查正文与标签 IN 查的复合条件。

### 2.7 菜单与权限（`sys_menu`）

按 `notice` 既有三层结构（目录→页面→按钮），权限键三段式 `knowhub:<模块>:<动作>`，与上游 `system:<模块>:<动作>` 对齐、`knowhub` 前缀区分二开新增；动作沿用仓库既有命名 `quarry/info/add/edit/delete/publish/revoke/review`。`sys_menu` INSERT 行写进 `sql/knowhub-blog.sql`：

```
knowhub:blog:quarry   文章查询(列表)
knowhub:blog:info     文章详情
knowhub:blog:add      文章新增
knowhub:blog:edit     文章编辑
knowhub:blog:delete   文章删除
knowhub:blog:publish  文章发布(提交)
knowhub:blog:revoke   文章撤回
knowhub:blog:review   文章审核（通过/驳回，仅管理员）
knowhub:tag:*         标签管理(管理员) quarry/info/add/edit/delete
```

权限键用独立顶级域 `blog`（与 notice 的 `system:notice` 并列），不污染 `system` 命名空间。落地时同步给前端 v-perm 配置。

## 3. 流程架构

### 3.1 分层流程总览

```
HTTP 请求 + Token（TokenVerifyFilter 已有，JWT 校验）
   │
   ▼
Controller    @PreAuthorize 鉴权 + @Log 操作日志
   │  入参: BlogVo / BlogQuarry / ReviewVo / 路径参数
   ▼
Service (interface + impl)   事务边界 @Transactional(仅写)
   │  DTO↔Entity(Hutool BeanUtil)、审计字段回填、业务校验、缓存读写
   │  发布时经 BlogConfigReader 读审核开关决定目标状态
   ▼
Mapper (@Mapper) + *Mapper.xml   纯 MyBatis，PageHelper 拦截分页，fulltext 查写在 XML
   │
   ▼
MySQL(knowhub 库)  +  Redis(view 计数 / 详情缓存 / 字典缓存)
```

### 3.2 文章状态机（五态，受审核开关控制）

```
                 publish 开关关
┌─────────────┐ ──────────────────►  ┌──────────────┐
│  DRAFT 草稿  │                       │  PUBLISHED    │ ◄── review 通过 ──┐
└─────────────┘ ◄───────────────────  │   已发布      │                    │
     ▲  edit/delete                   └──────┬───────┘                    │
     │                                        │ revoke                    │
     │                                        ▼                            │
     │                                  ┌──────────────┐                   │
     │   publish 开关开                 │   REVOKED     │                   │
     │   ─────────────────────►  ┌─────┴───┐                            │
     │                            │PENDING_  │ ──review 通过──► PUBLISHED ┘
     │                            │REVIEW    │
     │       edit(回草稿再改)     └────┬────┘
     │            ◄─────────────────────│
     │                                 │ review 驳回
     ▼                                 ▼
┌──────────┐                       ┌──────────┐
│  REJECTED │ ──edit──► DRAFT       │
│  已驳回    │                       └──────────┘
└──────────┘
```

- publish 走 `BlogConfigReader.isReviewEnabled()`：false → `PUBLISHED` + 写 `publish_time`；true → `PENDING_REVIEW` + `review_status=PENDING`。
- revoke → `REVOKED`。
- review 通过 → `PUBLISHED` + 写 `publish_time` + `review_status=APPROVED/reviewer/review_time`；驳回 → `REJECTED` + `review_status=REJECTED/review_advice`。
- 读者侧只看 `status=PUBLISHED AND deleted=0`；待审/驳回读者不可见。

### 3.3 各操作主流程

**新增 Create** — 鉴权 → 校验标题/正文非空 + tagId 全部落到受控标签集（`tag.status=1 AND deleted=0`）→ insert blog（status=DRAFT，`review_status=NONE`）→ 批量 insert blog_tag → 草稿不缓存。

**查询列表 Read-list** — `PageUtil.startPage()` → mapper 查 → `packagedPageInfo` + `copyPageInfo(BlogVo.class)`。读者侧默认 `status=PUBLISHED AND deleted=0`，管理台带 `status` 过滤查全状态，待审文章可单独按 `review_status=PENDING` 筛。支持 fulltext 关键词 + 标签(`blog_tag join, tag_id IN(...)`) 复合筛选，两条件 Service 拼进同一 Quarry。

**查询详情 Read-info** — 查 Redis `blog:detail:{id}`，命中返回；未命中查库 + 回填 VO（经 `blog_tag` 关联查 tagId/tag 列表、作者信息）→ 回写缓存(`setCacheToSetTime`)；浏览量对 `blog:view:{id}` incr，**不直接 update 主表**（防热点）。

**编辑 Update** — 校验存在性 + 权限归属（作者本人或管理员）→ 校验标签受控 → 事务内：update article(`<set>` 动态列) + `blog_tag` 先删后插重建关联 → 失效 detail 缓存。驳回态(REJECTED)经 edit 回 DRAFT 可再改再发。

**删除 Delete** — 批量软删 `update article set deleted=1, update_by=?, update_time=now()`（沿用 sys_notice 软删）→ 失效缓存 + 清 `blog_tag` 关联行（业务层清，不用物理外键）。

**发布/撤回 Publish/Revoke** — publish 经 `BlogConfigReader.isReviewEnabled()` 分支决定目标状态（见 §3.2）并写 `publish_time`；revoke 置 REVOKED；都失效 detail 缓存。

**审核 Review**（`knowhub:blog:review` 权限，仅管理员）— 入参 `reviewVo(blogId, pass, advice)`：通过 → `status=PUBLISHED, publish_time=now(), review_status=APPROVED, reviewer=?, review_time=now()`；驳回 → `status=REJECTED, review_status=REJECTED, review_advice=?, reviewer=?, review_time=now()`。失效 detail 缓存。一步审核，无流转。

**点赞/收藏 Like/Collect** — 点赞：`blog_like` insert 一行 + `article.like_count +1`；取消：delete 一行 + `-1`。收藏同理。

**标签管理 Tag CRUD**（`knowhub:tag:*`，仅管理员）— 增删改 tag 表。**删除/禁用标签时必须级联清理 `blog_tag` 中间表里该 tag_id 的关联行**（`DELETE FROM blog_tag WHERE tag_id = ?`）——主表不存标签字段，清了关联文章即不再带该标签，主表无需再动。落地易漏点，务必处理。

### 3.4 缓存策略

| 场景 | Key | 行为 |
|------|-----|------|
| 文章详情 | `blog:detail:{id}` | 读穿透，`setCacheToSetTime` 写入(默认 30min，受 redis.expire-time) |
| 浏览计数 | `blog:view:{id}` | `incr` 原子累加，定时/写操作批量回写 `blog.view_count` |
| 审核开关 | `sys_dict_name:blog_review_enabled`（`DictUtil` 维护） | 走 `DictUtil` 现成缓存，无需自管 |
| 热门/榜单 | `blog:hot` | 定时刷新 top-N（可选，本期可不做）|

- 写/删/发布/撤回/审核一律 `deleteCache` 失效对应 detail key。
- 所有 key 经 `RedisCache` 自动加 `rookie:framework:` 前缀（已配置）；字典 key 经 `DictUtil` 前缀 `sys_dict_name:`。
- 详情 VO 的标签列表来自 `blog_tag` 实时查（缓存进 detail VO），编辑改标签后删 detail 缓存重新生成，保证不脏读。

## 4. 代码架构

### 4.1 包结构（`knowhub-blog` 模块内）

```
knowhub-blog/src/main/java/com/knowhub/blog/
├── config/
│   ├── BlogMapperConfig.java              (自注册本模块 Mapper，见 4.3)
│   ├── BlogOpenApiConfig.java             (自注册 springdoc blog 组，见 4.3)
│   └── BlogConfigReader.java ──*          (审核开关读取收口，见 §5) *置于 config 或 util 包均可
├── controller/
│   ├── BlogController.java
│   └── TagController.java                 (管理员维持受控标签)
├── service/
│   ├── BlogService.java                (interface)
│   ├── TagService.java                    (interface)
│   └── impl/
│       ├── BlogServiceImpl.java
│       └── TagServiceImpl.java
├── mapper/
│   ├── BlogMapper.java
│   ├── TagMapper.java
│   ├── BlogTagMapper.java
│   ├── BlogLikeMapper.java
│   └── BlogCollectMapper.java
├── pojo/
│   ├── entity/                          (knowhub 业务实体，不放 rookie-common)
│   │   ├── Blog.java
│   │   ├── Tag.java
│   │   ├── BlogTag.java
│   │   ├── BlogLike.java
│   │   └── BlogCollect.java
│   ├── vo/
│   │   ├── BlogVo.java                 (含 tagIds, tagNames, 计数, 作者信息)
│   │   ├── TagVo.java
│   │   └── ReviewVo.java                  (审核入参: blogId, pass, advice)
│   └── quarry/
│       └── BlogQuarry.java             (关键词 + tagIds + status + reviewStatus + 时间区间 + pageNum/pageSize)
└── enums/
    ├── BlogStatus.java                 (DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED)
    └── ReviewStatus.java                  (NONE/PENDING/APPROVED/REJECTED)
knowhub-blog/src/main/resources/
└── mapper/blog/
    ├── BlogMapper.xml
    ├── TagMapper.xml
    ├── BlogTagMapper.xml
    ├── BlogLikeMapper.xml
    └── BlogCollectMapper.xml
```

**实体放置**：knowhub 业务实体放在**本模块内** `com.knowhub.blog.pojo.entity.*`（与 `pojo.vo`/`pojo.quarry` 并列），**不放进 `com.rookie.common.pojo.entity`**——遵守 doc/README.dev.md「knowhub 业务产物不得放入 rookie 模块」。实体 `extends com.rookie.common.pojo.BaseEntity`（仅含 createTime/updateTime/createBy/updateBy 四字段，无 remark；remark 各实体自带），引用框架基类不算违规。项目**不用 Lombok**，全手写 getter/setter。

### 4.2 分层要点（沿用 notice 模块约定）

- **Controller**：路径 `/blog`（不套 `/sys`，因 blog 在 com.knowhub 命名空间）。`@Tag`/`@Operation`(springdoc) 描述。写操作加 `@Log(title="博客文章", businessType=...)`，读操作不加。方法 `@PreAuthorize('knowhub:blog:quarry/info/add/edit/delete/publish/revoke/review')`。请求头 `Token` 无 Bearer 前缀。
- **Service**：接口只暴露 DTO（BlogVo/BlogQuarry/ReviewVo/PageInfo），不暴露实体。实现 `@Service`，写方法 `@Transactional`，失败 `throw new ServiceException(500, "...", e.getMessage())`。DTO↔Entity 用 Hutool `BeanUtil.toBean`。
- **列表分页三段式**：`PageUtil.startPage()` → mapper 查 → `PageUtil.packagedPageInfo` → `copyPageInfo(page, BlogVo.class)`（Controller 不直接调 PageHelper）。
- **当前用户**：`(UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()`，调 `.getUserId()`/`.getUsername()`（项目**无 SecurityUtils 封装**）。
- **返回**：统一 `Result.success(data)`；失败由 Service 抛异常经全局处理。
- **Mapper + XML**：接口加 `@Mapper`，XML 放模块内 `resources/mapper/blog/*Mapper.xml`，被现有 `mybatis.mapperLocations: classpath*:mapper/**/*Mapper.xml` 自动扫描。**全限定类名**引用类型（项目未配 type-aliases）。纯 MyBatis（**不引 MyBatis-Plus**），分页全靠 PageHelper。
- **fulltext 查询写在 XML**：`<select>` 里 `MATCH(title,content) AGAINST(#{keyword} IN BOOLEAN MODE)`，配合动态 `<if>` 拼标签 `tag_id IN(...)` 条件。
- **标签回填**：详情 VO 的 tagIds/tagNames 由 BlogTagMapper 关联查 tag 表组装；列表批量回填避免 N+1（一次 `IN` 查所有标签再按 article 分组）。
- **审核调用**：`BlogServiceImpl.publish` 调 `blogConfigReader.isReviewEnabled()` 分支；`review` 方法调 `blogConfigReader` 不读开关直接按入参 pass 处理。

### 4.3 模块自注册（**零修改 rookie 框架**）

当前框架 `@MapperScan("com.rookie.**.mapper")` 只扫 `com.rookie`，springdoc group 只扫 `com.rookie.system.controller`。blog 模块在 `com.knowhub.**`，**不改这两处框架配置**，改在本模块自带配置类自注册（`KnowhubApplication` 已 `scanBasePackages={"com.rookie","com.knowhub"}`，自带 `@Configuration` 会被扫到）：

1. **Mapper 自注册** — 新建 `config.com.knowhub.BlogMapperConfig`：
   ```java
   @Configuration
   @MapperScan("com.knowhub.blog.mapper")     // 与框架的 @MapperScan 并存叠加生效
   public class BlogMapperConfig {}
   ```
   多个 `@MapperScan` 跨配置类是叠加注册，blog 的 Mapper Bean 即被识别，**无需改 `ApplicationConfig`**。

2. **springdoc 分组自注册** — 新建 `config.com.knowhub.BlogOpenApiConfig`，编程式声明 `GroupedOpenApi` Bean：
   ```java
   @Configuration
   public class BlogOpenApiConfig {
       @Bean
       GroupedOpenApi blogGroup() {
           return GroupedOpenApi.builder().group("blog")
                   .pathsToMatch("/**")
                   .packagesToScan("com.knowhub.blog.controller").build();
       }
   }
   ```
   springdoc 收集所有 `GroupedOpenApi` Bean，blog 接口即进 swagger-ui，**无需改 `application.yml`**。

> 这两处是"新增本模块文件"，非改动 rookie 既有代码/配置，符合禁令。

## 5. 审核开关与 `BlogConfigReader`

### 5.1 字典落地

走 `sys_dict` + `sys_dict_data`（见 doc/README.dev.md「全局开关落地约定」），DDL 与数据写进 `sql/knowhub-blog.sql`：

```sql
-- 字典类型：审核开关（注意 sys_dict 备注字段是 remake，不是 remark）
INSERT INTO `sys_dict` (`dict_name`,`dict_key`,`status`,`remake`,`create_by`,`create_time`,`update_by`,`update_time`)
VALUES ('博客审核开关','blog_review_enabled',1,'控制文章发布是否需经审核','admin',NOW(),'admin',NOW());

-- 两条对称数据项：sys_dict_data 备注字段是 remark
INSERT INTO `sys_dict_data` (`dict_id`,`dict_key`,`dict_data_label`,`dict_data_value`,`remark`,`dict_data_sort`,`status`,`is_default`,...)
VALUES (@dictId,'blog_review_enabled','关闭审核','false','发布直通',1,1,'1',...);
INSERT INTO `sys_dict_data` (`dict_id`,`dict_key`,`dict_data_label`,`dict_data_value`,`remark`,`dict_data_sort`,`status`,`is_default`,...)
VALUES (@dictId,'blog_review_enabled','开启审核','true','发布需审核',2,1,'0',...);
```

> `dict_id` 取上条插入自增 id（用 `LAST_INSERT_ID()` 或变量）。`@dictId`、`...` 占位以 `sys_dict_data` 实际列为准，落地时补全。

### 5.2 读取收口到 `BlogConfigReader`

```java
// config.com.knowhub.BlogConfigReader —— 全项目唯一"知道开关从哪来"的地方
@Component
public class BlogConfigReader {
    public boolean isReviewEnabled() {
        // 当前实现：走字典
        List<SysDictData> data = DictUtil.getDictData("blog_review_enabled");
        return data.stream().anyMatch(d -> "true".equalsIgnoreCase(d.getDictDataValue())
                                        && Integer.valueOf(1).equals(d.getStatus()));
    }
    // 将来换系统设置表：只改本方法内部 → 查系统设置表；签名与调用方零改动
}
```

- `BlogServiceImpl.publish` 只调 `blogConfigReader.isReviewEnabled()`，**业务侧绝不直接 `DictUtil`**。
- **编辑开关后必须主动刷字典缓存**：复用字典管理接口的 add/edit 已自带 `DictUtil.setDictData` 刷缓存（见 `SysDictDataServiceImpl`）；本项目 `delete` 时不刷缓存有已知 `TODO`，开关是 edit 不是 delete，规避即可。即：后台改开关走 `/sys/dist/data` 的 edit 接口，自然触发刷缓存；前端用 `useDict('blog_review_enabled')` 渲染开关。
- `DictUtil` 的 `@Component` 虽被注释，但它是静态工具经 `SpringUtil.getBean` 取 `RedisCache`，运行时可用，无需注入。

### 5.3 将来切系统设置表的影响面

- 唯一耦合点是 `BlogConfigReader.isReviewEnabled()`；换存储只改该类内部实现，`BlogServiceImpl` 等调用方零改动。
- 迁移只需把字典里那两条值搬到新表的一次性脚本；缓存机制届时另起即可。

## 6. 落地清单（思路定稿后执行）

**新建（全部在本模块或 `sql/`，不动 rookie 框架）：**
- `knowhub-blog` 模块全套：controller(2) / service+impl / config(3) / mapper(5) / pojo(entity,vo,quarry) / enums(2) / `resources/mapper/blog/*.xml`
- 实体放本模块 `com.knowhub.blog.pojo.entity`：`Blog`、`Tag`、`BlogTag`、`BlogLike`、`BlogCollect`（**不放 `rookie-common`**）
- `sql/knowhub-blog.sql`：5 表 DDL + `sys_menu` 权限行 + `blog_review_enabled` 字典类型与两条数据项

**零修改之处（遵守禁令）：**
- 不改 `rookie-framework/.../ApplicationConfig.java`（用 `BlogMapperConfig` 自注册）
- 不改 `rookie-admin/.../application.yml`（用 `BlogOpenApiConfig` 自注册 springdoc 分组）
- 不改 `sql/rookie.sql`、不改任何既有 rookie 代码/配置

**文档同步（按 doc 约定）：**
- `doc/knowhub-api.md`：按四子节格式补接口。文章 CRUD 7 个 + 审核 1 个 + 标签管理 5 个 + 点赞/收藏 4 个 ≈ 17 个，满足"5–8/模块"门限；追记「接口更新日志」一条
- `doc/knowhub-devlog.md`：仅当**实际代码落地**后追记（设计稿不入 devlog）

**易漏点速查（落地务必覆盖）：**
1. 标签删除/禁用时级联清 `blog_tag` 关联行（§3.3）
2. 编辑文章标签先删后插，同步删 detail 缓存（§3.3、§3.4）
3. 浏览量走 Redis incr，不直接 update 主表（§3.3）
4. `BlogConfigReader` 收口开关读取，业务侧禁直接 `DictUtil`（§5.2）
5. 字典 SQL 两表备注字段拼写：`sys_dict.remake` / `sys_dict_data.remark`；controller 前缀 `/sys/dist` 不是 `/sys/dict`（§5.1）