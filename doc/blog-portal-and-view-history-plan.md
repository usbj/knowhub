# knowhub 博客前台推荐/搜索 + 浏览历史/浏览量/文章标签/标签排行榜 — 设计计划

> 两条配套链路，2026-07-15 定稿待实施。无新增菜单/字典，无新增 Maven 模块，全 `com.knowhub.*`。

---

## 第一条链路：博客前台推荐 + 搜索（/portal/blog/*）

### 数据底座（已核查确凿）
- 博客全文索引 `ft_blog_title_content(title,content) WITH PARSER ngram` 已存在（`sql/knowhub-blog.sql:52`），需 MySQL `ngram_token_size=2`。搜索复用，不建索引。
- 正文直接在 `blog.content`(longtext)，**无 blog_content 分表**。
- 用户行为明细：`blog_like(blog_id,user_id)`、`blog_collect(blog_id,user_id)` 已有。
- 浏览量 `view_count`：DB 恒 0，Redis `blog:view:{id}` 累加但不回读（`BlogServiceImpl:668-674` 孤儿）。
- 用户偏好/画像表：无。从 like/collect 现算反推。
- 博客等级过滤：`level<=userViewLevel OR author_id=userId`，未考虑访客；前台公开接口默认 `status='PUBLISHED' AND level=1`（分级开关开时 `level<=userViewLevel`，见决策#8）。
- 评分：BlogVo 无 rating，不引入。
- 公开接口白名单：`SecurityConfig:55-83` 只有 `/login`、`/file/public/**`。

