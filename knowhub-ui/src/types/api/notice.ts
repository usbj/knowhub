/**
 * 文件作用：
 * 当前登录用户可见的通知记录类型，与后端 SysNoticeVo 对齐。
 * 前台公告下拉与 /notices 列表消费，只列展示所需字段；
 * 后端其他字段（groupIds / publishScope / 审核元数据等）不声明也不影响反序列化。
 */

/**
 * 通知主体记录（前台消费子集）。
 * 与后台 rookie-ui 的 SysNoticeRecord 同源，省略前台用不到的管理字段。
 */
export interface SysNoticeRecord {
  noticeId?: number
  title: string
  content: string
  /** 通知类型字典值，如 sys_notice_type 的 code；前台展示用原始值或简单映射 */
  noticeType: string
  /** 通知级别字典值，如 sys_notice_level（普通/重要/紧急），后台展示用 */
  level: string
  status: string
  /** 是否置顶：后端 Integer，前台统一用 number（0/1） */
  isTop: number
  /** 是否需确认：后台用它驱动确认弹窗，前台暂不强交互，仅展示 */
  needConfirm: number
  publishTime?: string
  /** 发布者（后端 create_by） */
  createBy?: string
  createTime?: string
  /** 当前用户是否已读，后端 /sys/notice/my 返回时填充 */
  hasRead?: boolean
  /** 当前用户是否已确认（前台暂不强交互） */
  hasConfirmed?: boolean
}