/**
 * 文件作用：
 * 当前登录用户相关的后端接口，前台承接个人资料读取 + 编辑更新 + 头像上传 + 密码重置。
 * （后台还有路由树 / 用户管理 CRUD，前台暂不需要，按需再补。）
 */
import { get, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysUserProfile } from '@/types/api/user'

/**
 * 方法效果：
 * 获取当前登录用户的个人资料（GET /person）。
 * 参数：无。
 * 返回值：后端 Result 包裹的个人资料对象（含 avatar 字段，/file/resolve/{id} 形态）。
 */
export const getPersonalProfileApi = () => get<ApiResult<SysUserProfile>>('/person')

/**
 * 方法效果：
 * 更新当前登录用户的个人资料（PUT /person，rookie SysLoginController 经 editUserInfo 持久化）。
 * 后端 editUserInfo SQL 动态更新 username/nickName/phoneNumber/sex/avatar（avatar!=null 才更新），
 * controller 强制 setUserId(currentUser) 不可改他人；password/status 不在 SQL 白名单故发也不动。
 * avatar 字段在头像上传成功后单独随 PUT 提交持久化（/file/resolve/{id} 稳定引用）。
 * 返回值：后端 Result<boolean>。
 */
export const updatePersonalProfileApi = (data: {
  nickName?: string
  phoneNumber?: string
  sex?: string
  avatar?: string
}) => put<ApiResult<boolean>, { nickName?: string; phoneNumber?: string; sex?: string; avatar?: string }>('/person', data)

/**
 * 方法效果：
 * 修改当前登录用户密码（PUT /person/password，独立接口，与资料编辑分离）。
 * 后端校验原密码（BCrypt matches）后复用 resetSysUserPassword 加密落库；新密码 6-20 位。
 * 参数：
 * - `data`：原密码 + 新密码。
 * 返回值：后端 Result<boolean>。原密码不匹配或新密码长度不合法时后端抛业务错误。
 */
export const modifyPersonalPasswordApi = (data: { oldPassword: string; newPassword: string }) =>
  put<ApiResult<boolean>, { oldPassword: string; newPassword: string }>('/person/password', data)