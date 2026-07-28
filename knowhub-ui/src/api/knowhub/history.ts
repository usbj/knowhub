/**
 * 文件作用：
 * 当前用户浏览历史接口（/history/*，走 authenticated 兜底，需登录）。
 * 列表分页 + 删单条 + 清空。
 */
import { del, getPage } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { ViewHistoryRecord } from '@/types/api/knowhub/history'

/** 当前用户浏览历史分页（bizType 可选过滤） */
export const listHistoryApi = (bizType?: string) =>
  getPage<ViewHistoryRecord>('/history/list', { params: { bizType } })

/** 删单条浏览历史（校验 user_id 归属） */
export const deleteHistoryApi = (viewId: number) =>
  del<ApiResult<boolean>>(`/history/${viewId}`)

/** 清空当前用户全部浏览历史 */
export const clearHistoryApi = () => del<ApiResult<boolean>>('/history/clear')
