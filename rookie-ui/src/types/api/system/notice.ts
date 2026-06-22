/**
 * 文件作用：
 * 定义通知管理与通知分组管理对接后端所需的接口类型，
 * 与后端 SysNoticeVo / SysNoticeGroupVo / SysNoticeGroupMember / NoticeQuarry / NoticeGroupQuarry 对齐，
 * 供 api 层和页面层统一消费。
 */
import type { NormalizedPageResult, PageQueryParams } from './common'

/**
 * 通知主体记录，与后端 SysNoticeVo 字段对齐。
 * isTop / needConfirm 后端为 Integer，前端统一用 number（0/1）。
 * groupIds 用于发布范围为分组时携带关联分组主键集合。
 */
export interface SysNoticeRecord {
  noticeId?: number
  title: string
  content: string
  noticeType: string
  level: string
  publishScope: string
  status: string
  isTop: number
  needConfirm: number
  publishTime?: string
  /** 发布者（后端 create_by），详情弹窗展示用 */
  createBy?: string
  createTime?: string
  remark?: string
  groupIds?: number[]
  noticeGroups?: Array<{ groupId: number; groupName: string; groupCode: string }>
  hasRead?: boolean
  hasConfirmed?: boolean
}

/**
 * 通知列表查询参数，与后端 NoticeQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 */
export interface SysNoticeListQuery extends Partial<PageQueryParams> {
  title?: string
  noticeType?: string
  level?: string
  publishScope?: string
  status?: string
  beginTime?: string
  endTime?: string
}

export type SysNoticePageResult = NormalizedPageResult<SysNoticeRecord>

/**
 * 通知分组成员记录，与后端 SysNoticeGroupMember 对齐。
 * nickName / username 不来自后端该对象，由页面拉取用户列表后按 userId 关联补全用于展示。
 */
export interface SysNoticeGroupMemberRecord {
  id: number
  groupId: number
  userId: number
  nickName?: string
  username?: string
}

/**
 * 通知分组记录，与后端 SysNoticeGroupVo 字段对齐。
 * members 为分组当前成员列表，详情接口返回。
 */
export interface SysNoticeGroupRecord {
  groupId?: number
  groupName: string
  groupCode: string
  groupDesc?: string
  status: number
  createTime?: string
  members?: SysNoticeGroupMemberRecord[]
}

/**
 * 通知分组列表查询参数，与后端 NoticeGroupQuarry + 分页参数对齐。
 */
export interface SysNoticeGroupListQuery extends Partial<PageQueryParams> {
  groupName?: string
  groupCode?: string
  status?: number
}

export type SysNoticeGroupPageResult = NormalizedPageResult<SysNoticeGroupRecord>
