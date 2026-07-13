/**
 * 文件作用：
 * 集中管理系统登录相关的后端接口请求方法，
 * 当前承接 /login 的调用。
 */
import { post } from '@/utils/http'
import type { ApiResult, LoginRequestData, LoginResponseData } from '@/types/api/login'

/**
 * 调用后端登录接口。
 * 当前后端返回 Result<String>，其中 data 为登录 token。
 * 账号密码错误时后端返回 401（见 GlobalExceptionHandler 的认证异常映射），
 * 这里带 skipAuthRedirect=true 阻止 http 拦截器对 401 做"跳登录页"处理，
 * 否则用户就停在登录页还要被整页刷新；只弹后端返回的真实失败原因即可。
 */
export const loginApi = (data: LoginRequestData) =>
  post<ApiResult<LoginResponseData>, LoginRequestData>('/login', data, {
    skipAuthRedirect: true,
  })