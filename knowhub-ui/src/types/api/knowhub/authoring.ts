/**
 * 文件作用：
 * 定义博客创作对接后端 /authoring/blog/** 的接口类型，与后端 BlogVo 对齐。
 * 创作提交载荷（BlogAuthoringPayload）只带后端接纳的创作相关字段（只读计数/审核字段/作者字段不带）；
 * 编辑回填（BlogAuthoringDetail）消费后端 getBlogInfo 返回的 BlogVo 子集。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 */

/**
 * 创作提交载荷（与后端 BlogVo 创作相关字段对齐，只读字段不带）。
 * - blogId：编辑时带（后端 editBlogInfo 需校验归属），草稿新建不带。
 * - level：1=公开(默认) 2=内部 3=机密；后端 assertCanCreateLevel 拦越级创建。
 * - coverUrl：封面上传后回填 /file/resolve/{objectId} 稳定引用，可空。
 * - tagIds：受控标签 id 列表，后端 validateTagIds 校验必须落在启用集，可空（标签非必填）。
 */
export interface BlogAuthoringPayload {
  blogId?: number
  title: string
  content: string
  summary?: string
  coverUrl?: string
  level?: number
  tagIds?: number[]
  /** 评论区开关 1开/0关，作者在创作页勾选 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，开启后新评论需作者同意后对他人展示 */
  commentCurated?: number
}

/**
 * 编辑回填数据（getBlogInfo 返回的 BlogVo 子集，前端创作页只消费这些字段）。
 * - status：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED，驱动按钮态（PUBLISHED 须先撤回才可编辑）。
 * - isAuthor/canEdit：后端回填，前端据此判断是否允许编辑（非作者/不可编辑则退回）。
 * 其余字段（点赞/收藏计数、审核流水等）回填后忽略。
 */
export interface BlogAuthoringDetail {
  blogId: number
  title: string
  content: string
  summary?: string
  coverUrl?: string
  level?: number
  status?: string
  tagIds?: number[]
  tagNames?: string[]
  isAuthor?: boolean
  canEdit?: boolean
  /** 评论区开关 1开/0关，编辑回填用 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，编辑回填用 */
  commentCurated?: number
}

/** 我的博客列表查询参数（前台 /authoring/blog/list） */
export interface MyBlogListQuery {
  /** 可选状态过滤（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED），留空取全部我可看的 */
  status?: string
  pageNum?: number
  pageSize?: number
}

/**
 * 我的博客列表项（后台 BlogVo 子集，前台消费列表展示字段）。
 * 复用 quarryBlog 出参，只取展示需要的字段，其它字段（content/审核/权限态）忽略。
 */
export interface BlogRecord {
  blogId: number
  title: string
  summary?: string
  coverUrl?: string
  level?: number
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