### 已拍板决策
1. 前台公开走 `/portal/**` permitAll（改 SecurityConfig 一处 rookie 文件，已批准）。
2. 用户标签偏好 = 现算 `blog_like ∪ blog_collect` 反推 tag 频次，不建偏好表。
3. 搜索复用现有 FULLTEXT(title,content)。
4. view_count 只在新公开接口回读 VO，不改 BlogServiceImpl 详情既有链路（该条作废，见第二条链路统一改）。
5. **前后台详情接口分离（铁律）**：新增前台公开详情接口 `/portal/blog/{blogId}`，后台 `/blog/{id}` 不动。二者等级校验语义不同，绝不能共享一个接口靠 if 分支切——分支写错 L2/L3/草稿就泄公网，拆开把铁律做成结构性的而非条件性的。详见下方"前后台详情接口分离"小节。
6. 推荐打分 = SQL 召回 + Service 层 Java 算分（权重可调）。
7. 不加总开关——前台数据获取接口一律不加权限不加开关；数据修改类才要权限。
8. **分级推荐开关（唯一例外，系统设置兜底）**：默认严格——前台推荐与详情一律 `level=1` 二元闸（L2/L3 永不下发门户）。系统设置 `knowhub.portal.hierarchical.enabled`（默认 `false`）开启后，前台才按登录用户实际 view 等级分级下发：推荐与详情 SQL 改走 `level<=userViewLevel` 阶梯闸，L1 用户/未登录行为等同默认严格态（仍只看 L1），L2/L3 用户才能收到本级推荐与详情。开关由 AdminConfigReader 单读、sys_config 单存、不走菜单/字典；默认关 = 公开门户语义不变、无越级风险。
9. **前台详情越级访问返回部分内容+锁态（不拒绝访问、不下发正文）**：`/portal/blog/{blogId}` 查到 `blog.level>userViewLevel`（分级开关关时 userViewLevel 恒视 1）时，不抛 403、不返回完整 VO，而是下发 `BlogPortalDetailVo` 的 `locked=true` 锁态——只带 title/summary/coverUrl/authorNickname/publishTime/计数/标签 + `lockReason`（如"需 L2 权限查看完整正文"），**content 置空不下发**。与第二条链路决策#3"未登录显示部分内容拒绝查看"同构：公开门户体面降级，不泄正文。
10. **读写路由物理隔离**：读（数据获取）走 `/portal/**` permitAll；写（创作/编辑/发布/撤回等数据修改）走新前缀 `/authoring/**` authenticated 兜底（不进 permitAll、不要按钮权限键）。`portal`=公开门户读、`authoring`=登录作者写，物理隔离避免 permitAll 区被污染。**点赞/收藏不进 `/authoring/**`**——复用现有 `PUT /blog/like/{id}`、`PUT /blog/collect/{id}`（已无 @PreAuthorize、仅登录态，见 [BlogController.java:119-133](../knowhub/src/main/java/com/knowhub/controller/BlogController.java#L119)），不重复实现。
11. **分级创作闸（创作链路通用，前后台共用同一 Service 闸）**：非 L2 级成员不能创建 L2 级博客（文章同理）。即能创作的内容等级上限 ≤ 自身 view 等级：`assertCanCreateLevel(userViewLevel, targetLevel)`——传 null 缺省 L1；传 N 必须 `userViewLevel>=N` 否则 ServiceException。后台 add 当前缺这个闸（[BlogServiceImpl.java:182-184](../knowhub/src/main/java/com/knowhub/service/impl/BlogServiceImpl.java#L182) 只在 null 时补 L1，传 L3 照建 L3）——是既有缺口，本轮补。闸抽成共用内部方法，后台 add 与前台创作各调一次，语义统一不重复实现。前台无按钮权限键者按登录态 view 等级走同一闸。

### 五个公开接口（/portal/blog/*，全部无 @PreAuthorize）
| 接口 | 方法 | 用途 | 出参 |
|---|---|---|---|
| `/portal/blog/search` | GET | 全文搜索+标签/作者复合过滤+相关度/热度/最新排序，分页 | `Result<PageInfo<BlogPortalVo>>` |
| `/portal/blog/recommend` | GET | 个性化推荐 feed（登录用户按偏好 tag，未登录/无行为走全局热门兜底） | `Result<List<BlogPortalVo>>` |
| `/portal/blog/{blogId}` | GET | **前台公开详情（含正文 content）**，二元闸只下发 PUBLISHED+level=1 | `Result<BlogPortalDetailVo>` |
| `/portal/blog/{blogId}/related` | GET | 详情页相关推荐 | `Result<List<BlogPortalVo>>` |
| `/portal/tag/hot` | GET | 标签榜（统计 blog_tag+article_tag，见第二条链路） | `Result<List<HotTagVo>>` |

> 注：原设计的 `/portal/blog/hot-tags`（仅博客）已废弃，统一为 `/portal/tag/hot`，因为标签是博客和文章共用的。

**铁律**：所有 `/portal/blog/*` SQL 默认 `where b.deleted=0 and b.status='PUBLISHED' and b.level=1`——L2/L3 永不下发前台（分级开关关时）。分级开关 `knowhub.portal.hierarchical.enabled` 开启后，推荐与详情 SQL 改走 `and b.level<=#{userViewLevel}` 阶梯闸（由 Service 层按 `BlogPermissionResolver.resolve().view()` 注入 userViewLevel，未登录=1）；L1 内容恒在等级闸内，故开关开也只是"放宽上限"，不破坏 L1 公开门户语义。

### 前后台详情接口分离（铁律，不共享接口）

前台 `/portal/blog/{blogId}` 与后台 `/blog/{id}` 必须是独立接口，**禁止**用同一个 service 方法 + if 分支切"前台/后台模式"。等级校验语义本质不同：

| 维度 | 后台 `/blog/{id}`（不动） | 前台 `/portal/blog/{id}`（新增） |
|---|---|---|
| 鉴权 | `knowhub:blog:info`（登录+权限键） | 无（permitAll） |
| 状态过滤 | 全态（编辑要看草稿/待审/驳回） | `status='PUBLISHED'` 硬编码 |
| 等级过滤 | `level<=userViewLevel OR author_id=userId` 阶梯 | 默认 `level=1` 硬编码；分级开关开则 `level<=userViewLevel` 阶梯（未登录恒1）；越级时锁态不泄正文（见决策#9） |
| 出参 | `BlogVo`（含 canView/canEdit/isAuthor 权限态+审核字段） | `BlogPortalDetailVo`（BlogPortalVo+content+locked+lockReason，越级时 content 置空，无审核/权限态） |
| 浏览量 | recordView（登录态才调，替换孤儿 Redis incr） | recordView（仅登录态调，未登录不计数，符合第二条链路决策 #3） |
| 缓存 | Redis 存重 VO+权限态（随用户变，命中后实时重算） | 内容对所有人一致，可按 blogId 单独缓存，viewCount 读主表不进缓存 |

理由：后台的 `canOp(view)=userLvl>=level OR author` 是阶梯放行（作者看全态、编辑看草稿/待审/驳回）；前台默认是二元闸（只要公开+已发布），分级开关开则阶梯闸（按登录等级放行 L2/L3，L1 公开仍打底）；越级访问不返 403 而锁态降级。共享一个接口靠分支切，分支写错就把 L2/L3 或草稿泄到公网。拆成独立接口 = 把"默认永不下发 L2/L3/草稿"的铁律做成**结构性的**（SQL 等级闸 + 锁态降级），而非**条件性的**（if 分支）。

### 前台创作接口（/authoring/blog，不进 /portal/，复用 addBlogInfo + 分级闸）

前台用户写博客复用后台 service `addBlogInfo`（body 与前后台无关：author_id 恒取 currentUser 强制覆盖 VO 值、新建即 DRAFT、计数初始化 0、非任何后台专属字段泄漏——见 [BlogServiceImpl.java:163-192](../knowhub/src/main/java/com/knowhub/service/impl/BlogServiceImpl.java#L163)）。不在 `/portal/**` permitAll 区开写接口（与决策#7"数据修改要权限"冲突），走新前缀 `/authoring/**` authenticated。

| 接口 | 路径 | 方法 | 鉴权 | service 复用 | 分级闸 |
|---|---|---|---|---|---|
| 前台创作 | `POST /authoring/blog/draft` | POST | `@PreAuthorize("isAuthenticated()")`（走 `/authoring/**` authenticated 兜底；无按钮权限键） | 直接调 `blogService.addBlogInfo(vo)`（薄封装或 inline） | 调 `assertCanCreateLevel(userViewLevel, vo.level)` |

> 命名用 `/draft` 而非裸 `/`：前台创作语义是"存草稿"，发布另走 `/authoring/blog/{id}/publish`（接 `publishBlog`，同样走分级闸+审核开关，见决策#1）。后续编辑/撤回/点赞/收藏等作者写动作均挂 `/authoring/blog/**`。

**后台 `POST /blog`（add）保留**：是管理员/编辑维护公开内容主入口，废弃会堵死"管理员直接发 L3"。唯一改动是给它补同一分级闸（`assertCanCreateLevel`），防"有 add 按钮权限键但只 L1 view 等级"者建 L3。前后台各调一次同一闸方法，语义统一。

**分级闸实现要点**（共用 Service 内部方法，前后台复用）：
- `assertCanCreateLevel(int userViewLevel, Integer targetLevel)`：targetLevel 为 null → 默认 L1 放行；非 null → `userViewLevel>=targetLevel` 否则抛 `ServiceException("无权创建 L{N} 级内容")`。
- userViewLevel 来源：后台走 `BlogPermissionResolver.resolve().view()`（admin 自然得 3）；前台无按钮权限键者同样走该 resolver（登录态 perms 已含 view:lN，未授权得 0=只能建 L1）。
- 闸只管"能不能创建此等级"，content/status/审核流仍按既有链路（新建即草稿→发布走审核）。

### 推荐算法（两段式现算）
- 第 1 步（登录用户）：`blog_like ∪ blog_collect` 关联 `blog_tag`，按 `collect*3 + like*1` 聚合取 Top-N 偏好 tag。**纳入文章标签后**：偏好信号额外包含"浏览过的文章 tag"（user_view_history biz_type=ARTICLE 关联 article_tag 取 tag）。
- 第 2 步：按偏好 tag 召回同 tag 公开博客（排除 excludeBlogId、排除已浏览），Service 层算分 `hotScore = tag命中数*5 + 收藏*3 + 点赞*2 + 浏览*1 + 时间衰减`，排序取 size 条。
- 兜底：未登录/无行为/召回不足 → 全局热门 `(like*2+collect*3+view) desc, publish_time desc` 补齐去重。
- **分级范围**：召回 SQL 的等级闸 = 分级开关关则 `level=1`，开关开则 `level<=userViewLevel`（Service 层用 BlogPermissionResolver.resolve().view() 注入，未登录/无权限=1）。未登录恒只召回 L1，符合公开门户语义；登录高等级用户分级开关开时才可能召回本级内容。

### BlogPortalVo（前台轻量 VO）
含 blogId/authorId/**authorNickname**(join sys_user，补这个既有缺口)/title/summary/coverUrl/publishTime/viewCount/likeCount/collectCount/tagIds/tagNames。**不含 content/审核字段/权限态**（访客无需）。列表类 SQL 不 select content。

### BlogPortalDetailVo（前台详情 VO，继承/叠加 BlogPortalVo）
在 BlogPortalVo 全部字段基础上**追加 `content`（正文）、`locked`(boolean)、`lockReason`(String)**，仍不含审核字段与权限态（canView/canEdit/isAuthor）。供 `/portal/blog/{blogId}` 出参：
- 正常（`blog.level<=userViewLevel`）：`locked=false`、`content` 下发完整正文、`lockReason=null`。
- 越级（`blog.level>userViewLevel`，含分级开关关时 userViewLevel 恒 1 的情况）：`locked=true`、`content` 置空（不下发只字正文）、`lockReason`="需 L{N} 权限查看完整正文"；其余元数据字段正常带出。
列表类接口继续用 BlogPortalVo（不 select content、不带 locked）。

---

## 第二条链路：浏览历史 + 浏览量 + 文章标签 + 标签排行榜

### 现状（已核查）
| 模块 | view_count | 标签关联 | 互动表 | 详情计数 |
|---|---|---|---|---|
| 博客 | 主表冗余列(Redis incr 孤儿) | blog_tag ✅ | blog_like/collect ✅ | Redis incr |
| 文章 | ❌ 无 | ❌ 无 article_tag | ❌ 无 | 无计数 |
| 资源 | ❌ 无 | ❌ 无 resource_tag | resource_like/collect/rating ✅ | 无计数，仅 download_count |

### 已拍板决策
1. **浏览量 = 事实表 `user_view_history`（计数源/防刷）+ 主表 `view_count` 冗余列（读快）双重存储**。
2. **标签只给文章加 `article_tag`，资源不加**；标签是博客和文章共用的，标签热度从 `blog_tag + article_tag` 统一计算。
3. **未登录不计浏览量**——未登录不让看详情（显示部分内容拒绝查看），不算统计量。recordView 只在登录态调用，user_id 始终来自登录态，不需要 IP 指纹兜底。
4. **推荐偏好纳入文章标签**：博客推荐偏好 tag 召回信号 = blog_like/blog_collect 的博客 tag + 浏览过的文章 tag。

### 新建 2 张表 + 改 3 张主表加列

#### 1. user_view_history（统一浏览明细事实表，三模块共用，防刷核心）
```
view_id PK / user_id / biz_type(BLOG/ARTICLE/CHAPTER/RESOURCE) / biz_id
view_time(首次) / last_view_time(最近) / view_count(同内容累计次数)
UNIQUE(user_id, biz_type, biz_id)  -- 同用户同内容只一行=去重防刷
KEY(user_id, view_time)            -- 历史分页
KEY(biz_type, biz_id)              -- 聚合浏览量
```
**防刷语义**：详情接口先查 (user_id,biz_type,biz_id)，命中→UPDATE 累加 view_count（主表不动）；未命中→INSERT（主表 view_count +1，记独立访客）。主表 view_count = 独立访客数，同一用户反复刷只增 1 次，刷不动。

#### 2. article_tag（文章标签关联，照搬 blog_tag 结构）
```
article_id / tag_id / create_time
PRIMARY KEY(article_id, tag_id)
KEY(tag_id) / KEY(article_id)
```
tag 表本身不改，文章复用同一批 tag。

#### 改主表加 view_count 列（幂等 ALTER patch）
- `article` + `chapter` + `resource` 各加 `view_count bigint NOT NULL DEFAULT 0` + 索引。
- `blog` 已有该列不动，但**废弃孤儿 Redis incrView**（incrView/CACHE_VIEW_PREFIX/incrViewCount 死代码删除），改走事实表。

### 计数流程（三模块统一）
详情接口（getBlogInfo/getArticleInfo/getChapterInfo/getResourceInfo）在确认登录态后、返回前调 `ViewHistoryService.recordView(userId, bizType, bizId)`：
1. 事务内 upsert user_view_history（命中累加，未命中 INSERT）；
2. 仅 INSERT 那次 UPDATE 主表 view_count +1（记独立访客）；
3. VO.viewCount 读主表（快，无 Redis）。
未登录态不调 recordView（直接返回，不计数）。

### 接口
**用户历史（登录，走 authenticated 兜底，不进 /portal/）**：
- `GET /history/list` — 当前用户浏览历史分页，入参 bizType(可选)/pageNum/pageSize，join blog/article/resource 带出标题封面，出参 `PageInfo<ViewHistoryVo>`。
- `DELETE /history/{ids}` — 删单条。
- `DELETE /history/clear` — 清空。

**标签排行榜（公开，进 /portal/，废弃上一轮的 /portal/blog/hot-tags）**：
- `GET /portal/tag/hot` — 统计 blog_tag + article_tag，按"关联的 PUBLISHED+公开内容数 + 总热度"排序，出参 `List<HotTagVo>(tagId/tagName/contentCount/hotScore)`。

---

## 落点文件（全部 com.knowhub.*，不碰 SecurityConfig 除外的一条链路）

### 新增 SQL
- `sql/knowhub-blog-portal.sql` — 第一条链路无建表（推荐全现算、无开关），可能空或仅注释。
- `sql/knowhub-view-history-tags.sql` — 2 建表 + 3 主表加列 patch，幂等；无新菜单/字典；续编前查 DB MAX 记 [[knowhub-sql-id-numbering-pitfall]]。

### 第一条链路新增/修改
- 新增 `controller/BlogPortalController.java`（5 读接口，无 @PreAuthorize）
- 新增 `controller/BlogAuthoringController.java`（`/authoring/blog/**`：draft/publish/后续 edit/revoke 写动作；`@PreAuthorize("isAuthenticated()")`，不进 permitAll。**不含点赞/收藏**——复用现有 `PUT /blog/like|collect/{id}`）
- 新增 `pojo/vo/BlogPortalVo.java` + `BlogPortalDetailVo.java`（含 locked/lockReason）+ `HotTagVo.java`
- 新增 `pojo/quarry/BlogPortalSearchQuarry.java`（keyword/tagIds/authorId/sort）
- 新增 `service/BlogPortalService.java` + `impl/BlogPortalServiceImpl.java`（推荐打分、搜索、view_count 回读、authorNickname 回填、**分级开关判定+userViewLevel 注入、详情越级锁态降级**集中在此）
- **新增/修改 service 层共用分级闸**：在 `BlogServiceImpl` 加 `assertCanCreateLevel(userViewLevel, targetLevel)`（或抽 BlogAuthSupport），后台 add 与前台创作各调一次（决策#11）
- 新增 `mapper/BlogPortalMapper.java` + `resources/mapper/blog/BlogPortalMapper.xml`（5 套独立 SQL，列表类不 select content；推荐第 1 步偏好 tag 查询纳入 article_tag；等级闸 `level<=#{userViewLevel}` 参数化，开关关时 Service 传 1）
- 新增 `config/PortalConfigReader.java`（读 `knowhub.portal.hierarchical.enabled`，照 BlogConfigReader 范式，SysConfigUtil.getBoolean，默认 false）
- 新增 `sql/knowhub-blog-portal-config.sql`（幂等 insert sys_config：configKey=`knowhub.portal.hierarchical.enabled`、value=`false`、默认启用；续编 config_id 前查 DB MAX，记 [[knowhub-sql-id-numbering-pitfall]]）
- **修改 1 处 rookie 文件**：`rookie-framework/.../config/SecurityConfig.java` 白名单加 `/portal/**` permitAll（读）+ `/authoring/**` 走 authenticated 兜底（写）；不混在一条规则里

### 第二条链路新增/修改
- 新增 entity `UserViewHistory`/`ArticleTag`、mapper + xml
- 新增 `ViewHistoryService` + Impl（recordView 统一计数 + listHistory/deleteHistory/clearHistory）
- 新增 `HistoryController`(/history)、`ViewHistoryVo`
- 改 4 个 ServiceImpl 详情加 recordView（Blog 删孤儿 Redis 计数）
- 前台 `/portal/blog/{blogId}` 详情：越级锁态不调 recordView（未达权限不算浏览量），正常达权才计；与第二条链路决策#3"未登录不计浏览量"同构
- Article/Chapter/Resource 实体 + VO 加 viewCount
- ArticleVo/ArticleQuarry 加 tagIds/tagNames
- ArticleMapper.xml quarryArticle 加 tagIds join 分支（照博客 quarryBlog 的 group by having count 范式）+ 文章编辑先删后插标签（照 BlogServiceImpl.saveBlogTags）
- 前端 knowhub-ui 加 /history 页 + 博客搜索(notes)/详情(detail)接真实接口（首页 mock 不动）；前台无文章编辑入口，文章标签选择落点只在后台 rookie-ui
- 后台 rookie-ui 文章管理编辑弹窗加标签

### 不动
- BlogVo（不加 rating）
- 不新建用户偏好/画像表
- 不加 SysConfig 总开关（决策#7"数据获取接口一律不加开关"）；分级推荐开关 `knowhub.portal.hierarchical.enabled` 是**唯一例外**（决策#8），因其控制的是"等级闸放宽上限"属于安全语义而非数据获取开关，且默认关=严格、无越级风险
- SecurityConfig 第一条链路改两处规则：`/portal/**` permitAll（读）、`/authoring/**` authenticated 兜底（写）；第二条链路的历史 `/history/**` 走 authenticated、标签排行 `/portal/tag/hot` 进既有 /portal/**

---

## 两条链路的关系
配套打通：浏览历史给推荐补上了"用户看过"信号（之前推荐只能靠 like/collect，现在可纳入浏览过的文章 tag 作偏好），两个方案打通。记忆 [[knowhub-blog-portal-recommend-search]] + [[knowhub-view-history-tags]]。

## 建议实施顺序
1. 建表 SQL（2 张表 + 3 主表加列）
2. ViewHistoryService 统一计数 + 4 个 ServiceImpl 详情接入
3. 历史接口（/history/*）
4. 文章标签关联（实体/mapper/编辑先删后插/列表过滤）
5. 第一条链路：SecurityConfig 放开 /portal/** → BlogPortalController 四接口
6. 标签排行榜接口（/portal/tag/hot，统计 blog_tag+article_tag）
7. 前端 /history 页 + 文章标签选择 + 前台博客搜索/推荐页接真实接口
