/**
 * 文件作用：
 * 前台选人接口（/authoring/user/search，走 /authoring/** authenticated 兜底，权限键 knowhub:authoring:user-search）。
 * 供前台项目/资源成员添加子弹窗选人用。后端独立权限键（默认分配给"实验室成员"角色，admin 自带），
 * 不依赖后台 knowhub:system:user:quarry 系统 admin 接口（含 role/email 敏感字段且按钮权限普通创作者未必有）。
 * 输出最小渲染集 userId/nickName/username/avatar/phoneNumber，与后端 AuthoringUserVo 对齐。
 * 字段搜索字段对应后台口径：nickName(昵称) / username(用户名) / phoneNumber(手机号) 任一非空按该字段 LIKE，皆空走最近注册兜底分页。
 */
import { get, getPage } from '@/utils/http'
import type { ApiResult, PageQueryParams } from '@/types/api/common'

/** 前台选人查询参数（与后端 AuthoringUserSearchQuarry + 分页参数对齐） */
export interface AuthoringUserSearchQuery extends Partial<PageQueryParams> {
  nickName?: string
  username?: string
  phoneNumber?: string
}

/** 前台选人记录（与后端 AuthoringUserVo 对齐，仅选人所需最小字段集） */
export interface AuthoringUserRecord {
  userId: number
  nickName: string
  username: string
  avatar?: string
  phoneNumber?: string
  createTime?: string
}

/**
 * 前台模糊查询活动用户（昵称/用户名/手机号 LIKE，分页）。
 * 后端强制 delete=0 AND status=1 过滤，按 create_time desc 兜底排序。
 */
export const searchAuthoringUsersApi = (query: AuthoringUserSearchQuery) =>
  getPage<AuthoringUserRecord>('/authoring/user/search', { params: query })