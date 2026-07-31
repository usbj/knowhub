/**
 * 文件作用：
 * 前台文章门户接口（/portal/article/*，公开免登录）+ 文章互动接口（/authoring/article/**，需登录态）。
 * 文章 = 章节集合（文档站结构）：列表不带正文，详情给章节大纲，章节正文单独拉取。
 * 搜索覆盖标题/简介/章节正文，命中章节由 matchedChapters 标出（前端在卡片上点跳章节阅读页）。
 * 标签热度榜复用既有 /portal/tag/hot（跨 blog+article 统一聚合），不在此处定义，见 ./blog.ts。
 */
import { get, getPage, put } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type {
  ArticlePortalRecord,
  ArticlePortalDetailRecord,
  ArticlePortalSearchQuery,
  ChapterContentRecord,
} from '@/types/api/knowhub/article'

/**
 * 前台文章搜索（标题/简介/章节正文全检索+标签/作者复合过滤+排序，命中章节标出，分页）。
 * 后端 PageHelper 归一化，直接返回 NormalizedPageResult。
 */
export const searchArticlesApi = (query: ArticlePortalSearchQuery) =>
  getPage<ArticlePortalRecord>('/portal/article/search', { params: query })

/** 前台文章个性化推荐 feed（标签命中×5+收藏×3+点赞×2+浏览×1+时间衰减多维打分） */
export const recommendArticlesApi = (size = 10, excludeArticleId?: number) =>
  get<ApiResult<ArticlePortalRecord[]>>('/portal/article/recommend', {
    params: { size, excludeArticleId },
  })

/** 前台文章详情（章节大纲+越级锁态降级，正文走章节接口） */
export const getArticleDetailApi = (articleId: number) =>
  get<ApiResult<ArticlePortalDetailRecord>>(`/portal/article/${articleId}`)

/** 前台章节正文（达权下发正文+计章节浏览量，越级锁态 content 为 null） */
export const getChapterContentApi = (articleId: number, chapterId: number) =>
  get<ApiResult<ChapterContentRecord>>(`/portal/article/${articleId}/chapter/${chapterId}`)

/** 详情页相关推荐 */
export const relatedArticlesApi = (articleId: number, size = 10) =>
  get<ApiResult<ArticlePortalRecord[]>>(`/portal/article/${articleId}/related`, {
    params: { size },
  })

/** 收藏/取消收藏文章（collected=true 收藏, false 取消，主表 collect_count 同步） */
export const collectArticleApi = (articleId: number, collected: boolean) =>
  put<ApiResult<boolean>, unknown>(`/authoring/article/${articleId}/collect`, undefined, {
    params: { collected },
  })

/** 点赞/取消点赞文章（like=true 点赞, false 取消，主表 like_count 同步） */
export const likeArticleApi = (articleId: number, liked: boolean) =>
  put<ApiResult<boolean>, unknown>(`/authoring/article/${articleId}/like`, undefined, {
    params: { liked },
  })

/** 我的文章收藏列表（按收藏时间倒序，仅返回前台可见口径的已发布文章） */
export const listMyCollectedArticlesApi = (pageNum = 1, pageSize = 20) =>
  getPage<ArticlePortalRecord>('/authoring/article/collect/list', {
    params: { pageNum, pageSize },
  })

/** 类型再导出，供页面直接用 */
export type {
  ArticlePortalRecord,
  ArticlePortalDetailRecord,
  ArticlePortalSearchQuery,
  ChapterContentRecord,
}
export type { NormalizedPageResult }