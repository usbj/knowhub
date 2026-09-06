/**
 * 文件作用：
 * 集中管理博客受控标签管理对接后端的接口方法，
 * 包括标签 CRUD，供标签管理页统一调用。
 * 路由前缀 /tag（knowhub 命名空间，不套 /sys）。
 * 关键约定：标签列表不分页，后端返回 List<TagVo>，前端用 ApiResult<TagRecord[]> 消费。
 */
import { del, get, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { TagListQuery, TagPayload, TagRecord } from '@/types/api/knowhub/tag'

/**
 * 方法效果：
 * 查询标签列表（不分页，返回全量数组）。
 * 参数：
 * - `params`：标签查询条件（tagName 模糊 / status 0禁用1启用）。
 * 返回值：
 * - 后端 Result 包裹的标签数组。
 */
export const getTagListApi = (params?: TagListQuery) =>
  get<ApiResult<TagRecord[]>>('/tag/list', { params })

/**
 * 方法效果：
 * 根据标签主键获取标签详情。
 * 参数：
 * - `tagId`：标签主键。
 * 返回值：
 * - 后端 Result 包裹的标签详情对象。
 */
export const getTagDetailApi = (tagId: number) => get<ApiResult<TagRecord>>(`/tag/${tagId}`)

/**
 * 方法效果：
 * 新增标签（tagName 唯一）。
 * 参数：
 * - `data`：标签表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createTagApi = (data: TagPayload) =>
  post<ApiResult<boolean>, TagPayload>('/tag', data)

/**
 * 方法效果：
 * 编辑标签（tagId 必填）。
 * 参数：
 * - `data`：标签表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateTagApi = (data: TagPayload) =>
  put<ApiResult<boolean>, TagPayload>('/tag', data)

/**
 * 方法效果：
 * 批量删除标签。后端路径变量接收 Long[]，按逗号自动分割；
 * 软删标签并级联清理 blog_tag 中该标签的关联行。
 * 参数：
 * - `tagIds`：待删除的标签主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteTagsApi = (tagIds: number[]) =>
  del<ApiResult<boolean>>(`/tag/${tagIds.join(',')}`)
