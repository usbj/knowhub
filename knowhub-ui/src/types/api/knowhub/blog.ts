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
}

/** 搜索查询条件（GET /portal/blog/search） */
export interface BlogPortalSearchQuery {
  keyword?: string
  tagIds?: number[]
  authorId?: number
  /** RELEVANCE 相关度 / HOT 热度 / LATEST 最新 */
  sort?: 'RELEVANCE' | 'HOT' | 'LATEST'
}
