/**
 * mock 博客数据
 * ------------------------------------------------------------------
 * 笔记导航列表 + 首页推荐 + 详情。真实接口：GET /blog/list、GET /blog/{id}（已落地）。
 * 缺评分/观看/全文搜索接口（见计划）。
 */
import { tags } from './tag'

export interface MockBlog {
  /** 博客ID（对齐后端 BlogVo.blogId） */
  blogId: number
  title: string
  summary: string
  /** 封面图（对齐后端 BlogVo.coverUrl；前端 mock 用渐变色块占位） */
  coverUrl?: string
  /**
   * 【前端私加】后端 BlogVo 只有 createBy(username)，无 authorNickname（缺口 #13）。
   * mock 用"作者昵称"展示，接入时需后端 join sys_user 补 authorNickname，
   * 否则前端只能展示账号名 createBy。见 knowhub-ui README.dev.md §11.1。
   */
  authorNickname: string
  /** 作者 userId（对齐后端 BlogVo.authorId） */
  authorId: number
  tags: string[]
  /**
   * 【前端私加】后端 BlogVo 无 rating 字段，counts 仅 viewCount/likeCount/collectCount（缺口 #12）。
   * mock 占位供详情头星级展示，接入前需推动后端补评分聚合字段 + 评分接口（缺口 #10）。
   */
  rating: number
  /** 阅读数（对齐后端 BlogVo.viewCount） */
  viewCount: number
  /** 点赞数（对齐后端 BlogVo.likeCount） */
  likeCount: number
  /** 收藏数（对齐后端 BlogVo.collectCount） */
  collectCount: number
  publishTime: string
  /** 状态（对齐后端 BlogVo.status）：DRAFT/PUBLISHED/PENDING_REVIEW/REJECTED/REVOKED */
  status: 'DRAFT' | 'PUBLISHED' | 'PENDING_REVIEW' | 'REJECTED' | 'REVOKED'
  /** 正文（静态预渲染文本，详情页用；对齐后端 BlogVo.content） */
  content: string
}

const tagNames = (ids: number[]) => ids.map((i) => tags[i - 1]?.name).filter(Boolean) as string[]

