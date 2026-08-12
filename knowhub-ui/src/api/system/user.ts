/**
 * 文件作用：
 * 当前登录用户相关的后端接口，前台承接个人资料读取 + 编辑更新。
 * （后台还有路由树 / 用户管理 CRUD，前台暂不需要，按需再补。）
 */
import { get, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysUserProfile } from '@/types/api/user'

/**
 * 方法效果：
 * 获取当前登录用户的个人资料（GET /person）。
 * 参数：无。
 * 返回值：后端 Result 包裹的个人资料对象。
 */
export const getPersonalProfileApi = () => get<ApiResult<SysUserProfile>>('/person')

/**
 * 方法效果：
 * 更新当前登录用户的个人资料（PUT /person，rookie SysLoginController 经 editUserInfo 持久化）。
 * 后端 editUserInfo SQL 只 set nickName/phoneNumber/sex（username/status 不暴露、password/avatar 不发也不动），
 * controller 强制 setUserId(currentUser) 不可改他人；故前端只发这三项。
 * 返回值：后端 Result<boolean>。
 */
export const updatePersonalProfileApi = (data: { nickName: string; phoneNumber: string; sex: string }) =>
  put<ApiResult<boolean>, { nickName: string; phoneNumber: string; sex: string }>('/person', data)