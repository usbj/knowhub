/**
 * 文件作用：
 * 前台资源门户接口（/portal/resource/*，全部公开免登录，后端 permitAll）。
 * 资源无标签体系、无 level 等级（与博客门户差异点）：SQL 铁律 status=PUBLISHED AND deleted=0，
 * 非发布资源前台根本不下发（详情查不到返 404 语义）。推荐 feed 退化为全局热门兜底（无用户偏好源）。
 * 侧栏"热门下载榜""最近上传榜"直接复用 search 接口带 sort=HOT/LATEST + pageSize，不单独建榜接口。
 */
import type { AxiosRequestConfig } from 'axios'
import { get, getPage } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { NormalizedPageResult } from '@/types/api/common'
import type {
  ResourcePortalRecord,
  ResourcePortalDetailRecord,
  ResourcePortalSearchQuery,
  ResourceCategoryTreeNode,
} from '@/types/api/knowhub/resource'

/**
 * 前台资源搜索（全文关键字 + 类型/分类复合过滤 + 排序，分页）。
 * 后端 PageHelper 归一化，直接返回 NormalizedPageResult。
 *
 * 数组入参（resourceCategoryIds 多选）转逗号分隔串：Spring MVC 顺序绑定 + 内置 String→List<Long>
 * 转换器把 "1,2,3" 切分单元素转 Long 成 List<Long>；默认 axios 数组序列化成 `xx[]=1&xx[]=2`，
 * Spring POJO 字段匹配 `xx` 不识别末尾 `[]` 后缀，会拿到空列表，故显式 paramsSerializer 改写。
 * 单值入参（resourceType/sort/keyword 等）原样透传。
 */
const portalSearchParamsSerializer = (params: Record<string, unknown>): string => {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    if (Array.isArray(v)) {
      if (v.length === 0) continue
      sp.append(k, v.join(','))
    } else {
      sp.append(k, String(v))
    }
  }
  return sp.toString()
}

export const searchResourcesApi = (query: ResourcePortalSearchQuery, config?: AxiosRequestConfig) =>
  getPage<ResourcePortalRecord>('/portal/resource/search', { params: query, paramsSerializer: portalSearchParamsSerializer, ...config })

/**
 * 前台资源推荐 feed（全局热门兜底，资源无用户偏好源；详情页相关推荐时排除当前 resourceId）。
 */
export const recommendResourcesApi = (size = 10, excludeResourceId?: number, config?: AxiosRequestConfig) =>
  get<ApiResult<ResourcePortalRecord[]>>('/portal/resource/recommend', {
    params: { size, excludeResourceId },
    ...config,
  })

/**
 * 前台资源详情（登录态回填互动态 hasLiked/hasCollected/myScore + FILE 下载链接 + 登录态计浏览量）。
 * 非 PUBLISHED 或不存在后端返业务码 404。
 */
export const getResourceDetailApi = (resourceId: number) =>
  get<ApiResult<ResourcePortalDetailRecord>>(`/portal/resource/${resourceId}`)

/** 详情页相关推荐（同 resource_category_id 排除自身，按热度排） */
export const relatedResourcesApi = (resourceId: number, size = 10) =>
  get<ApiResult<ResourcePortalRecord[]>>(`/portal/resource/${resourceId}/related`, {
    params: { size },
  })

/**
 * 资源分类树（前台列表分类筛选 + 上传表单分类选择数据源）。
 * 前端硬编码加一个"其他"虚拟节点（categoryId=-1），不在此树内。
 */
export const getResourceCategoryTreeApi = () =>
  get<ApiResult<ResourceCategoryTreeNode[]>>('/portal/resource/category/tree')

/** 类型再导出，供页面直接用 */
export type {
  ResourcePortalRecord,
  ResourcePortalDetailRecord,
  ResourcePortalSearchQuery,
  ResourceCategoryTreeNode,
}
export type { NormalizedPageResult }