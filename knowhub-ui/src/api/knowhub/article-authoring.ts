/**
 * 文件作用：
 * 文章/章节创作接口（/authoring/article/** + /authoring/chapter/**，走 /authoring/** authenticated 兜底，需登录态）。
 * 复用后台 ArticleService/ChapterService（addArticleInfo/editArticleInfo/publishArticle/revokeArticle/
 * getArticleInfo/quarryArticle + submitChapter/editChapterInfo/publishChapter/revokeChapter/
 * deleteChapterInfo/getChapterInfo/quarryChapter），后端含状态机/权限/审核校验，前端只做薄封装。
 * - 文章草稿/编辑：ArticleVo 体提交（编辑带 articleId，新建不带）。
 * - 章节提交/编辑：ChapterVo 体提交（编辑带 chapterId，新建带 articleId）。
 * - publish/revoke/delete：仅路径变量，后端状态机校验。
 * - getForEdit：编辑回填，复用后台 getArticleInfo/getChapterInfo（canOp 防越权，含全态+权限态）。
 * - myLevel：当前用户文章 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。
 * 标签选择器数据源见 ./blog.ts 的 listEnabledTagsApi（标签是博客+文章共用）。
 */
import { get, getPage, post, put, del } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type {
  ArticleAuthoringPayload,
  ArticleAuthoringDetail,
  ArticleRecord,
  MyArticleListQuery,
  ChapterAuthoringPayload,
  ChapterAuthoringDetail,
  MyChapterListQuery,
  ChapterReorderPayload,
} from '@/types/api/knowhub/article-authoring'

// ============================ 文章创作 ============================

/** 当前用户文章 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。 */
export const getMyArticleLevelApi = () =>
  get<ApiResult<number>>('/authoring/article/level')

/**
 * 前台我的文章列表（薄封装 quarryArticle，service 内回填 userId 走 author_id 分支，
 * 天然只返回"自己写的 + 有权看的"）。可选 status 过滤草稿/已发布。
 */
export const getMyArticlesApi = (query: MyArticleListQuery) =>
  getPage<ArticleRecord>('/authoring/article/list', { params: query })

/** 编辑回填：复用后台 getArticleInfo（canOp 防越权，返回含全态+标签+canEdit+isAuthor）。 */
export const getArticleForEditApi = (articleId: number) =>
  get<ApiResult<ArticleAuthoringDetail>>(`/authoring/article/${articleId}`)

/** 前台新建文章草稿（复用 addArticleInfo，作者=current user，DRAFT）。返回后端 boolean。 */
export const draftArticleApi = (data: ArticleAuthoringPayload) =>
  post<ApiResult<boolean>, ArticleAuthoringPayload>('/authoring/article', data)

/** 前台编辑文章（复用 editArticleInfo，校验归属+状态机+先删后插标签）。编辑体需带 articleId。 */
export const editArticleApi = (data: ArticleAuthoringPayload) =>
  put<ApiResult<boolean>, ArticleAuthoringPayload>('/authoring/article', data)

/** 前台发布文章（复用 publishArticle，按审核开关决定 PUBLISHED 或 PENDING_REVIEW）。 */
export const publishArticleAuthoringApi = (articleId: number) =>
  put<ApiResult<boolean>>(`/authoring/article/${articleId}/publish`)

/** 前台撤回文章（复用 revokeArticle → REVOKED，撤回后可再编辑/再发布）。仅 PUBLISHED 可撤回。 */
export const revokeArticleAuthoringApi = (articleId: number) =>
  put<ApiResult<boolean>>(`/authoring/article/${articleId}/revoke`)

// ============================ 章节创作 ============================

/**
 * 前台某文章的章节列表（薄封装 quarryChapter，需 articleId；service 校验能看该文章）。
 * 列表 item 用 ChapterAuthoringDetail 子集（含 status/authorNickname/canEdit/canReview）。
 */
export const getMyChaptersApi = (query: MyChapterListQuery) =>
  getPage<ChapterAuthoringDetail>('/authoring/chapter/list', { params: query })

/** 章节编辑回填：复用后台 getChapterInfo（章节可见性=文章可见性防越权，回填 canEdit/canReview）。 */
export const getChapterForEditApi = (chapterId: number) =>
  get<ApiResult<ChapterAuthoringDetail>>(`/authoring/chapter/${chapterId}`)

/** 前台提交新章节（复用 submitChapter，按文章 visibility 决定状态机：作者免审/非作者 PRIVATE拒-SEMIPUBLIC审-PUBLIC免审）。 */
export const submitChapterApi = (data: ChapterAuthoringPayload) =>
  post<ApiResult<boolean>, ChapterAuthoringPayload>('/authoring/chapter', data)

/** 前台编辑章节（复用 editChapterInfo，PUBLISHED 须先撤回改）。编辑体需带 chapterId。 */
export const editChapterApi = (data: ChapterAuthoringPayload) =>
  put<ApiResult<boolean>, ChapterAuthoringPayload>('/authoring/chapter', data)

/** 前台再次提交/发布章节（复用 publishChapter，用于 DRAFT/REJECTED/REVOKED 再提交）。 */
export const publishChapterAuthoringApi = (chapterId: number) =>
  put<ApiResult<boolean>>(`/authoring/chapter/${chapterId}/publish`)

/** 前台撤回章节（复用 revokeChapter → REVOKED，可再编辑/再提交）。仅 PUBLISHED 可撤回。 */
export const revokeChapterAuthoringApi = (chapterId: number) =>
  put<ApiResult<boolean>>(`/authoring/chapter/${chapterId}/revoke`)

/** 前台删除章节（复用 deleteChapterInfo，章节作者 OR 文章作者 OR delete 权限）。chapterIds 逗号分隔。 */
export const deleteChapterApi = (chapterIds: number | number[]) =>
  del<ApiResult<boolean>>(`/authoring/chapter/${Array.isArray(chapterIds) ? chapterIds.join(',') : chapterIds}`)

/**
 * 前台批量重排章节顺序（长按拖拽持久化，PUT /authoring/chapter/reorder）。
 * **入参顶层即 List<ChapterVo>**（后端 @RequestBody List<ChapterVo> orders 期望 JSON 数组，
 * 不是 {orders:[...]} 包裹对象）。每项 = 拖拽后新顺序逐章生成的 {chapterId, sortOrder=新index, articleId}，
 * 后端逐章 canEdit 校验 + 事务。
 */
export const reorderChaptersApi = (orders: ChapterReorderPayload) =>
  put<ApiResult<boolean>, ChapterReorderPayload>('/authoring/chapter/reorder', orders)

/** 类型再导出，供页面直接用 */
export type {
  ArticleAuthoringPayload,
  ArticleAuthoringDetail,
  ArticleRecord,
  MyArticleListQuery,
  ChapterAuthoringPayload,
  ChapterAuthoringDetail,
  MyChapterListQuery,
  ChapterReorderPayload,
}
export type { NormalizedPageResult }