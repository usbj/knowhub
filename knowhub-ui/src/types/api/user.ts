/**
 * 文件作用：
 * 当前登录用户个人资料接口类型，与后端 GET /person 返回的 SysUserVo 对齐。
 * 前台仅消费展示名/账号/角色等基础信息，不必承袭后台管理用的增改表单结构。
 */
import type { ApiResult } from './common'

/**
 * 角色（前台展示名用）。
 * 复用后台 SysRoleRecord 的核心字段。
 */
export interface SysRoleRecord {
  roleId: number
  roleName: string
  roleLevel?: number
  roleKey?: string
  status?: number
  isDefault?: number
}

/**
 * 当前登录用户资料：与后端 /person 返回的 SysUserVo 重要字段对齐。
 * 只列前台实际展示用的字段，后端其他字段不在此声明也不影响反序列化。
 */
export interface SysUserProfile {
  userId: number
  username: string
  nickName: string
  phoneNumber: string
  sex: string
  status: number
  createTime?: string

  /** 头像 URL（/file/resolve/{objectId} 形态，无头像为 null/undefined，前端 <img> 直引失败回退首字） */
  avatar?: string

  /** 后端返回 userRole 列表，用于展示角色名标签。 */
  userRole?: SysRoleRecord[]
}

export type { ApiResult } from './common'