/**
 * 文件作用：
 * 定义文章/章节创作对接后端 /authoring/article/**、/authoring/chapter/** 的接口类型，与后端 ArticleVo/ChapterVo 对齐。
 * - 文章创作提交载荷（ArticleAuthoringPayload）只带创作相关字段：标题/前言/等级/可见性/封面/标签。
 *   封面 coverObjectKey 存 /file/resolve/{objectId} 稳定引用（与后台 ArticleCoverUploader 同口径——上传 PUBLIC 对象后回填 resolve 引用，与博客 coverUrl 同流程，列名沿用后端 cover_object_key）。
 * - 章节创作精简（ChapterAuthoringPayload）：章节名 + 排序 + 正文（章节不分等级、可见性随文章、无标签无封面）。
 * - 编辑回填（ArticleAuthoringDetail/ChapterAuthoringDetail）消费后端 getArticleInfo/getChapterInfo 返回的子集。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 */

/**
 * 文章创作提交载荷（与后端 ArticleVo 创作相关字段对齐，只读字段不带）。
 * - articleId：编辑时带（后端 editArticleInfo 校验归属+状态机），草稿新建不带。
 * - level：1=公开(默认) 2=内部 3=机密；非作者提升 level 需自身 edit>=新 level，后端兜底校验。
 * - visibility：PRIVATE 未公开(默认) / SEMIPUBLIC 半公开 / PUBLIC 全公开（决定章节提交审不审，与等级正交）。
 * - coverObjectKey：封面上传后回填 /file/resolve/{objectId} 稳定引用，可空。
 * - tagIds：受控标签 id 列表，后端 validateArticleTagIds 校验必须落在启用集，可空（标签非必填）。
 */
export interface ArticleAuthoringPayload {
  articleId?: number
  title: string
  summary?: string
  level?: number
  visibility?: string
  coverObjectKey?: string
  tagIds?: number[]
}

/**
 * 文章编辑回填数据（getArticleInfo 返回的 ArticleVo 子集，前端创作页只消费这些字段）。
 * - status：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED，驱动按钮态（PUBLISHED 须先撤回才可编辑）。
 * - canEdit/isAuthor：后端回填，非作者/不可编辑则退回。
 * 其余字段（viewCount/审核流水等）回填后忽略。
 */
export interface ArticleAuthoringDetail {
  articleId: number
  title: string
  summary?: string
  level?: number
  visibility?: string
  coverObjectKey?: string
  status?: string
  tagIds?: number[]
  tagNames?: string[]
  isAuthor?: boolean
  canEdit?: boolean
  /**
   * 后台文章可见性三档（PRIVATE/SEMIPUBLIC/PUBLIC）。章节提交状态机据此：
   * 作者提交任意 visibility 免审直 PUBLISHED；非作者 PRIVATE 拒、SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW、PUBLIC 免审。
   * 创作页据此提示作者"半公开时他人提交章节需你审核"。
   */
}

/** 我的文章列表查询参数（前台 /authoring/article/list，薄封装 quarryArticle 走作者分支） */
export interface MyArticleListQuery {
  /** 可选状态过滤（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED），留空取全部我可看的 */
  status?: string
  pageNum?: number
  pageSize?: number
}

/**
 * 我的文章列表项（后台 ArticleVo 子集，前台消费列表展示）。
 * 复用 quarryArticle 出参，coverObjectKey 列存 /file/resolve/{id} 引用可直接当封面 URL 用。
 */
export interface ArticleRecord {
  articleId: number
  title: string
  summary?: string
  coverObjectKey?: string
  level?: number
  visibility?: string
  status?: string
  reviewStatus?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  publishTime?: string
  createTime?: string
  updateTime?: string
  tagNames?: string[]
  tagIds?: number[]
}

/**
 * 章节创作精简载荷（仅章节名 + 排序 + 正文，章节不分等级/可见性随文章/无标签无封面）。
 * - chapterId：编辑时带，新建不带。
 * - articleId：章节所属文章，提交/编辑必带。
 * - sortOrder：章节排序（小→大），后端缺省 0。
 */
export interface ChapterAuthoringPayload {
  chapterId?: number
  articleId: number
  chapterName: string
  sortOrder?: number
  content: string
}

/**
 * 章节编辑回填（getChapterInfo 返回的 ChapterVo 子集，前台只消费创作字段+状态+权限态）。
 * - status：DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED，驱动按钮态（PUBLISHED 须先撤回才可改）。
 * - canEdit/canReview：后端回填。
 */
export interface ChapterAuthoringDetail {
  chapterId: number
  articleId: number
  chapterName: string
  sortOrder?: number
  content?: string
  status?: string
  /** 章节作者昵称（join sys_user 带出，列表展示用） */
  authorNickname?: string
  /** 所属文章标题（join article 带出，章节管理页头部展示用） */
  articleTitle?: string
  canEdit?: boolean
  canReview?: boolean
}

/** 我的章节列表查询参数（前台 /authoring/chapter/list，需 articleId 指定所属文章） */
export interface MyChapterListQuery {
  articleId: number
  /** 可选状态过滤 */
  status?: string
  pageNum?: number
  pageSize?: number
}