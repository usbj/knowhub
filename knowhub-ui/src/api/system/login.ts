/**
 * 文件作用：
 * 集中管理系统登录与注册相关的后端接口请求方法，
 * 承接 /portal/login（前台门户登录，不带后台访问权限闸）、/register（注册）、/register/enabled（注册开关查询）。
 *
 * 前后台登录分离：knowhub-ui 前台登录调 /portal/login（rookie SysLoginController.portalLogin），
 * 不经 system:access 闸——前台注册的 visitor 默认角色不绑该权限码仍可登录前台；
 * 后台 rookie-ui 登录调 /login（loginVerification，带 system:access 闸，无权账号被拒）。
 * 后台访问控制由后台接口 @PreAuthorize 兜底，前台登录只需拿到 token。
 */
import { get, post } from '@/utils/http'
import type { ApiResult, LoginRequestData, LoginResponseData, RegisterRequestData } from '@/types/api/login'

/**
 * 调用后端登录接口。
 * 当前后端返回 Result<String>，其中 data 为登录 token。
 * 账号密码错误时后端返回 401（见 GlobalExceptionHandler 的认证异常映射），
 * 这里带 skipAuthRedirect=true 阻止 http 拦截器对 401 做"跳登录页"处理，
 * 否则用户就停在登录页还要被整页刷新；只弹后端返回的真实失败原因即可。
 */
export const loginApi = (data: LoginRequestData) =>
  post<ApiResult<LoginResponseData>, LoginRequestData>('/portal/login', data, {
    skipAuthRedirect: true,
  })

/**
 * 调用后端注册接口。
 * 注册开关由后端系统设置 sys.user.registerEnabled 控制，关闭时后端直接拒绝（防绕过前端入口）。
 * 后端返回 Result<Boolean>，data 为是否注册成功；注册不自动登录，成功后前端引导跳登录页。
 * 带注册请求体 RegisterRequestData（username/password 必填，nickName/phoneNumber/sex 可选，不含 email）。
 */
export const registerApi = (data: RegisterRequestData) =>
  post<ApiResult<boolean>, RegisterRequestData>('/register', data)

/**
 * 查询注册开关（公开接口，无需登录）。
 * 后端读系统设置 sys.user.registerEnabled（BOOLEAN，默认 false）返回；
 * 前端注册页据此显隐「暂未开放」提示 + 禁用提交按钮，不直接读系统设置接口。
 */
export const getRegisterEnabledApi = () => get<ApiResult<boolean>>('/register/enabled')