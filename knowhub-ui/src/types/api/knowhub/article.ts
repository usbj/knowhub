/**
 * 文件作用：
 * 前台文章门户 VO 类型，与后端 com.knowhub.pojo.article.vo.ArticlePortalVo / ArticlePortalDetailVo /
 * ChapterContentVo / MatchedChapterVo / ChapterOutlineVo 对齐。
 * 文章 = 章节集合（文档站结构），列表不带正文，详情给章节大纲，正文走章节接口按需拉取。
 * matchedChapters 仅搜索接口填充（章节正文命中时标出对应章节，便于结果点击跳章节阅读页）。
 */

/** 搜索命中章节（仅章节级，不带正文片段；按 article 聚合返回） */
export interface MatchedChapterRecord {
  chapterId: number
  articleId: number
  chapterName: string
  sortOrder?: number
}

/** 章节大纲项（详情接口的章节列表，点章节跳阅读页） */
export interface ChapterOutlineRecord {
  chapterId: number
  chapterName: string
  sortOrder?: number
}

/** 前台文章列表项（搜索/推荐/相关推荐出参） */
export interface ArticlePortalRecord {
  articleId: number
  authorId?: number
  authorNickname?: string
  title: string
  summary?: string
  /** 封面 URL（/file/resolve/{objectId} 形态，无封面为 null） */
  coverUrl?: string | null
  /** 文章等级 1公开/2内部/3机密（前台可据此提示） */
  level?: number
  publishTime?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  /** 章节数量（后端回填，文档站章节数展示用） */
  chapterCount?: number
  tagIds?: number[]
  tagNames?: string[] | null
  /** 搜索命中的章节列表（仅 search 接口填充，其余接口为空） */
  matchedChapters?: MatchedChapterRecord[]
}

/** 前台文章详情（继承列表字段 + 章节大纲 + 锁态；正文不在 article 主表，走章节接口） */
export interface ArticlePortalDetailRecord extends ArticlePortalRecord {
  /** 章节大纲（章节名+排序，不含正文；越级时仍下发大纲，不泄正文） */
  chapterList?: ChapterOutlineRecord[]
  /** 是否越级锁态：true=无权看完整内容，章节接口也会锁态拒发正文 */
  locked?: boolean
  /** 锁态原因提示（如"需 L2 权限查看完整内容"，正常态为 null） */
  lockReason?: string | null
}

/** 章节正文（GET /portal/article/{articleId}/chapter/{chapterId}） */
export interface ChapterContentRecord {
  chapterId: number
  articleId: number
  chapterName?: string
  sortOrder?: number
  /** 章节正文 markdown（越级锁态时为 null） */
  content?: string | null
  /** 是否越级锁态 */
  locked?: boolean
  lockReason?: string | null
}

/** 搜索查询条件（GET /portal/article/search） */
export interface ArticlePortalSearchQuery {
  keyword?: string
  tagIds?: number[]
  authorId?: number
  /** RELEVANCE 相关度 / HOT 热度 / LATEST 最新 */
  sort?: 'RELEVANCE' | 'HOT' | 'LATEST'
}