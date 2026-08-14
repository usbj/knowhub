/**
 * 文件作用：
 * 集中管理当前登录用户相关的后端接口，
 * 包括个人信息读取/修改、当前用户路由树，以及系统模块下的用户管理接口。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { SysMenuRecord } from '@/types/api/system/menu'
import type {
  ModifyPasswordRequestData,
  SysUserFormData,
  SysUserListQuery,
  SysUserPageResult,
  SysUserProfile,
  UpdatePersonalProfilePayload,
} from '@/types/api/system/user'

/**
 * 方法效果：
 * 获取当前登录用户的个人资料。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的个人资料对象。
 */
export const getPersonalProfileApi = () => get<ApiResult<SysUserProfile>>('/person')

/**
 * 方法效果：
 * 更新当前登录用户的个人资料。
 * 参数：
 * - `data`：个人资料更新请求体。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updatePersonalProfileApi = (data: UpdatePersonalProfilePayload) =>
  put<ApiResult<boolean>, UpdatePersonalProfilePayload>('/person', data)

/**
 * 方法效果：
 * 修改当前登录用户密码（独立接口，与资料编辑分离）。
 * 参数：
 * - `data`：修改密码请求体（原密码 + 新密码）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const modifyPersonalPasswordApi = (data: ModifyPasswordRequestData) =>
  put<ApiResult<boolean>, ModifyPasswordRequestData>('/person/password', data)

/**
 * 方法效果：
 * 获取当前登录用户拥有权限的菜单路由树。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的菜单树数组。
 */
export const getCurrentUserRoutesApi = () => get<ApiResult<SysMenuRecord[]>>('/person/routers')

/**
 * 方法效果：
 * 获取系统模块下的用户分页列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：列表查询条件与分页参数。
 * 返回值：
 * - 归一化后的用户分页结果。
 */
export const getSysUserPageApi = (params: SysUserListQuery) =>
  getPage<SysUserFormData>('/sys/user/list', {
    params,
  }) as Promise<SysUserPageResult>

/**
 * 方法效果：
 * 根据用户主键获取用户详情。
 * 参数：
 * - `userId`：用户主键。
 * 返回值：
 * - 后端 Result 包裹的用户详情对象。
 */
export const getSysUserDetailApi = (userId: number) => get<ApiResult<SysUserFormData>>(`/sys/user/${userId}`)

/**
 * 方法效果：
 * 新增系统用户。
 * 参数：
 * - `data`：用户新增表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createSysUserApi = (data: SysUserFormData) =>
  post<ApiResult<boolean>, SysUserFormData>('/sys/user', data)

/**
 * 方法效果：
 * 更新系统用户信息。
 * 参数：
 * - `data`：用户编辑表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateSysUserApi = (data: SysUserFormData) =>
  put<ApiResult<boolean>, SysUserFormData>('/sys/user', data)

/**
 * 方法效果：
 * 批量删除系统用户。
 * 参数：
 * - `userIds`：待删除的用户主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteSysUsersApi = (userIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/user/${userIds.join(',')}`)

/**
 * 方法效果：
 * 修改系统用户状态。
 * 参数：
 * - `userId`：用户主键。
 * - `status`：目标状态。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const changeSysUserStatusApi = (userId: number, status: number) =>
  put<ApiResult<boolean>>('/sys/user/status', undefined, {
    params: {
      userId,
      status,
    },
  })
