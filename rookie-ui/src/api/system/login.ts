/**
 * 文件作用：
 * 集中管理系统登录相关的后端接口请求方法，
 * 当前先承接 /login 的调用。
 */
import { post } from '@/utils/http'
import type { ApiResult, LoginRequestData, LoginResponseData } from '@/types/api/system/login'

/**
 * 调用后端登录接口。
 * 当前后端返回 Result<String>，其中 data 为登录 token。
 */
export const loginApi = (data: LoginRequestData) =>
  post<ApiResult<LoginResponseData>, LoginRequestData>('/login', data)
