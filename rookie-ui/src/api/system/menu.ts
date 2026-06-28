import { del, get, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { SysMenuListQuery, SysMenuRecord } from '@/types/api/system/menu'

export const getSysMenuListApi = (params?: SysMenuListQuery) =>
  get<ApiResult<SysMenuRecord[]>>('/sys/menu/list', {
    params,
  })

export const getSysMenuDetailApi = (menuId: number) => get<ApiResult<SysMenuRecord>>(`/sys/menu/${menuId}`)

export const createSysMenuApi = (data: SysMenuRecord) =>
  post<ApiResult<boolean>, SysMenuRecord>('/sys/menu', data)

export const updateSysMenuApi = (data: SysMenuRecord) =>
  put<ApiResult<boolean>, SysMenuRecord>('/sys/menu', data)

export const deleteSysMenusApi = (menuIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/menu/${menuIds.join(',')}`)

export const changeSysMenuStatusApi = (menuId: number, status: number) =>
  put<ApiResult<boolean>>('/sys/menu/status', undefined, {
    params: {
      menuId,
      status,
    },
  })
