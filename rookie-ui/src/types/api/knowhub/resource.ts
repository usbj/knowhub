/**
 * 文件作用：
 * 定义资源管理模块对接后端所需的接口类型，
 * 与后端 ResourceVo / ResourceQuarry / ResourceReviewVo / ResourceReviewLogVo 对齐，
 * 供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 ResourceVo 的 String 形式（BeanUtil 复制 Date→String）。
 * - resourceType 取值见字典 resource_type（FILE/LINK）；status 见字典 resource_status；
 *   reviewStatus 见字典 review_status（复用，NONE/PENDING/APPROVED/REJECTED）；
 *   action 见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客+资源共用）。
 * - 互动计数（likeCount/collectCount/ratingAvg/ratingCount）由后端聚合事实表回填，不冗余主表。
 * - hasLiked/hasCollected/myScore 为详情接口回填的当前用户态，管理台列表一般不消费。
 * - resourceCategoryId 为 -1 表示"其他"（前端硬编码约定，不查分类表）。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 资源记录，与后端 ResourceVo 字段对齐。
 * FILE 类型：fileObjectId 关联 file_object，originalName/contentLength/contentType 由后端 join 带出。
 * LINK 类型：linkUrl 外部链接，linkIcon 图标（可空，前端可运行时拼 favicon 兜底）。
 */
export interface ResourceRecord {
  resourceId?: number
  authorId?: number
  resourceType: string
  resourceCategoryId?: number
  /** 分类名（后端 join 带出；-1=其他时为 null，前端硬编码展示"其他"） */
  categoryName?: string
  title: string
  summary?: string
  description?: string
  /** FILE 类型：关联 file_object.object_id */
  fileObjectId?: number
  /** FILE 类型：原始文件名（后端 join file_object 带出） */
  originalName?: string
  /** FILE 类型：文件大小字节（后端 join file_object 带出） */
  contentLength?: number
  /** FILE 类型：MIME 类型（后端 join file_object 带出） */
  contentType?: string
  /** LINK 类型：外部链接 URL */
  linkUrl?: string
  /** LINK 类型：图标 URL（可空） */
  linkIcon?: string
  status?: string
  reviewStatus?: string
  publishTime?: string
  /** 下载次数（仅 FILE 下载 +1，主表冗余） */
  downloadCount?: number
  /** 点赞数（事实表聚合回填） */
  likeCount?: number
  /** 收藏数（事实表聚合回填） */
  collectCount?: number
  /** 平均评分 0-5（事实表聚合回填） */
  ratingAvg?: number
  /** 评分数（事实表聚合回填） */
  ratingCount?: number
  hasLiked?: boolean
  hasCollected?: boolean
  /** 当前用户评分（详情接口回填，无则 0） */
  myScore?: number
  /** FILE 下载链接（详情接口按访问模式回填） */
  downloadUrl?: string
  createBy?: string
  /** 作者昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 资源列表查询参数，与后端 ResourceQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 */
export interface ResourceListQuery extends Partial<PageQueryParams> {
  title?: string
  resourceType?: string
  resourceCategoryId?: number
  status?: string
  reviewStatus?: string
  createBy?: string
  beginTime?: string
  endTime?: string
}

export type ResourcePageResult = NormalizedPageResult<ResourceRecord>

/**
 * 资源审核入参，与后端 ResourceReviewVo 对齐。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 */
export interface ResourceReviewPayload {
  resourceId: number
  pass: boolean
  advice?: string
}

/**
 * 资源审核流水记录，与后端 ResourceReviewLogVo 字段对齐。
 * action 取值见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客+资源共用）。
 * operator 为操作人用户名快照，operatorNickname 由后端 join sys_user 带出便于直接展示。
 * role 为审核业务身份（AUTHOR作者/REVIEWER审核员/SYSTEM系统直通），按动作类型定非系统角色。
 */
export interface ResourceReviewLogRecord {
  reviewLogId?: number
  resourceId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/**
 * 资源分类扁平记录，与后端 ResourceCategoryVo 字段对齐。
 * parent_id=0 表示顶级；status:0禁1启。
 */
export interface ResourceCategoryRecord {
  categoryId?: number
  parentId?: number
  categoryName: string
  sort?: number
  status?: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 资源分类树形记录，与后端 ResourceCategoryTreeVo 对齐，供前端 el-tree 渲染。
 * 前端约定 -1=其他（不在分类树内，由前端硬编码加一个"其他"虚拟节点）。
 */
export interface ResourceCategoryTreeNode {
  categoryId?: number
  parentId?: number
  categoryName: string
  sort?: number
  status?: number
  children?: ResourceCategoryTreeNode[]
}
