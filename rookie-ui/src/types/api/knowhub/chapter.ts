/**
 * 文件作用：
 * 定义章节管理模块对接后端所需的接口类型，
 * 与后端 ChapterVo / ChapterQuarry / ChapterReviewVo / ChapterReviewLogVo 对齐，
 * 供 api 层和页面层统一消费。
 * 章节是文章的子模块（无独立菜单页，从文章列表点"章节"跳二级路由页）。
 * 关键约定：
 * - 时间字段沿用后端 Date 经 jackson 序列化输出的 string 形式（契约不变），展示用 formatDateTime。
 * - status 见字典 chapter_status（DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED，多一个待作者审）；
 *   reviewStatus 见字典 review_status（复用）；action 见字典 review_action（章节审核仅 SUBMIT/APPROVE/REJECT 三值）。
 * - 章节不分等级，可见性=文章可见性（articleVisibility join article 带出）。
 * - 权限态 canEdit/canReview 由后端详情接口回填，供前端控制按钮显隐。
 * - content 是 mediumtext 大字段：列表不带，详情才回填。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 章节记录，与后端 ChapterVo 字段对齐。
 * 章节≈博客，正文走主表不分表（content mediumtext，列表不带，详情才回填）。
 */
export interface ChapterRecord {
  chapterId?: number
  articleId: number
  chapterName: string
  sortOrder?: number
  authorId?: number
  /** 章节作者昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  /** Markdown正文（列表不带，详情才回填） */
  content?: string
  /** 状态：DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED（字典 chapter_status） */
  status?: string
  /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
  reviewStatus?: string
  publishTime?: string
  // ---- join article 带出的所属文章信息（非章节表字段，供前端展示+权限判定） ----
  articleTitle?: string
  /** 所属文章可见性（join article 带出；章节提交状态机判定要用） */
  articleVisibility?: string
  /** 所属文章等级（join article 带出；章节编辑权限判定要用） */
  articleLevel?: number
  // ---- 当前用户对该章节的权限态（详情接口回填，列表不回填） ----
  canEdit?: boolean
  canReview?: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 章节列表查询参数，与后端 ChapterQuarry + 分页参数对齐。
 * articleId 必传（章节按文章维度列表）；userViewLevel/userId/articleAuthorId 为后端回填字段，前端不传。
 */
export interface ChapterListQuery extends Partial<PageQueryParams> {
  articleId: number
  chapterName?: string
  status?: string
  reviewStatus?: string
  authorId?: number
  beginTime?: string
  endTime?: string
}

export type ChapterPageResult = NormalizedPageResult<ChapterRecord>

/**
 * 章节作者审核入参，与后端 ChapterReviewVo 对齐。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 * 仅 PENDING_AUTHOR_REVIEW 态可审；审核人必须是文章作者（或系统审权限代审）。
 */
export interface ChapterReviewPayload {
  chapterId: number
  pass: boolean
  advice?: string
}

/**
 * 章节作者审核流水记录，与后端 ChapterReviewLogVo 字段对齐。
 * action 仅 SUBMIT/APPROVE/REJECT 三值（章节审核无 REVOKE/PUBLISH）；
 * role 取 AUTHOR(章节提交者)/REVIEWER(文章作者审)，SYSTEM 不会出现在章节流水。
 */
export interface ChapterReviewLogRecord {
  reviewLogId?: number
  chapterId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/** 章节状态常量（与后端 ChapterStatus 枚举对齐），供前端按钮显隐逻辑判定 */
export const CHAPTER_STATUS = {
  DRAFT: 'DRAFT',
  PENDING_AUTHOR_REVIEW: 'PENDING_AUTHOR_REVIEW',
  PUBLISHED: 'PUBLISHED',
  REJECTED: 'REJECTED',
  REVOKED: 'REVOKED',
} as const
