/**
 * 文件作用：
 * 前台公开公告门户接口（/portal/notice/*，全部公开免登录，后端 permitAll）。
 * 与登录态通知接口（api/system/notice.ts 的 /sys/notice/my）分离：
 *   - 公开公告：未登录访客与登录用户都可读群发公告，无读写态；
 *   - 我的公告：登录用户铃铛下拉，含私发/分组/已读态。
 */
import type { AxiosRequestConfig } from 'axios'
import { get, getPage, post } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type {
  NoticePortalRecord,
  NoticePortalListQuery,
  NoticePortalPageResult,
} from '@/types/api/notice-portal'

/**
 * 公开公告分页列表。
 * - pageNum/pageSize/noticeType 透传，pageSize 不传时后端 PageUtil 默认 10。
 */
export const getPublicNoticesApi = (query: NoticePortalListQuery = {}, config?: AxiosRequestConfig) =>
  getPage<NoticePortalRecord>('/portal/notice/list', { params: query, ...config })

/**
 * 公开公告详情。非群发公告或不存在后端返业务码 404。
 */
export const getPublicNoticeDetailApi = (noticeId: number) =>
  get<ApiResult<NoticePortalRecord>>(`/portal/notice/${noticeId}`)

/**
 * 确认公告（登录用户，needConfirm=1 的群发已发布公告）。
 * 后端校验登录态 + 公告需确认，upsert sys_notice_read 后回填最新 hasConfirmed=true 的 VO。
 * 未登录访问后端抛 ServiceException（业务码 401），http 拦截器统一弹错。
 */
export const confirmNoticeApi = (noticeId: number) =>
  post<ApiResult<NoticePortalRecord>>(`/portal/notice/confirm/${noticeId}`)