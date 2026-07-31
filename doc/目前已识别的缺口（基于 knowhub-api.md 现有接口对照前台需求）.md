目前已识别的缺口（基于 knowhub-api.md 现有接口对照前台需求）：

1. **首页聚合接口**：现有接口都是单模块 list，缺一个首页聚合推荐接口（综合返回推荐博客/项目/资源/公告 + 统计），否则前台首页要并发调 4-5 个 list 拼装。
2. **公告展示接口**：rookie 有通知模块（`SysNotice`/`SysNoticeGroup`），但前台需要的"全站公告轮播"需要 `GET /notice/public/list` 之类的公开公告接口（当前通知是后台管理 + "我的通知"，无面向访客的公开公告接口）。
3. **标签热门度接口**：~~笔记导航要展示"标签最多/标签排行"，需要 `GET /tag/hot`（按博客数排序的标签列表 + count），当前 `GET /tag/list` 不带使用计数排序。~~ ✅ 已解决：`GET /portal/tag/hot`（2026-07-15 博客前台门户落地，统计 blog_tag+article_tag 关联的 PUBLISHED+公开内容 score 之和，2026-07-29 文章分支维度升级为 like×2+collect×3+view 与博客对齐）。前台/notes、前台/docs 共用同一榜单。
4. **博客搜索接口**：~~§11.2 笔记导航要"内容模糊搜索"，当前 `GET /blog/list` 走 quarry 参数，能否全文搜正文待确认；可能需要独立搜索接口或 Elasticsearch（§8.1 提及）。~~ ✅ 已解决：`GET /portal/blog/search`（2026-07-15，FULLTEXT ngram 标题+正文，RELEVANCE/HOT/LATEST 排序，分页）。
5. **文档搜索接口**：~~文档学习页"专门文档搜索"，章节集合型文章的搜索接口当前未见（文章模块接口待查 knowhub-api.md）。~~ ✅ 已解决：`GET /portal/article/search`（2026-07-29 文章前台门户落地，FULLTEXT ngram 覆盖文章标题/简介 + 章节正文，命中章节由 matchedChapters 标出便于跳章节阅读页）。
6. **项目活跃度接口**：项目展示要"最活跃项目"，需按活跃度（下载量/更新时间/成员数）排序的 `GET /project/active`，当前 `GET /project/list` 排序能力待确认。
7. **资源分类聚合/热门下载榜接口**：资源推荐页要"热门下载榜 + 分类聚合"，需 `GET /resource/hot` + 分类维度聚合。
8. **个人中心聚合接口**：用户全部创作内容（博客/文章/项目/资源）+ 收藏 + 统计，缺一个 `/profile/overview` 聚合接口。
9. **AI 日报接口**：§10.3.1 设计了但未实施，前台首页卡片 + 独立日报页需要 `GET /ai-daily/latest` + `GET /ai-daily/list` + `GET /ai-daily/{date}`。
10. **评分/观看/反馈接口**：§11.2 提到评分星级、观看人数、问题反馈，当前博客有 like/collect，但评分(rating)与反馈(feedback)接口未见。

---

### 2026-07-13 补登记（项目详情子页面化 + 博客/项目前台展示项核对新增）

本轮前台改项目详情为"介绍 / 项目文件"双子页 + 文件项右侧展示上传时间/大小/下载，并存项目/博客卡片标记了下述私加展示项——均无后端 VO 支撑，接入前需补：

11. **项目文件树 VO 缺时间字段**：`GET /project/file/tree/{projectId}` 返回 `ProjectFileTreeVo` 字段为 fileId/projectId/parentId/name/isDir/objectId/sort/originalName/contentLength/contentType/businessType/children，**无任何时间字段**。前台文件子页"上传时间"列无后端支撑。建议后端在 VO 回填一个时间（取 `file_object.create_time` 或 `project_file.create_time` join 出来，命名建议 `uploadTime`/`createTime`）。前端 [mock/project.ts](../knowhub-ui/src/mock/project.ts) 的 `MockProjectFile.uploadTime` 是**前端私加**占位，接入时据后端回填字段名映射。
12. **博客 VO 缺评分 rating 字段**：`BlogVo`（列表/详情）counts 仅有 `viewCount/likeCount/collectCount`，无 rating。前台博客详情头/星级展示、列表行评分都消费 `rating`，目前全部走 mock 私加（[mock/blog.ts](../knowhub-ui/src/mock/blog.ts) `MockBlog.rating`）。需后端补 rating 聚合字段 + 评分写入接口（与缺口 #10 合并推进）。
13. **博客 VO 缺 authorNickname**：`BlogVo` 列表/详情只有 `createBy`(username)，无 join 出的昵称。前台博客卡片/详情头展示作者名（mock `author`）目前按 createBy 映射会暴露账号而非昵称——项目 `ProjectVo`、文章 `ArticleVo` 都已 join sys_user 回填 authorNickname，博客模块未补，应对齐补 authorNickname。
14. **项目 VO 缺 downloadCount**：`ProjectVo` 字段无下载量。前台项目卡片底部"下载量"StatPill、列表"按下载量排序"提示、详情头下载量都消费这个字段（mock 私加，见 [mock/project.ts](../knowhub-ui/src/mock/project.ts)）。需后端在 ProjectVo 补 downloadCount 聚合；活跃度/下载量排序接口见缺口 #6。注：原 mock `activity` 活跃度综合分字段已从前端移除——"最活跃"徽标与列表排序改用 downloadCount，活跃度综合分不进 VO，不作为缺口。
15. **项目无封面是既定事实**：本轮已把项目列表 ProjectCard 与详情头去掉封面卡片，无需后端补字段；记录此处说明 mock 的 `cover`/`icon` 是纯前端占位、不应进 ProjectVo（类型/等级徽标 + 标题即项目卡主视觉）。