/**
 * 文件作用：
 * 资源创作与互动接口（/authoring/resource/**，走 /authoring/** authenticated 兜底，需登录态）。
 * 复用后台 ResourceService（addResourceInfo/editResourceInfo/publishResource/revokeResource/
 * toggleLike/toggleCollect/rateResource/downloadResource），后端含状态机+归属校验，前端只做薄封装。
 * - draft/edit：ResourceVo 体提交（编辑带 resourceId，新建不带）。PUBLISHED 禁编辑须先撤回。
 * - publish/revoke：仅路径变量，后端状态机校验。
 * - getForEdit：编辑回填，复用后台 getResourceInfo（含归属校验拒非作者 + 全态 + 互动回填）。
 * - download：FILE + PUBLISHED 下载链接下发，下载量 +1（LINK 类型前端直接用 linkUrl）。
 * - like/collect/rating：登录即可对任意已发布资源互动。
 * - myCollected：我的收藏列表（前台可见口径）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type {
  ResourceAuthoringPayload,
  ResourceAuthoringDetail,
  MyResourceRecord,
  MyResourceListQuery,
  MyResourcePageResult,
} from '@/types/api/knowhub/resource-authoring'
import type { ResourcePortalRecord } from '@/types/api/knowhub/resource'

/**
 * 前台我的资源列表（薄封装 listMyResources，service 内硬置 authorId=当前用户）。
 * 复用后台 ResourceVo（含 status/reviewStatus 草稿全态）。
 */
export const getMyResourcesApi = (query: MyResourceListQuery) =>
  getPage<MyResourceRecord>('/authoring/resource/list', { params: query }) as Promise<MyResourcePageResult>

/**
 * 编辑回填：复用后台 getResourceInfo（getResourceForAuthor 内归属校验拒非作者，
 * 返回含全态+互动回填的 ResourceVo）。
 */
export const getMyResourceForEditApi = (resourceId: number) =>
  get<ApiResult<ResourceAuthoringDetail>>(`/authoring/resource/${resourceId}`)

/** 前台新建资源草稿（复用 addResourceInfo，作者=current user，DRAFT）。返回后端 boolean。 */
export const addMyResourceApi = (data: ResourceAuthoringPayload) =>
  post<ApiResult<boolean>, ResourceAuthoringPayload>('/authoring/resource', data)

/** 前台编辑资源（复用 editResourceInfo，校验归属+状态机；PUBLISHED 须先撤回才可编辑/换源）。 */
export const editMyResourceApi = (data: ResourceAuthoringPayload) =>
  put<ApiResult<boolean>, ResourceAuthoringPayload>('/authoring/resource', data)

/** 前台发布资源（复用 publishResource，按审核开关 knowhub.resource.review_enabled 决定 PUBLISHED 或 PENDING_REVIEW）。 */
export const publishMyResourceApi = (resourceId: number) =>
  put<ApiResult<boolean>>(`/authoring/resource/${resourceId}/publish`)

/** 前台撤回资源（复用 revokeResource → REVOKED，撤回后可再编辑/换源/再发布）。仅 PUBLISHED 可撤回。 */
export const revokeMyResourceApi = (resourceId: number) =>
  put<ApiResult<boolean>>(`/authoring/resource/${resourceId}/revoke`)

/** 前台删除资源（软删 resource.deleted=1，复用 deleteResourceInfo，仅作者或 admin；普通用户仅删自己上传的）。 */
export const deleteMyResourceApi = (resourceId: number) =>
  del<ApiResult<boolean>>(`/authoring/resource/${resourceId}`)

/**
 * FILE 资源下载链接下发（复用 downloadResource，校验 PUBLISHED+FILE，下载量 +1）。
 * 返回的下载链接形态不是固定的（中转 /file/proxy/{objectId} 或 OSS 预签名），前端 window.open 即可。
 */
export const downloadResourceApi = (resourceId: number) =>
  get<ApiResult<string>>(`/authoring/resource/${resourceId}/download`)

/** 点赞/取消点赞资源（liked=true 点赞/false 取消，事实表 upsert，计数读时聚合）。 */
export const toggleResourceLikeApi = (resourceId: number, liked: boolean) =>
  put<ApiResult<boolean>>(`/authoring/resource/${resourceId}/like`, undefined, { params: { liked } })

/** 收藏/取消收藏资源（collected=true 收藏/false 取消，事实表 upsert）。 */
export const toggleResourceCollectApi = (resourceId: number, collected: boolean) =>
  put<ApiResult<boolean>>(`/authoring/resource/${resourceId}/collect`, undefined, { params: { collected } })

/** 评分(1-5)（一人一资源一条，upsert 事实表，均值读时聚合）。 */
export const rateResourceApi = (resourceId: number, score: number) =>
  put<ApiResult<boolean>>(`/authoring/resource/${resourceId}/rating`, undefined, { params: { score } })

/**
 * 我的资源收藏列表（按收藏时间倒序，仅返回前台可见口径的已发布资源）。
 * 返回前台资源门户 VO（ResourcePortalRecord 口径）。
 */
export const listMyCollectedResourcesApi = (pageNum = 1, pageSize = 20) =>
  getPage<ResourcePortalRecord>('/authoring/resource/collect/list', { params: { pageNum, pageSize } })

/** 类型再导出，供页面直接用 */
export type {
  ResourceAuthoringPayload,
  ResourceAuthoringDetail,
  MyResourceRecord,
  MyResourceListQuery,
  MyResourcePageResult,
}
export type { NormalizedPageResult }