/**
 * 文件作用：
 * 前台博客门户 VO 类型，与后端 com.knowhub.pojo.vo.BlogPortalVo / BlogPortalDetailVo 对齐。
 * 前台只消费展示子集（列表不含 content；详情含 content + 锁态）。
 * 注意 authorNickname：后端 join sys_user on author_id 带出（author_id 由 knowhub-blog-review-log.sql 追加）。
 * rating 是前端私加（后端 BlogVo 无评分字段，缺口），列表/详情均不展示评分，故此处不声明。
 */

/** 前台博客列表项（搜索/推荐/相关推荐出参） */
export interface BlogPortalRecord {
  blogId: number
  authorId?: number
  authorNickname?: string
  title: string
  summary?: string
  coverUrl?: string
  publishTime?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  /** 标签 id 列表（后端回填，可能为空数组） */
  tagIds?: number[]
  /** 标签名列表（后端当前置空，前端按 tagIds 映射标签云名称） */
  tagNames?: string[] | null
}

/** 前台博客详情（继承列表字段 + 正文 + 锁态） */
export interface BlogPortalDetailRecord extends BlogPortalRecord {
  /** 正文（越级锁态时为 null，不下发只字正文） */
  content?: string | null
  /** 是否越级锁态：true=无权看完整正文，只给元数据 */
  locked?: boolean
  /** 锁态原因提示（如"需 L2 权限查看完整正文"，正常态为 null） */
  lockReason?: string | null
  /** 博客等级 1/2/3（service 判越级用，前台可据此提示内容等级） */
  level?: number
  /** 当前用户是否已点赞（登录态回填，未登录为 null；详情接口下发，列表接口为空） */
  hasLiked?: boolean | null
  /** 当前用户是否已收藏（登录态回填，未登录为 null；详情接口下发，列表接口为空） */
  hasCollected?: boolean | null
  /** 评论区开关 1开/0关（详情接口带出，前端据此渲染评论区开关态） */
  commentEnabled?: number
  /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
  commentCurated?: number
}

/** 搜索查询条件（GET /portal/blog/search） */
export interface BlogPortalSearchQuery {
  keyword?: string
  tagIds?: number[]
  authorId?: number
  /** RELEVANCE 相关度 / HOT 热度 / LATEST 最新 */
  sort?: 'RELEVANCE' | 'HOT' | 'LATEST'
  /**
   * 分页页码（后端 PageUtil.startPage 从 HTTP 请求读，默认 1）。
   * 前台分页需显式带，与 ProjectPortalSearchQuery/ArticlePortalSearchQuery 同口径。
   */
  pageNum?: number
  /** 分页每页条数（后端 PageUtil 从 HTTP 请求读，默认 10） */
  pageSize?: number
}

/**
 * 前台博客全量统计（GET /portal/blog/stats）。
 * 固定口径：已发布且当前用户可见的博客数 + 累计阅读数 + 启用标签总数。
 * 与搜索/翻页/标签过滤无关——前端进入页面拉一次定盘展示，避免统计随筛选结果 total 变动。
 */
export interface PortalBlogStatsRecord {
  /** 已发布且当前用户可见等级内的博客数 */
  publishedBlogCount: number
  /** 上述博客的累计阅读数（sum view_count 主表冗余列） */
  totalReads: number
  /** 启用标签总数（与 /portal/tag/hot 同口径，hot 只回 Top-N，全量数在此给） */
  tagCount: number
}
