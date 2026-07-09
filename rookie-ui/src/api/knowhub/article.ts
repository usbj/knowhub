/**
 * 文件作用：
 * 集中管理文章管理模块对接后端的接口方法，
 * 包括文章 CRUD、发布/撤回/审核、审核历史，供文章管理页统一调用。
 * 路由前缀 /article（knowhub 命名空间，不套 /sys）。
 * 鉴权由后端 @PreAuthorize('knowhub:article:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 * 等级权限(view/edit:lN)由后端 ArticlePermissionResolver 扫 perms 取最高等级判定，
 * 前端不直接处理等级，列表可见性由后端 SQL 过滤、详情按钮显隐由后端回填的 canView/canEdit/isAuthor 控制。
 * 文章无成员表（轻量权限：系统级 + 作者归属），无 download。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  ArticleListQuery,
  ArticlePageResult,
  ArticleRecord,
  ArticleReviewLogRecord,
  ArticleReviewPayload,
} from '@/types/api/knowhub/article'

/**
 * 方法效果：
 * 分页查询文章列表，并在请求层完成分页结果归一化。
 * 列表可见性由后端按权限过滤（level<=userViewLevel OR author_id=userId 作者能看自己的文章）。
 * 参数：
 * - `params`：文章查询条件与分页参数。
 * 返回值：
 * - 归一化后的文章分页结果。
 */
export const getArticlePageApi = (params: ArticleListQuery) =>
  getPage<ArticleRecord>('/article/list', {
    params,
  }) as Promise<ArticlePageResult>

/**
 * 方法效果：
 * 根据文章主键获取文章详情（含权限态 canView/canEdit/isAuthor，供前端控制按钮显隐）。
 * 参数：
 * - `articleId`：文章主键。
 * 返回值：
 * - 后端 Result 包裹的文章详情对象。
 */
export const getArticleDetailApi = (articleId: number) =>
  get<ApiResult<ArticleRecord>>(`/article/${articleId}`)

/**
 * 方法效果：
 * 新增文章（新建即草稿，status=DRAFT）。作者=当前用户。
 * 参数：
 * - `data`：文章表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createArticleApi = (data: ArticleRecord) =>
  post<ApiResult<boolean>, ArticleRecord>('/article', data)

/**
 * 方法效果：
 * 编辑文章（PUBLISHED 禁止编辑须先撤回；改 level 需自身编辑等级>=新等级，作者除外）。
 * 参数：
 * - `data`：文章表单数据，articleId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateArticleApi = (data: ArticleRecord) =>
  put<ApiResult<boolean>, ArticleRecord>('/article', data)

/**
 * 方法效果：
 * 批量删除文章。后端路径变量接收 Long[]，按逗号自动分割；软删。
 * 级联软删 chapter + 封面 file_object（对象本体由 FileGcTask 异步清）。
 * 仅作者或拥有 knowhub:article:delete 权限可删。
 * 参数：
 * - `articleIds`：待删除的文章主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteArticlesApi = (articleIds: number[]) =>
  del<ApiResult<boolean>>(`/article/${articleIds.join(',')}`)

/**
 * 方法效果：
 * 发布文章。受全局审核开关 knowhub.article.review_enabled（默认 true 开启）控制：
 * 开关关→直接 PUBLISHED；开关开→PENDING_REVIEW 待审。
 * 参数：
 * - `articleId`：文章主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishArticleApi = (articleId: number) =>
  put<ApiResult<boolean>>(`/article/publish/${articleId}`)

/**
 * 方法效果：
 * 撤回文章，状态置 REVOKED。
 * 参数：
 * - `articleId`：文章主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeArticleApi = (articleId: number) =>
  put<ApiResult<boolean>>(`/article/revoke/${articleId}`)

/**
 * 方法效果：
 * 审核文章。pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 * 作者不能审核自己文章（回避）。
 * 参数：
 * - `payload`：审核入参（articleId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const reviewArticleApi = (payload: ArticleReviewPayload) =>
  put<ApiResult<boolean>, ArticleReviewPayload>('/article/review', payload)

/**
 * 方法效果：
 * 获取文章审核历史流水（按动作时间升序），供详情弹窗「审核历史」折叠区展示。
 * 参数：
 * - `articleId`：文章主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表（含操作人昵称 operatorNickname）。
 */
export const getArticleReviewLogApi = (articleId: number) =>
  get<ApiResult<ArticleReviewLogRecord[]>>(`/article/review-log/${articleId}`)