export const blogs: MockBlog[] = [
  {
    blogId:1,
    title: 'Spring Boot 3.4 多模块项目实战：从脚手架到生产部署',
    summary: '记录 knowhub 后端从 rookie 基础框架二次开发为综合知识库系统的全过程，涵盖 Maven 多模块划分、模块边界、依赖治理与启动配置。',
    coverUrl: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
    authorNickname: '陈一帆',
    authorId: 2,
    tags: tagNames([1, 3, 14]),
    rating: 4.8,
    viewCount: 2841,
    likeCount: 312,
    collectCount: 158,
    publishTime: '2026-07-09 14:32',
    status: 'PUBLISHED',
    content:
      '## 背景\n\nknowhub 在 rookie 后台管理框架之上二次开发，目标是承载博客、项目、资源、审核等上层业务。本文沉淀多模块划分的取舍过程。\n\n## 模块划分\n\n- rookie-admin：启动与控制器入口\n- rookie-common：公共基础能力\n- rookie-framework：安全与鉴权\n- rookie-system：用户/角色/菜单/字典/通知/日志\n- knowhub-blog：知识库博客业务\n\n## 关键决策\n\n1. **不修改 rookie-* 任何代码**：上游框架层稳定，二开产物全部归 knowhub 模块\n2. **业务实体继承框架 BaseEntity**：引用框架、不在框架里加内容\n3. **统一返回/分页/异常**复用 rookie-common\n\n> 这一节的代码示例会在后续章节展开……',
  },
  {
    blogId:2,
    title: 'Vue 3 + Pinia 中大型项目目录约定与组件复用实践',
    summary: '以 rookie-ui 的 SharedTablePanel/SharedFormPanel 为例，谈 schema 驱动的通用表格与表单如何让一个列表页只写 config.ts。',
    coverUrl: 'linear-gradient(135deg,#16a34a,#0ea5e9)',
    authorNickname: '周牧',
    authorId: 4,
    tags: tagNames([2, 10, 17]),
    rating: 4.6,
    viewCount: 1923,
    likeCount: 204,
    collectCount: 96,
    publishTime: '2026-07-08 09:15',
    status: 'PUBLISHED',
    content:
      '## 为什么要 schema 驱动\n\n后台管理系统里，列表页 80% 的工作是：筛选项 + 表格列 + 弹窗表单。把这些抽成 schema 后，一个页面只剩 config.ts 与少量交互。\n\n## SharedTablePanel 的字段配置\n\n每个字段描述 type/label/options/rules，组件按 schema 渲染……',
  },
  {
    blogId:3,
    title: 'RustFS 对象存储 + 预签名直传：文件上传安全落地',
    summary: '为什么选 RustFS 而不是直接存本地、预签名直传如何避免后端经手大文件字节、中转与直链双模式如何切换。',
    coverUrl: 'linear-gradient(135deg,#f59e0b,#fde68a)',
    authorNickname: '何川',
    authorId: 6,
    tags: tagNames([11, 6, 15]),
    rating: 4.9,
    viewCount: 3204,
    likeCount: 421,
    collectCount: 234,
    publishTime: '2026-07-06 21:48',
    status: 'PUBLISHED',
    content:
      '## 选型理由\n\nRustFS 是 S3 兼容的本地自建对象存储，承载博客配图、项目源码压缩包、资源文件等。\n\n## 预签名直传链路\n\n1. 前端请求 POST /file/upload-token 拿到预签名 URL\n2. 前端直传 RustFS\n3. 前端调 POST /file/confirm 确认入库\n\n后端不经流文件字节，对齐"文件上传安全"目标……',
  },
  {
    blogId:4,
    title: '内容审核范式：状态机 + 回避 + 流水表 + 对账定时任务',
    summary: '博客/资源/项目三个模块共用一套审核范式的设计推导，附对账任务如何处理"审核开关关闭后遗留待审内容"。',
    coverUrl: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    authorNickname: '陈一帆',
    authorId: 2,
    tags: tagNames([3, 17, 12]),
    rating: 4.7,
    viewCount: 2156,
    likeCount: 268,
    collectCount: 187,
    publishTime: '2026-07-04 16:20',
    status: 'PUBLISHED',
    content:
      '## 审核状态机\n\nDRAFT → PENDING_REVIEW → PUBLISHED / REJECTED → REVOKED → 可重发\n\n## 审核员回避\n\nuserId ≠ author_id，作者不能审自己。\n\n## 流水表\n\nreview_log 记录每次动作（SUBMIT/APPROVE/REJECT/REVOKE）+ 操作人 + 时间 + 意见……',
  },
  {
    blogId:5,
    title: '博客权限模型收紧记：从分等级编辑到作者 + 超管',
    summary: '为什么把博客编辑权限从 view:l1-l3 等级制收紧为"作者 + 超级管理员"短路，以及 rookie admin 短路如何复用。',
    coverUrl: 'linear-gradient(135deg,#dc2626,#fca5a5)',
    authorNickname: '林溪',
    authorId: 1,
    tags: tagNames([12, 13, 1]),
    rating: 4.5,
    viewCount: 1487,
    likeCount: 156,
    collectCount: 78,
    publishTime: '2026-07-11 11:02',
    status: 'PUBLISHED',
    content:
      '## 收紧动机\n\n博客的编辑场景与查看不同：编辑是"改我的内容"，不该按等级放开。于是收紧为作者本人 + 超级管理员。\n\n## admin 短路\n\n复用 rookie 登录时 admin 角色已物理塞入全部 perm_key 的机制，无需额外 isAdmin 判断……',
  },
  {
    blogId:6,
    title: 'GitHub 式文件树：project_file 与 file_object 分工设计',
    summary: '项目内文件树骨架与对象存储元数据如何解耦，一个项目拉全树前端内存组装 parent→children 递归渲染。',
    coverUrl: 'linear-gradient(135deg,#0f766e,#5eead4)',
    authorNickname: '叶禾',
    authorId: 5,
    tags: tagNames([11, 2, 3]),
    rating: 4.6,
    viewCount: 1734,
    likeCount: 198,
    collectCount: 124,
    publishTime: '2026-07-07 19:35',
    status: 'PUBLISHED',
    content:
      '## 两层分工\n\n- file_object：对象存储元数据（扁平，对接 RustFS）\n- project_file：项目内目录树骨架，叶子 object_id 指向 file_object\n\n## 前端组装\n\n按 project_id 拉全树，内存里 parent→children 递归渲染展开/折叠……',
  },
  {
    blogId:7,
    title: 'AI 日报最小闭环：定时驱动 LLM 生成 + 前台只读展示',
    summary: 'AI 模块第一个落地场景的设计：定时任务驱动、管理员提示词定制内容范围、前台首页卡片 + 独立日报页。',
    coverUrl: 'linear-gradient(135deg,#f59e0b,#fbbf24)',
    authorNickname: '苏念',
    authorId: 3,
    tags: tagNames([16, 1, 3]),
    rating: 4.4,
    viewCount: 982,
    likeCount: 87,
    collectCount: 45,
    publishTime: '2026-07-12 07:00',
    status: 'PUBLISHED',
    content:
      '## 定位\n\n后台只管配置不管内容；前台只读，不提供编辑入口。\n\n## 触发\n\n定时任务（默认早 7:00）驱动 LLM 生成当日报道，内容范围由管理员提示词定制……',
  },
  {
    blogId:8,
    title: 'MySQL 中等正文入库 vs 分表：blog_content 单独建表的取舍',
    summary: '为什么把 Markdown 正文从 blog 主表拆到 blog_content 单独建表，列表分页 SELECT 不带正文的性能考量。',
    coverUrl: 'linear-gradient(135deg,#0ea5e9,#7dd3fc)',
    authorNickname: '何川',
    authorId: 6,
    tags: tagNames([4, 16, 15]),
    rating: 4.7,
    viewCount: 2401,
    likeCount: 287,
    collectCount: 201,
    publishTime: '2026-07-05 13:12',
    status: 'PUBLISHED',
    content:
      '## 问题\n\nMEDIUMTEXT 正文列污染列表分页 SELECT，分页查询拉回大量不需要的正文。\n\n## 方案\n\n正文与博客元数据单独建表 blog_content，列表查询不带正文……',
  },
  {
    blogId:9,
    title: 'Redis 缓存与数据库一致性：系统设置即时生效的语义',
    summary: 'sys_config 编辑保存写库后立即重写 Redis、绕过接口直接改 DB 时点刷新缓存兜底、启动预热三段式。',
    coverUrl: 'linear-gradient(135deg,#f59e0b,#fed7aa)',
    authorNickname: '陈一帆',
    authorId: 2,
    tags: tagNames([5, 3, 15]),
    rating: 4.3,
    viewCount: 1124,
    likeCount: 98,
    collectCount: 67,
    publishTime: '2026-07-03 10:45',
    status: 'PENDING_REVIEW',
    content: '## 三段式缓存语义\n\n1. 编辑保存：写库后立即 setConfig 重写 Redis，即时生效\n2. 绕过接口改 DB：点「刷新缓存」兜底\n3. 启动：WarmUpRunner 首次预热……',
  },
  {
    blogId:10,
    title: 'TS 类型与后端 VO 对齐：时间字段为什么用 Date 而不是 String',
    summary: '后端 jackson 全局 date-format 自动序列化，VO 用 Date 比 String 更干净，前端 TS 仍标 string 契约不变。',
    coverUrl: 'linear-gradient(135deg,#6366f1,#c7d2fe)',
    authorNickname: '周牧',
    authorId: 4,
    tags: tagNames([10, 2, 17]),
    rating: 4.5,
    viewCount: 876,
    likeCount: 72,
    collectCount: 58,
    publishTime: '2026-07-02 15:28',
    status: 'DRAFT',
    content: '## 时间字段的类型选择\n\nVO/实体/Query 的时间属性一律用 java.util.Date，不要用 String……',
  },
  {
    blogId:11,
    title: 'Docker Compose 本地起 RustFS + MySQL + Redis 一体化开发环境',
    summary: '一份 docker-compose.yml 跑起整套后端依赖，新人 clone 即可启动，附 path-style 访问踩坑。',
    coverUrl: 'linear-gradient(135deg,#2563eb,#93c5fd)',
    authorNickname: '林知夏',
    authorId: 8,
    tags: tagNames([6, 11, 15]),
    rating: 4.8,
    viewCount: 2678,
    likeCount: 334,
    collectCount: 198,
    publishTime: '2026-07-01 18:09',
    status: 'PUBLISHED',
    content: '## 一体化 compose\n\n本地起 RustFS + MySQL + Redis，新人 clone 即可启动……',
  },
  {
    blogId:12,
    title: '算法竞赛备赛笔记：动态规划从背包到区间 DP',
    summary: '安以同学的比赛备赛整理，从 01 背包、完全背包到区间 DP 与树形 DP 的递进式理解。',
    coverUrl: 'linear-gradient(135deg,#16a34a,#bbf7d0)',
    authorNickname: '安以',
    authorId: 7,
    tags: tagNames([7, 17]),
    rating: 4.6,
    viewCount: 1542,
    likeCount: 176,
    collectCount: 132,
    publishTime: '2026-06-28 20:15',
    status: 'PUBLISHED',
    content: '## DP 递进\n\n从 01 背包到区间 DP……',
  },
]

export const getBlogById = (id: number): MockBlog | undefined => blogs.find((b) => b.blogId === id)
