/**
 * 文件作用：
 * 前台统一评论接口（四类作品 blog/article/project/resource 共用）。
 * 读走 /portal/comment/**（permitAll，未登录可看已「同意展示」的评论）；写走 /authoring/comment/**（authenticated 兜底）。
 * 后端 com.knowhub.pojo.comment.* / com.knowhub.service.comment.impl.CommentService|CommentPortalService。
 * 列表/回复分页由 PageHelper 接管，后端 PageUtil 从请求读 pageNum/pageSize。
 * 点赞沿用博客范式：PUT /authoring/comment/{id}/like?liked=true|false（第3参 {params}，body null）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type {
  CommentCreatePayload,
  CommentRecord,
  CommentReplyRecord,
  CommentReviewPayload,
  CommentListQuery,
} from '@/types/api/knowhub/comment'

/** 顶级评论分页（指定作品 bizType+bizId；order=new/like，默认 new 按 createTime desc） */
export const listCommentsApi = (bizType: string, bizId: number, params?: CommentListQuery) =>
  getPage<CommentRecord>('/portal/comment/list', { params: { bizType, bizId, ...params } })

/** 某顶级评论下的回复分页（两层嵌套第二层） */
export const listRepliesApi = (commentId: number, params?: { pageNum?: number; pageSize?: number }) =>
  getPage<CommentReplyRecord>(`/portal/comment/replies/${commentId}`, { params })

/** 发评论（顶级或回复；parentId 非空即回复，留空即顶级）。返回新评论 commentId。 */
export const createCommentApi = (data: CommentCreatePayload) =>
  post<ApiResult<number>, CommentCreatePayload>('/authoring/comment', data)

/** 删评论（自删/作者删任意/admin 短路；删顶级连带其下回复）。 */
export const deleteCommentApi = (commentId: number) =>
  del<ApiResult<boolean>>(`/authoring/comment/${commentId}`)

/** 点赞/取消点赞评论（liked 缺省 true；主表 like_count 同步）。对齐 likeBlogApi 第3参范式。 */
export const toggleCommentLikeApi = (commentId: number, liked = true) =>
  put<ApiResult<boolean>>(`/authoring/comment/${commentId}/like`, null, { params: { liked } })

/** 作者 inline 精选（APPROVE 同意展示 / REJECT 拒绝）。作者本人作品下评论才可调。 */
export const reviewCommentApi = ({ commentId, action, advice }: CommentReviewPayload) =>
  post<ApiResult<boolean>, { commentId: number; action: string; advice?: string }>(
    `/authoring/comment/${commentId}/review`,
    { commentId, action, advice },
  )