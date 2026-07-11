/**
 * 文件作用：
 * 定义博客文章管理对接后端所需的接口类型，
 * 与后端 BlogVo / BlogQuarry / ReviewVo 对齐，供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 BlogVo 的 String 形式（BeanUtil 复制 Date→String）。
 * - tagIds 为作者选用的受控标签 id 列表；tagNames 由后端关联表回填用于展示。
 * - hasLiked / hasCollected 为详情接口回填的当前用户态，管理台列表一般不消费。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 博客文章记录，与后端 BlogVo 字段对齐。
 * status 取值见字典 blog_status；reviewStatus 见字典 review_status；level 见字典 blog_level。
 * authorId 为作者用户ID(userId)，与 createBy(username) 互补，前台展示昵称走 join sys_user。
 * canView 为详情接口回填的当前用户查看权限态（系统 view 等级够 OR 作者）；
 * canEdit 为详情接口回填的编辑权限态（作者本人 OR 超级管理员，编辑不分等级）；
 * isAuthor 为当前用户是否该博客作者。管理台列表一般不消费这三个字段，供详情按钮显隐。
 */
export interface BlogRecord {
  blogId?: number
  authorId?: number
  /** 博客等级 1公开/2内部/3机密（字典 blog_level，对标系统 view:lN 查看权限等级；编辑不分等级） */
  level?: number
  title: string
  content: string
  summary?: string
  coverUrl?: string
  status?: string
  publishTime?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  reviewStatus?: string
  reviewer?: string
  reviewTime?: string
  reviewAdvice?: string
  tagIds?: number[]
  tagNames?: string[]
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  hasLiked?: boolean
  hasCollected?: boolean
  // ---- 当前用户对该博客的权限态（详情接口回填，列表不回填） ----
  canView?: boolean
  canEdit?: boolean
  isAuthor?: boolean
}

/**
 * 博客列表查询参数，与后端 BlogQuarry + 分页参数对齐。
 * tagIds 为标签 id 列表，后端要求同时命中全部所选标签；
 * level 为按等级筛选（1/2/3）；
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 * userViewLevel/userId 为后端回填字段，前端不传。
 */
export interface BlogListQuery extends Partial<PageQueryParams> {
  title?: string
  keyword?: string
  level?: number
  tagIds?: number[]
  status?: string
  reviewStatus?: string
  createBy?: string
  beginTime?: string
  endTime?: string
}

export type BlogPageResult = NormalizedPageResult<BlogRecord>

/**
 * 博客审核入参，与后端 ReviewVo 对齐。
 * pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 */
export interface ReviewPayload {
  blogId: number
  pass: boolean
  advice?: string
}

/**
 * 博客审核流水记录，与后端 ReviewLogVo 字段对齐。
 * action 取值见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客+资源共用）。
 * operator 为操作人用户名快照，operatorNickname 由后端 join sys_user 带出便于直接展示。
 * role 为审核业务身份（AUTHOR作者/REVIEWER审核员/SYSTEM系统直通），按动作类型定非系统角色。
 */
export interface ReviewLogRecord {
  reviewLogId?: number
  blogId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/**
 * 封面上传申请入参，与后端 UploadApplyVo 对齐。
 * 博客封面固定 businessType=BLOG_COVER、access=PUBLIC，bizRefId 上传时业务行可能未建可空。
 */
export interface BlogCoverUploadApply {
  businessType: string
  contentType: string
  size: number
  originalName?: string
  access?: string
  bizRefId?: number
}
