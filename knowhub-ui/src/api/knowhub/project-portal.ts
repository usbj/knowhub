/**
 * 文件作用：
 * 前台项目门户接口（/portal/project/*，全部公开免登录，后端 permitAll）。
 * 列表/搜索/推荐/相关走 get + getPage；详情越级锁态由后端返回 locked 字段；
 * 文件树扁平 listFiles，前端按 parentId 内存组装树渲染；文件下载链接经项目级权限闸后下发。
 * 项目无标签体系，故无 /tag 相关接口（与博客门户差异点）。
 * 权限：未登录默认 L1（后端 resolveUserViewLevel Math.max(1, view) 兜底），L2/L3 永不下发前台。
 */
import type { AxiosRequestConfig } from 'axios'
import { get, getPage } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { NormalizedPageResult } from '@/types/api/common'
import type {
  ProjectPortalRecord,
  ProjectPortalDetailRecord,
  ProjectPortalSearchQuery,
  ProjectFileRecord,
  ProjectMemberRecord,
} from '@/types/api/knowhub/project-portal'

/**
 * 前台项目搜索（标题关键字 + type/level 复合过滤 + 排序，分页）。
 * 后端 PageHelper 归一化，直接返回 NormalizedPageResult。
 */
export const searchProjectsApi = (query: ProjectPortalSearchQuery) =>
  getPage<ProjectPortalRecord>('/portal/project/search', { params: query })

/**
 * 前台项目推荐 feed（全局热门兜底，登录用户排除已浏览项目；详情页相关推荐时排除当前 projectId）。
 * 打分公式后端 download*3 + like*2 + collect*1 + view*1 + 时间衰减。
 */
export const recommendProjectsApi = (size = 10, excludeProjectId?: number, config?: AxiosRequestConfig) =>
  get<ApiResult<ProjectPortalRecord[]>>('/portal/project/recommend', {
    params: { size, excludeProjectId },
    ...config,
  })

/** 前台项目详情（越级锁态降级，locked=true 时 description 为 null；登录态计浏览量） */
export const getProjectDetailApi = (projectId: number) =>
  get<ApiResult<ProjectPortalDetailRecord>>(`/portal/project/${projectId}`)

/** 详情页相关推荐（同 type 排除自身，按热度打分） */
export const relatedProjectsApi = (projectId: number, size = 10) =>
  get<ApiResult<ProjectPortalRecord[]>>(`/portal/project/${projectId}/related`, {
    params: { size },
  })

/**
 * 前台项目成员列表（公开详情页右栏展示用，仅 PUBLISHED 下发，裁剪内部权限态）。
 * service 层置 canView/canDownload/canEdit 为 null，前端仅需渲染 nickname/username/memberRole。
 */
export const getProjectMembersApi = (projectId: number) =>
  get<ApiResult<ProjectMemberRecord[]>>(`/portal/project/${projectId}/members`)

/**
 * 前台项目文件树（扁平带 parentId，前端按 parentId 内存组装树渲染）。
 * 无权看该项目的项目返回空列表（后端不暴露文件结构）。
 */
export const getProjectFileTreeApi = (projectId: number) =>
  get<ApiResult<ProjectFileRecord[]>>(`/portal/project/${projectId}/file-tree`)

/**
 * 前台文件下载链接下发（需项目级下载权限，未登录/无权抛 ServiceException）。
 * 后端按当前访问模式（DIRECT 直链 / TRANSFER 中转）分发 downloadUrl：
 *  - DIRECT：OSS 预签名绝对 URL（host=directBaseUrl，前端直连 OSS/nginx）
 *  - TRANSFER：`/file/proxy/{objectId}` 相对路径，前端同源命中后端中转字节流接口
 * 返回的下载链接形态不是固定的，前端只管拿 url 触发 window.open 跳转下载。
 */
export const downloadProjectFileApi = (fileId: number) =>
  get<ApiResult<string>>(`/portal/project/file/download/${fileId}`)

/** 类型再导出，供页面直接用 */
export type {
  ProjectPortalRecord,
  ProjectPortalDetailRecord,
  ProjectPortalSearchQuery,
  ProjectFileRecord,
  ProjectMemberRecord,
}
export type { NormalizedPageResult }