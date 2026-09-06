/**
 * 文件作用：
 * 集中管理资源分类管理对接后端的接口方法，
 * 包括分类树查询、详情、增删改，供资源分类管理页与资源表单分类下拉树复用。
 * 路由前缀 /resource-category（knowhub 命名空间，不套 /sys）。
 * 鉴权由后端 @PreAuthorize('knowhub:resource:category:*') 控制。
 */
import { del, get, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { ResourceCategoryRecord, ResourceCategoryTreeNode } from '@/types/api/knowhub/resource'

/**
 * 方法效果：
 * 获取资源分类树（全量启用分类组树，前端 el-tree 渲染）。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的分类树节点列表。
 */
export const getResourceCategoryTreeApi = () =>
  get<ApiResult<ResourceCategoryTreeNode[]>>('/resource-category/tree')

/**
 * 方法效果：
 * 获取分类详情。
 * 参数：
 * - `categoryId`：分类主键。
 * 返回值：
 * - 后端 Result 包裹的分类记录。
 */
export const getResourceCategoryDetailApi = (categoryId: number) =>
  get<ApiResult<ResourceCategoryRecord>>(`/resource-category/${categoryId}`)

/**
 * 方法效果：
 * 新增分类。parentId 缺省置 0（顶级）。
 * 参数：
 * - `data`：分类表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createResourceCategoryApi = (data: ResourceCategoryRecord) =>
  post<ApiResult<boolean>, ResourceCategoryRecord>('/resource-category', data)

/**
 * 方法效果：
 * 编辑分类。
 * 参数：
 * - `data`：分类表单数据，categoryId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateResourceCategoryApi = (data: ResourceCategoryRecord) =>
  put<ApiResult<boolean>, ResourceCategoryRecord>('/resource-category', data)

/**
 * 方法效果：
 * 删除分类。有子分类拒绝删；无子分类把挂载资源置 -1（其他）后软删。
 * 参数：
 * - `categoryId`：分类主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteResourceCategoryApi = (categoryId: number) =>
  del<ApiResult<boolean>>(`/resource-category/${categoryId}`)
