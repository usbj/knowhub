/**
 * 文件作用：
 * 定义资源创作对接后端 /authoring/resource/** 的接口类型，与后端 ResourceVo 对齐。
 * 资源无标签体系、无 level 等级（与博客/文章创作差异点）：创作者只填类型 + 载体字段 + 分类 + 标题/摘要/正文。
 * - 创作提交载荷（ResourceAuthoringPayload）：新建\PUBLISHED 禁编辑（须先撤回）。
 * - 编辑回填（ResourceAuthoringDetail）：消费 getMyResourceForEditApi 返回的 ResourceVo 子集（含 status/reviewStatus 驱动按钮态）。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 */
import type { NormalizedPageResult } from '../common'

/**
 * 创作提交载荷（与后端 ResourceVo 创作相关字段对齐，只读字段不带）。
 * - resourceId：编辑时带（后端 editResourceInfo 校验归属+状态机），草稿新建不带。
 * - resourceType：FILE 文件 / LINK 链接（必填）。
 * - FILE 类型须带 fileObjectId（已上传文件 objectId，前端直传 presignedUploadFlow RESOURCE_FILE/PRIVATE 拿到）；
 *   已发布资源换源须先撤回，前端按 status 隐藏换文件入口。
 * - LINK 类型须带 linkUrl（必填），linkIcon 可空（前端可运行时拼 favicon 兜底）。
 * - resourceCategoryId：分类树叶子 id，不传后端缺省置 -1（其他）。
 * - description：资源正文（Markdown），编辑器走 presignedUploadFlow，businessType=BLOG_BODY（沿用博文章节配图口径）。
 */
export interface ResourceAuthoringPayload {
  resourceId?: number
  resourceType: 'FILE' | 'LINK'
  title: string
  summary?: string
  description?: string
  /** FILE 类型：关联 file_object.object_id（已上传 RESOURCE_FILE 文件 objectId） */
  fileObjectId?: number | null
  /** LINK 类型：外部链接 URL（必填） */
  linkUrl?: string | null
  /** LINK 类型：图标 URL（可空） */
  linkIcon?: string | null
  /** 分类树叶子 id，不传后端置 -1 */
  resourceCategoryId?: number
  /** 评论区开关 1开/0关，作者在上传页勾选 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，开启后新评论需作者同意后对他人展示 */
  commentCurated?: number
}

/**
 * 编辑回填数据（getResourceForAuthor 返回的 ResourceVo 子集，前端创作页只消费这些字段）。
 * - status：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED，驱动按钮态（PUBLISHED 须先撤回才可编辑/换源）。
 * - reviewStatus：NONE/PENDING/APPROVED/REJECTED。
 * 复用后台 ResourceVo（含全态），前台回填表单只用编辑相关字段，多余互动/计数字段忽略。
 */
export interface ResourceAuthoringDetail {
  resourceId: number
  authorId?: number
  authorNickname?: string
  resourceType: string
  resourceCategoryId?: number
  title: string
  summary?: string
  description?: string
  fileObjectId?: number | null
  originalName?: string | null
  contentLength?: number | null
  contentType?: string | null
  linkUrl?: string | null
  linkIcon?: string | null
  status?: string
  reviewStatus?: string
  publishTime?: string
  createTime?: string
  /** 评论区开关 1开/0关，编辑回填用 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，编辑回填用 */
  commentCurated?: number
}

/** 我的资源列表查询参数（前台 /authoring/resource/list） */
export interface MyResourceListQuery {
  /** 可选状态过滤（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED），留空取全部全态资源 */
  status?: string
  /** 标题关键字（后端 quarryResource 走 LIKE，与后台一致） */
  title?: string
  /** 资源类型过滤 FILE / LINK */
  resourceType?: string
  /** 分类过滤 */
  resourceCategoryId?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 我的资源列表项（后台 ResourceVo 子集，前台消费列表展示字段）。
 * service 内硬置 authorId=当前用户，只返回作者本人全态资源（含草稿/待审/驳回/已发布/撤回）。
 */
export interface MyResourceRecord {
  resourceId: number
  authorId?: number
  authorNickname?: string
  resourceType: string
  resourceCategoryId?: number
  categoryName?: string | null
  title: string
  summary?: string
  fileObjectId?: number | null
  originalName?: string | null
  contentLength?: number | null
  contentType?: string | null
  linkUrl?: string | null
  linkIcon?: string | null
  status?: string
  reviewStatus?: string
  publishTime?: string
  downloadCount?: number
  viewCount?: number
  likeCount?: number
  collectCount?: number
  ratingAvg?: number
  ratingCount?: number
  createTime?: string
  updateTime?: string
}

/**
 * 资源文件业务类型常量（与后端 FileBusinessType 枚举对齐，资源文件单一类型）。
 * 默认 access=PRIVATE，两访问模式（DIRECT 直链 / TRANSFER 中转）后端自适应分发。
 */
export const RESOURCE_BUSINESS_TYPE = {
  FILE: 'RESOURCE_FILE',
} as const

export type MyResourcePageResult = NormalizedPageResult<MyResourceRecord>