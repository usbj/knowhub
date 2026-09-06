/**
 * 文件作用：
 * 集中管理资源管理模块对接后端的接口方法，
 * 包括资源 CRUD、发布/撤回/审核、点赞/收藏/评分、下载、审核历史，供资源管理页统一调用。
 * 路由前缀 /resource（knowhub 命名空间，不套 /sys）。
 * 鉴权由后端 @PreAuthorize('knowhub:resource:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 * 互动接口（点赞/收藏/评分）后端无 @PreAuthorize，登录即可用（Token 已注入）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  ResourceCategoryRecord,
  ResourceCategoryTreeNode,
  ResourceListQuery,
  ResourcePageResult,
  ResourceRecord,
  ResourceReviewLogRecord,
  ResourceReviewPayload,
} from '@/types/api/knowhub/resource'

/**
 * 方法效果：
 * 分页查询资源列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：资源查询条件与分页参数。
 * 返回值：
 * - 归一化后的资源分页结果。
 */
export const getResourcePageApi = (params: ResourceListQuery) =>
  getPage<ResourceRecord>('/resource/list', {
    params,
  }) as Promise<ResourcePageResult>

/**
 * 方法效果：
 * 根据资源主键获取资源详情（含互动计数、当前用户态、FILE 下载链接、审核快照走流水表）。
 * 参数：
 * - `resourceId`：资源主键。
 * 返回值：
 * - 后端 Result 包裹的资源详情对象。
 */
export const getResourceDetailApi = (resourceId: number) =>
  get<ApiResult<ResourceRecord>>(`/resource/${resourceId}`)

/**
 * 方法效果：
 * 新增资源（新建即草稿，status=DRAFT）。FILE 类资源先传完文件拿 fileObjectId 再提交。
 * 参数：
 * - `data`：资源表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createResourceApi = (data: ResourceRecord) =>
  post<ApiResult<boolean>, ResourceRecord>('/resource', data)

/**
 * 方法效果：
 * 编辑资源（PUBLISHED 禁止编辑须先撤回；FILE 类可换文件，后端回填新文件业务关联）。
 * 参数：
 * - `data`：资源表单数据，resourceId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateResourceApi = (data: ResourceRecord) =>
  put<ApiResult<boolean>, ResourceRecord>('/resource', data)

/**
 * 方法效果：
 * 批量删除资源。后端路径变量接收 Long[]，按逗号自动分割；软删。
 * FILE 类资源级联软删关联 file_object 行（对象本体由 FileGcTask 异步清）。
 * 参数：
 * - `resourceIds`：待删除的资源主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteResourcesApi = (resourceIds: number[]) =>
  del<ApiResult<boolean>>(`/resource/${resourceIds.join(',')}`)

/**
 * 方法效果：
 * 发布资源。受全局审核开关 knowhub.resource.review_enabled 控制：
 * 开关关→直接 PUBLISHED；开关开→PENDING_REVIEW 待审。
 * 参数：
 * - `resourceId`：资源主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishResourceApi = (resourceId: number) =>
  put<ApiResult<boolean>>(`/resource/publish/${resourceId}`)

/**
 * 方法效果：
 * 撤回资源，状态置 REVOKED。
 * 参数：
 * - `resourceId`：资源主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeResourceApi = (resourceId: number) =>
  put<ApiResult<boolean>>(`/resource/revoke/${resourceId}`)

/**
 * 方法效果：
 * 审核资源。pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 * 参数：
 * - `payload`：审核入参（resourceId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const reviewResourceApi = (payload: ResourceReviewPayload) =>
  put<ApiResult<boolean>, ResourceReviewPayload>('/resource/review', payload)

/**
 * 方法效果：
 * 获取资源审核历史流水（按动作时间升序），供详情弹窗「审核历史」折叠区展示。
 * 参数：
 * - `resourceId`：资源主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表（含操作人昵称 operatorNickname）。
 */
export const getResourceReviewLogApi = (resourceId: number) =>
  get<ApiResult<ResourceReviewLogRecord[]>>(`/resource/review-log/${resourceId}`)

/**
 * 方法效果：
 * 点赞/取消点赞（toggle）。计数不冗余主表，走事实表聚合。
 * 参数：
 * - `resourceId`：资源主键。
 * - `liked`：true 点赞 / false 取消点赞。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const toggleResourceLikeApi = (resourceId: number, liked: boolean) =>
  put<ApiResult<boolean>>(`/resource/like/${resourceId}`, undefined, { params: { liked } })

/**
 * 方法效果：
 * 收藏/取消收藏（toggle）。计数不冗余主表，走事实表聚合。
 * 参数：
 * - `resourceId`：资源主键。
 * - `collected`：true 收藏 / false 取消收藏。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const toggleResourceCollectApi = (resourceId: number, collected: boolean) =>
  put<ApiResult<boolean>>(`/resource/collect/${resourceId}`, undefined, { params: { collected } })

/**
 * 方法效果：
 * 评分（1-5）。一人一资源可改分（upsert 事实表），评分均值/计数走聚合读时算。
 * 参数：
 * - `resourceId`：资源主键。
 * - `score`：评分 1-5。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const rateResourceApi = (resourceId: number, score: number) =>
  put<ApiResult<boolean>>(`/resource/rating/${resourceId}`, undefined, { params: { score } })

/**
 * 方法效果：
 * 获取 FILE 资源下载链接（校验 PUBLISHED + FILE 类型，下载量 +1）。
 * LINK 类型不走此接口（前端直接用 linkUrl 外链打开）。
 * 参数：
 * - `resourceId`：资源主键。
 * 返回值：
 * - 后端 Result 包裹的下载链接字符串（中转/预签名按访问模式）。
 */
export const downloadResourceApi = (resourceId: number) =>
  get<ApiResult<string>>(`/resource/download/${resourceId}`)
