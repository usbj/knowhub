/**
 * 文件作用：
 * 「我的协作」中心页对接后端 /authoring/contributor/** + /authoring/invite/** 的接口。
 * 走 /authoring/** authenticated 兜底，需登录态。同意/拒绝操作放本独立页，不调 confirmNoticeApi
 * （NotifySupport needConfirm 仍 0，通知仅提醒+跳转 /collaboration?tab=xxx）。
 * 通知展示全走既有 getMyNoticesApi（/sys/notice/my → noticeStore.myNotices → 顶栏铃铛 + /profile?tab=message）。
 * - applyContributor：申请成为文章贡献者（在 detail.vue 入口调用，建一条 PENDING 并通知作者）。
 * - myContributorStatus：查当前用户对该文章的贡献申请态，detail.vue 申请按钮态判定。
 * - listReceivedContributors：我收到的文章贡献申请（我是文章作者）。
 * - listMineContributors：我申请过的文章贡献资格（只读状态列表）。
 * - acceptContributor / rejectContributor：作者处理申请。
 * - listReceivedInvites：我收到的项目邀请。
 * - acceptInvite / rejectInvite：受邀人处理邀请。
 */
import { get, getPage, put } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type {
  ArticleContributorRecord,
  ProjectInviteRecord,
} from '@/types/api/collaboration'

// ============================ 文章贡献申请 ============================

/** 查当前用户对该文章的贡献申请态（detail.vue 申请按钮态判定：null 未申请/作者，PENDING/APPROVED/REJECTED） */
export const myContributorStatusApi = (articleId: number) =>
  get<ApiResult<string | null>>(`/authoring/contributor/${articleId}/my-status`)

/** 我收到的文章贡献申请（我是文章作者，可按 status 过滤 PENDING/APPROVED/REJECTED） */
export const listReceivedContributorsApi = (status?: string) =>
  getPage<ArticleContributorRecord>('/authoring/contributor/list-received',
    status ? { params: { status } } : undefined)

/** 我申请过的文章贡献资格（只读状态列表，可按 status 过滤） */
export const listMineContributorsApi = (status?: string) =>
  getPage<ArticleContributorRecord>('/authoring/contributor/list-mine',
    status ? { params: { status } } : undefined)

/** 作者同意文章贡献申请。 */
export const acceptContributorApi = (contributorId: number) =>
  put<ApiResult<boolean>>(`/authoring/contributor/${contributorId}/accept`)

/** 作者驳回文章贡献申请（advice 必填）。 */
export const rejectContributorApi = (contributorId: number, advice: string) =>
  put<ApiResult<boolean>>(`/authoring/contributor/${contributorId}/reject`, undefined, { params: { advice } })

// ============================ 项目邀请 ============================

/** 我收到的项目邀请（受邀人侧，可按 status 过滤 PENDING/ACCEPTED/REJECTED） */
export const listReceivedInvitesApi = (status?: string) =>
  getPage<ProjectInviteRecord>('/authoring/invite/list-received',
    status ? { params: { status } } : undefined)

/** 受邀人同意项目邀请（写成员 MEMBER 行 + 邀请置 ACCEPTED + 通知负责人）。 */
export const acceptInviteApi = (inviteId: number) =>
  put<ApiResult<boolean>>(`/authoring/invite/${inviteId}/accept`)

/** 受邀人拒绝项目邀请。 */
export const rejectInviteApi = (inviteId: number) =>
  put<ApiResult<boolean>>(`/authoring/invite/${inviteId}/reject`)

// 类型再导出
export type {
  ArticleContributorRecord,
  ProjectInviteRecord,
}
export type { NormalizedPageResult }