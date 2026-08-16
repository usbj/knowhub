/**
 * 文件作用：
 * 博客创作接口（/authoring/blog/**，走 /authoring/** authenticated 兜底，需登录态）。
 * 复用后台 BlogService（addBlogInfo/publishBlog/editBlogInfo/revokeBlog/toggleLike/toggleCollect），后端含分级创作闸。
 * 点赞/收藏也在此路径下（2026-07-31 从后台 /blog/like|collect/{id} 挪来，与文章 /authoring/article/{id}/like|collect 范式对齐）。
 * - draft/edit：BlogVo 体提交（编辑带 blogId）。
 * - publish/revoke：仅 blogId 路径变量，后端状态机校验（DRAFT/REJECTED/REVOKED 可编辑发布，PUBLISHED 须先撤回）。
 * - getForEdit：编辑回填，复用后台 getBlogInfo（canOp 防越权，含全态+标签+canEdit/isAuthor）。
 * - myLevel：当前用户博客 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。
 * - like/collect：query 参数 true|false 切换（缺省 true），主表 like_count/collect_count 同步。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type { BlogAuthoringPayload, BlogAuthoringDetail, BlogRecord, MyBlogListQuery } from '@/types/api/knowhub/authoring'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'

/** 前台新建博客草稿（复用 addBlogInfo，含分级创作闸）。返回后端 boolean。 */
export const draftBlogApi = (data: BlogAuthoringPayload) =>
  post<ApiResult<boolean>, BlogAuthoringPayload>('/authoring/blog/draft', data)

/** 前台编辑博客（复用 editBlogInfo，校验归属+状态机+先删后插标签）。编辑体需带 blogId。 */
export const editBlogApi = (data: BlogAuthoringPayload) =>
  put<ApiResult<boolean>, BlogAuthoringPayload>('/authoring/blog', data)

/** 前台发布博客（复用 publishBlog，按审核开关决定 PUBLISHED 或 PENDING_REVIEW）。 */
export const publishBlogApi = (blogId: number) =>
  put<ApiResult<boolean>>(`/authoring/blog/${blogId}/publish`)

/** 前台撤回博客（复用 revokeBlog → REVOKED，撤回后可再编辑/再发布）。仅 PUBLISHED 可撤回。 */
export const revokeBlogApi = (blogId: number) =>
  put<ApiResult<boolean>>(`/authoring/blog/${blogId}/revoke`)

/** 前台删除博客（软删 blog.deleted=1，复用 deleteBlogInfo，仅作者或 admin；普通用户仅删自己写的）。 */
export const deleteBlogApi = (blogId: number) =>
  del<ApiResult<boolean>>(`/authoring/blog/${blogId}`)

/** 编辑回填：复用后台 getBlogInfo（canOp 防越权，返回含全态+标签+canEdit/isAuthor 的 BlogVo）。 */
export const getBlogForEditApi = (blogId: number) =>
  get<ApiResult<BlogAuthoringDetail>>(`/authoring/blog/${blogId}`)

/** 当前用户博客 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。 */
export const getMyBlogLevelApi = () =>
  get<ApiResult<number>>('/authoring/blog/level')

/** 点赞/取消点赞博客（liked 缺省 true；主表 like_count 同步）。2026-07-31 从后台 /blog/like/{id} 挪来。 */
export const likeBlogApi = (blogId: number, liked = true) =>
  put<ApiResult<boolean>>(`/authoring/blog/${blogId}/like`, null, { params: { liked } })

/** 收藏/取消收藏博客（collected 缺省 true；主表 collect_count 同步）。2026-07-31 从后台 /blog/collect/{id} 挪来。 */
export const collectBlogApi = (blogId: number, collected = true) =>
  put<ApiResult<boolean>>(`/authoring/blog/${blogId}/collect`, null, { params: { collected } })

/**
 * 前台我的博客列表（薄封装 quarryBlog，service 内回填 userId 走 author_id 分支，
 * 天然只返回"自己写的 + 有权看的"）。可选 status 过滤草稿/已发布等。
 * 返回后端 PageInfo 归一化为 NormalizedPageResult。
 */
export const getMyBlogsApi = (query: MyBlogListQuery) =>
  getPage<BlogRecord>('/authoring/blog/list', { params: query })

/**
 * 我的博客收藏列表（GET /authoring/blog/collect/list，按收藏时间倒序，仅前台可见口径的已发布博客）。
 * BlogPortalRecord 口径。后端 listMyCollected 照 article 范式实现：取"收藏 ∩ 前台可见"再按收藏顺序排。
 * 后端实际是一次性返回可见收藏全量（pageNum/pageSize 被忽略），前端取 records+total 用即可。
 */
export const listMyCollectedBlogsApi = (pageNum = 1, pageSize = 20) =>
  getPage<BlogPortalRecord>('/authoring/blog/collect/list', { params: { pageNum, pageSize } })

/** 类型再导出，供页面直接用 */
export type { BlogAuthoringPayload, BlogAuthoringDetail, BlogRecord, MyBlogListQuery }
export type { NormalizedPageResult }