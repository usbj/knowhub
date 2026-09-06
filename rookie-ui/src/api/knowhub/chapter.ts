/**
 * 文件作用：
 * 集中管理章节管理模块对接后端的接口方法，
 * 包括章节 CRUD、提交/撤回/作者审核、审核历史，供章节管理页统一调用。
 * 路由前缀 /chapter（knowhub 命名空间，不套 /sys）。
 * 章节是文章的子模块（无独立菜单页，从文章列表点"章节"跳二级路由页）。
 * 鉴权由后端 @PreAuthorize('knowhub:chapter:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 * 章节编辑/审核实际可见性由后端按章节可见性=文章可见性 + 提交者/作者归属判定，前端仅按钮显隐门槛。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  ChapterListQuery,
  ChapterPageResult,
  ChapterRecord,
  ChapterReviewLogRecord,
  ChapterReviewPayload,
} from '@/types/api/knowhub/chapter'

/**
 * 方法效果：
 * 分页查询章节列表（按 articleId 过滤），并在请求层完成分页结果归一化。
 * 列表不带 content 大字段（避免拖列表）；可见性由后端按章节可见性=文章可见性过滤。
 * 参数：
 * - `params`：章节查询条件与分页参数（articleId 必传）。
 * 返回值：
 * - 归一化后的章节分页结果。
 */
export const getChapterPageApi = (params: ChapterListQuery) =>
  getPage<ChapterRecord>('/chapter/list', {
    params,
  }) as Promise<ChapterPageResult>

/**
 * 方法效果：
 * 根据章节主键获取章节详情（含正文 content 大字段 + 权限态 canEdit/canReview）。
 * 参数：
 * - `chapterId`：章节主键。
 * 返回值：
 * - 后端 Result 包裹的章节详情对象。
 */
export const getChapterDetailApi = (chapterId: number) =>
  get<ApiResult<ChapterRecord>>(`/chapter/${chapterId}`)

/**
 * 方法效果：
 * 新增/提交章节。后端按文章 visibility + 提交者是否文章作者决定状态机分支：
 * 作者提交任意 visibility 免审直接 PUBLISHED；非作者提交 PRIVATE 拒绝；
 * 非作者提交 SEMIPUBLIC 进 PENDING_AUTHOR_REVIEW 待作者审；非作者提交 PUBLIC 直接 PUBLISHED 免审。
 * 参数：
 * - `data`：章节表单数据（articleId/chapterName/content 必填）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const submitChapterApi = (data: ChapterRecord) =>
  post<ApiResult<boolean>, ChapterRecord>('/chapter', data)

/**
 * 方法效果：
 * 编辑章节（PUBLISHED 禁编须先撤回；PENDING_AUTHOR_REVIEW 审核中不能改）。
 * 权限：章节作者 OR 文章作者 OR 系统编辑权限够。
 * 参数：
 * - `data`：章节表单数据，chapterId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateChapterApi = (data: ChapterRecord) =>
  put<ApiResult<boolean>, ChapterRecord>('/chapter', data)

/**
 * 方法效果：
 * 批量删除章节。后端路径变量接收 Long[]，按逗号自动分割；软删。
 * 仅章节作者/文章作者或拥有 knowhub:chapter:delete 权限可删。
 * 参数：
 * - `chapterIds`：待删除的章节主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteChaptersApi = (chapterIds: number[]) =>
  del<ApiResult<boolean>>(`/chapter/${chapterIds.join(',')}`)

/**
 * 方法效果：
 * 提交/发布章节（DRAFT/REJECTED/REVOKED 再提交，按文章 visibility 决定走不走作者审）。
 * 与 submitChapterApi 同构，用于已存在章节的再提交场景。
 * 参数：
 * - `chapterId`：章节主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishChapterApi = (chapterId: number) =>
  put<ApiResult<boolean>>(`/chapter/publish/${chapterId}`)

/**
 * 方法效果：
 * 撤回章节，状态置 REVOKED（仅 PUBLISHED 可撤回）。
 * 参数：
 * - `chapterId`：章节主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeChapterApi = (chapterId: number) =>
  put<ApiResult<boolean>>(`/chapter/revoke/${chapterId}`)

/**
 * 方法效果：
 * 章节作者审核（仅 PENDING_AUTHOR_REVIEW 可审）。pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 * 审核人必须是文章作者（或拥有 knowhub:chapter:review 系统权限代审）；章节提交者不能审自己提交的（回避）。
 * 参数：
 * - `payload`：审核入参（chapterId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const reviewChapterApi = (payload: ChapterReviewPayload) =>
  put<ApiResult<boolean>, ChapterReviewPayload>('/chapter/review', payload)

/**
 * 方法效果：
 * 获取章节作者审核历史流水（按动作时间升序），供章节详情弹窗「审核历史」折叠区展示。
 * 参数：
 * - `chapterId`：章节主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表（含操作人昵称 operatorNickname）。
 */
export const getChapterReviewLogApi = (chapterId: number) =>
  get<ApiResult<ChapterReviewLogRecord[]>>(`/chapter/review-log/${chapterId}`)
