/**
 * 文件作用：
 * 当前登录用户侧的通知接口，前台版只接"我的通知"拉取 + 标记已读。
 * （后台另有通知主体/分组/发布撤回/确认等管理接口，前台暂不需要，按需再补。）
 */
import { get, post } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysNoticeRecord } from '@/types/api/notice'

/**
 * 获取当前登录用户可见的通知列表（含已读/已确认状态与完整正文）。
 * 后端从 SecurityContextHolder 取当前用户，无需传参。
 * 仅登录用户可访问；响应头由 http 工具自动注入 Token。
 */
export const getMyNoticesApi = () => get<ApiResult<SysNoticeRecord[]>>('/sys/notice/my')

/**
 * 标记指定通知为当前用户已读。
 * - `noticeId`：通知主键。
 */
export const markAsReadApi = (noticeId: number) =>
  post<ApiResult<boolean>>(`/sys/notice/read/${noticeId}`)