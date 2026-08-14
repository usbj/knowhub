/**
 * 文件作用：
 * 集中管理系统登录与注册相关的后端接口请求方法，
 * 当前先承接 /login 与 /register 的调用。
 */
import { post } from '@/utils/http'
import type {
  ApiResult,
  LoginRequestData,
  LoginResponseData,
  RegisterRequestData,
} from '@/types/api/system/login'

/**
 * 调用后端登录接口。
 * 当前后端返回 Result<String>，其中 data 为登录 token。
 */
export const loginApi = (data: LoginRequestData) =>
  post<ApiResult<LoginResponseData>, LoginRequestData>('/login', data)

/**
 * 调用后端注册接口。
 * 注册开关由后端系统设置 sys.user.registerEnabled 控制，关闭时后端直接拒绝。
 * 当前后端返回 Result<Boolean>，data 为是否注册成功。
 */
export const registerApi = (data: RegisterRequestData) =>
  post<ApiResult<boolean>, RegisterRequestData>('/register', data)
