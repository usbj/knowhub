/**
 * 文件作用：
 * 集中管理博客文章管理对接后端的接口方法，
 * 包括文章 CRUD、发布/撤回/审核，供博客管理页统一调用。
 * 路由前缀 /blog（knowhub 命名空间，不套 /sys）。
 * 鉴权由后端 @PreAuthorize('knowhub:blog:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { BlogListQuery, BlogPageResult, BlogRecord, ReviewPayload } from '@/types/api/knowhub/blog'

/**
 * 方法效果：
 * 分页查询博客列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：博客查询条件与分页参数。
 * 返回值：
 * - 归一化后的博客分页结果。
 */
export const getBlogPageApi = (params: BlogListQuery) =>
  getPage<BlogRecord>('/blog/list', {
    params,
  }) as Promise<BlogPageResult>

/**
 * 方法效果：
 * 根据博客主键获取博客详情（含正文、审核信息、当前用户点赞/收藏态）。
 * 参数：
 * - `blogId`：博客主键。
 * 返回值：
 * - 后端 Result 包裹的博客详情对象。
 */
export const getBlogDetailApi = (blogId: number) => get<ApiResult<BlogRecord>>(`/blog/${blogId}`)

/**
 * 方法效果：
 * 新增博客文章（新建即草稿，status=DRAFT）。
 * 参数：
 * - `data`：博客表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createBlogApi = (data: BlogRecord) =>
  post<ApiResult<boolean>, BlogRecord>('/blog', data)

/**
 * 方法效果：
 * 编辑博客文章（标签先删后插重建）。
 * 参数：
 * - `data`：博客表单数据，blogId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateBlogApi = (data: BlogRecord) =>
  put<ApiResult<boolean>, BlogRecord>('/blog', data)

/**
 * 方法效果：
 * 批量删除博客文章。后端路径变量接收 Long[]，按逗号自动分割；软删。
 * 参数：
 * - `blogIds`：待删除的博客主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteBlogsApi = (blogIds: number[]) =>
  del<ApiResult<boolean>>(`/blog/${blogIds.join(',')}`)

/**
 * 方法效果：
 * 发布博客文章。受全局审核开关 blog_review_enabled 控制：
 * 开关关→直接 PUBLISHED；开关开→PENDING_REVIEW 待审。
 * 参数：
 * - `blogId`：博客主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishBlogApi = (blogId: number) =>
  put<ApiResult<boolean>>(`/blog/publish/${blogId}`)

/**
 * 方法效果：
 * 撤回博客文章，状态置 REVOKED。
 * 参数：
 * - `blogId`：博客主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeBlogApi = (blogId: number) =>
  put<ApiResult<boolean>>(`/blog/revoke/${blogId}`)

/**
 * 方法效果：
 * 审核博客文章。pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 * 参数：
 * - `payload`：审核入参（blogId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const reviewBlogApi = (payload: ReviewPayload) =>
  put<ApiResult<boolean>, ReviewPayload>('/blog/review', payload)
