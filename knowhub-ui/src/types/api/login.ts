/**
 * 文件作用：
 * 登录相关接口类型，与后端 LoginBody 对齐。
 */
import type { ApiResult } from './common'

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

/**
 * 注册表单提交参数，与后端 RegisterBody 字段对齐。
 * - username/password 必填；nickName/phoneNumber/sex 可选（后端缺省：nickName 取 username、sex 取 '0'）。
 * - **不含 email**：后端 RegisterBody 与 sys_user 表均无 email 列，email 仅在前端收集作找回密码提示用途，
 *   传后端也会被忽略；故前端不传，避免误导。
 * - username 对齐后端口径：≤12 位字母数字下划线；password 6-20；phoneNumber 可选但需 ^1\d{10}$。
 */
export interface RegisterRequestData {
  username: string
  password: string
  nickName?: string
  phoneNumber?: string
  sex?: string
}

export type { ApiResult } from './common'