/**
 * 文件作用：
 * 定义文章管理模块对接后端所需的接口类型，
 * 与后端 ArticleVo / ArticleQuarry / ArticleReviewVo / ArticleReviewLogVo 对齐，
 * 供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 Date 经 jackson 序列化输出的 string 形式（契约不变），展示用 formatDateTime。
 * - level 见字典 article_level（1/2/3，对标权限等级 view/edit:lN）；
 *   visibility 见字典 article_visibility（PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开，决定章节提交审不审）；
 *   status 见字典 article_status（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED，无 ARCHIVED）；
 *   reviewStatus 见字典 review_status（复用，NONE/PENDING/APPROVED/REJECTED）；
 *   action 见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客/资源/项目/文章共用）。
 * - 权限态 canView/canEdit/isAuthor 由后端详情接口回填，供前端控制按钮显隐。
 * - 文章无成员表（轻量权限：系统级 + 作者归属），无 download；作者对自己的文章全权（isAuthor）。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 文章记录，与后端 ArticleVo 字段对齐。
 * 文章=章节集合，主表不存正文（正文在 chapter），不冗余审核快照（全在 article_review_log）。
 */
export interface ArticleRecord {
  articleId?: number
  title: string
  /** 前言/编者按（整书导言，mediumtext，列表可预览） */
  summary?: string
  /** 文章等级 1公开/2内部/3机密（字典 article_level，对标权限等级） */
  level: number
  /** 内部可见性 PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开（字典 article_visibility，决定章节提交审不审） */
  visibility: string
  authorId?: number
  /** 作者昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  /** 封面图 RustFS 对象key（对接 file_object business_type=ARTICLE_COVER） */
  coverObjectKey?: string
  /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED（字典 article_status） */
  status?: string
  /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
  reviewStatus?: string
  publishTime?: string
  // ---- 当前用户对该文章的权限态（详情接口回填，列表不回填） ----
  canView?: boolean
  canEdit?: boolean
  /** 当前用户是否为该文章作者（作者全权不看等级/不看 visibility） */
  isAuthor?: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 文章列表查询参数，与后端 ArticleQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 * userViewLevel / userId 为后端 service 层回填字段，前端不传。
 */
export interface ArticleListQuery extends Partial<PageQueryParams> {
  title?: string
  level?: number
  visibility?: string
  status?: string
  reviewStatus?: string
  createBy?: string
  authorId?: number
  beginTime?: string
  endTime?: string
}

export type ArticlePageResult = NormalizedPageResult<ArticleRecord>

/**
 * 文章审核入参，与后端 ArticleReviewVo 对齐。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 * 作者不能审核自己文章（回避）。
 */
export interface ArticleReviewPayload {
  articleId: number
  pass: boolean
  advice?: string
}

/**
 * 文章审核流水记录，与后端 ArticleReviewLogVo 字段对齐。
 * action 取值见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客/资源/项目/文章共用）。
 * operator 为操作人用户名快照，operatorNickname 由后端 join sys_user 带出便于直接展示。
 * role 为审核业务身份（AUTHOR作者/REVIEWER审核员/SYSTEM系统直通），按动作类型定非系统角色。
 */
export interface ArticleReviewLogRecord {
  reviewLogId?: number
  articleId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/** 文章可见性常量（与后端 ArticleVisibility 枚举对齐），供前端逻辑判定 */
export const ARTICLE_VISIBILITY = {
  PRIVATE: 'PRIVATE',
  SEMIPUBLIC: 'SEMIPUBLIC',
  PUBLIC: 'PUBLIC',
} as const

/** 文章状态常量（与后端 ArticleStatus 枚举对齐），供前端按钮显隐逻辑判定 */
export const ARTICLE_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  REVOKED: 'REVOKED',
  PENDING_REVIEW: 'PENDING_REVIEW',
  REJECTED: 'REJECTED',
} as const
