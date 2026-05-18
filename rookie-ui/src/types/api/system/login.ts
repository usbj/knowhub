import type { ApiResult } from '@/types/api/system/common'
export type { ApiResult } from '@/types/api/system/common'

/**
 * 登录表单提交参数。
 * 与后端 LoginBody 中的 username、password 字段保持一致。
 */
export interface LoginRequestData {
  username: string
  password: string
}

/**
 * 当前后端登录接口返回的 data 实际上只有 token 字符串。
 */
export type LoginResponseData = string
