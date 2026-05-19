import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'
import type { SysMenuRecord } from '@/types/api/system/menu'

export interface SysRoleRecord {
  roleId?: number
  roleName: string
  roleLevel: number
  roleKey: string
  status: number
  isDefault: number
  createTime?: string
  rolePerm?: SysMenuRecord[]
  permId: number[]
}

export interface SysRoleListQuery extends Partial<PageQueryParams> {
  roleName?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

export type SysRolePageResult = NormalizedPageResult<SysRoleRecord>
