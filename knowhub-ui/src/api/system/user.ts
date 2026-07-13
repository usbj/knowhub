/**
 * 文件作用：
 * 当前登录用户相关的后端接口，前台版只承接个人资料读取。
 * （后台还有更新个人资料 / 路由树 / 用户管理 CRUD，前台暂不需要，按需再补。）
 */
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysUserProfile } from '@/types/api/user'

/**
 * 方法效果：
 * 获取当前登录用户的个人资料（GET /person）。
 * 参数：无。
 * 返回值：后端 Result 包裹的个人资料对象。
 */
export const getPersonalProfileApi = () => get<ApiResult<SysUserProfile>>('/person')