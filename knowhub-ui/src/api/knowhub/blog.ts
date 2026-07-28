/**
 * 文件作用：
 * 前台博客门户接口（/portal/blog/* + /portal/tag/hot），全部公开免登录（后端 permitAll）。
 * 列表/搜索/推荐/相关/标签榜走 get + getPage；详情越级锁态由后端返回 locked 字段。
 * 作者创作（/authoring/blog/**）走 authenticated，需登录态，见 authoring.ts。
 */
import { get, getPage } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { NormalizedPageResult } from '@/types/api/common'
import type {
  BlogPortalRecord,
  BlogPortalDetailRecord,
  BlogPortalSearchQuery,
} from '@/types/api/knowhub/blog'
import type { HotTagRecord, TagRecord } from '@/types/api/knowhub/tag'

/**
 * 前台博客搜索（全文+标签/作者复合过滤+排序，分页）。
 * 后端 PageHelper 归一化，直接返回 NormalizedPageResult。
 */
export const searchBlogsApi = (query: BlogPortalSearchQuery) =>
  getPage<BlogPortalRecord>('/portal/blog/search', { params: query })

/** 前台个性化推荐 feed（登录用户按偏好 tag，未登录/无行为走全局热门兜底） */
export const recommendBlogsApi = (size = 10, excludeBlogId?: number) =>
  get<ApiResult<BlogPortalRecord[]>>('/portal/blog/recommend', {
    params: { size, excludeBlogId },
  })

/** 前台博客详情（越级锁态降级，locked=true 时 content 为 null） */
export const getBlogDetailApi = (blogId: number) =>
  get<ApiResult<BlogPortalDetailRecord>>(`/portal/blog/${blogId}`)

/** 详情页相关推荐 */
export const relatedBlogsApi = (blogId: number, size = 10) =>
  get<ApiResult<BlogPortalRecord[]>>(`/portal/blog/${blogId}/related`, {
    params: { size },
  })

/** 标签热度榜（统计 blog_tag + article_tag） */
export const hotTagsApi = (size = 20) =>
  get<ApiResult<HotTagRecord[]>>('/portal/tag/hot', { params: { size } })

/** 全部启用标签（创作页标签选择器数据源，仅 tagId+tagName，后端 TagOptionVo） */
export const listEnabledTagsApi = () =>
  get<ApiResult<TagRecord[]>>('/portal/tag/list')

/** 类型再导出，供页面直接用 */
export type { BlogPortalRecord, BlogPortalDetailRecord, BlogPortalSearchQuery }
export type { NormalizedPageResult }
