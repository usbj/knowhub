import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { SysRoleListQuery, SysRolePageResult, SysRoleRecord } from '@/types/api/system/role'

export const getSysRolePageApi = (params: SysRoleListQuery) =>
  getPage<SysRoleRecord>('/sys/role/list', {
    params,
  }) as Promise<SysRolePageResult>

export const getSysRoleDetailApi = (roleId: number) => get<ApiResult<SysRoleRecord>>(`/sys/role/${roleId}`)

export const createSysRoleApi = (data: SysRoleRecord) =>
  post<ApiResult<boolean>, SysRoleRecord>('/sys/role', data)

export const updateSysRoleApi = (data: SysRoleRecord) =>
  put<ApiResult<boolean>, SysRoleRecord>('/sys/role', data)

export const deleteSysRolesApi = (roleIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/role/${roleIds.join(',')}`)

export const changeSysRoleStatusApi = (roleId: number, status: number) =>
  put<ApiResult<boolean>>('/sys/role/status', undefined, {
    params: {
      roleId,
      status,
    },
  })

export const setDefaultSysRoleApi = (roleId: number) =>
  put<ApiResult<boolean>>(`/sys/role/default/${roleId}`)
