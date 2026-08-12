/**
 * 文件作用：
 * 前台公开公告类型，与后端 com.knowhub.pojo.common.vo.NoticePortalVo 对齐。
 * 独立于 SysNoticeRecord（后者含 hasRead/targetUsers，登录态铃铛用）；公开公告对未登录访客开放，
 * 无读写态、无私发目标成员字段。noticeType/level 留后端字典 code，前端用内联映射翻。
 */

import type { NormalizedPageResult } from '@/types/api/common'

/** 前台公开公告记录（/portal/notice/* 出参子集） */
export interface NoticePortalRecord {
  noticeId?: number
  title: string
  content: string
  /** 通知类型字典 code（sys_notice_type：NOTICE/NOTIFY/REMIND），前端内联映射翻中文 */
  noticeType: string
  /** 通知级别字典 code（sys_notice_level：普通/重要/紧急），展示用 */
  level?: string
  /** 是否置顶：后端 Integer(0/1) */
  isTop?: number
  /** 是否需要确认（后端 need_confirm，0/1）：1 时登录用户露"确认"按钮，未登录访客只读 */
  needConfirm?: number
  /** 当前登录用户是否已确认（sys_notice_read.confirm_status=1）；未登录恒 false */
  hasConfirmed?: boolean
  /** 发布时间，后端 Date 经 jackson 输出 yyyy-MM-dd HH:mm:ss 字符串 */
  publishTime?: string
  /** 失效时间，可为 null */
  expireTime?: string
  /** 发布者（后端 create_by） */
  createBy?: string
  createTime?: string
}

/** /portal/notice/list 入参 */
export interface NoticePortalListQuery {
  pageNum?: number
  pageSize?: number
  /** 按通知类型筛选，空表示全部 */
  noticeType?: string
}

export type NoticePortalPageResult = NormalizedPageResult<NoticePortalRecord>