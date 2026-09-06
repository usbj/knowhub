/**
 * 文件作用：
 * 「我的协作」中心页对接后端 /authoring/contributor/** + /authoring/invite/** 的接口类型，
 * 与后端 ArticleContributorVo / ProjectInviteVo 对齐。
 * - ArticleContributorRecord：文章贡献申请（我收到的 / 我申请过的）；
 * - ProjectInviteRecord：项目邀请（我收到的）；
 * 时间字段用 string（后端 Date 经 jackson 输出 yyyy-MM-dd HH:mm:ss，照 knowhub-ui 全项目约定）。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 */

/** 文章贡献申请记录（我收到的=文章作者侧 / 我申请过的=申请人侧 共用此类型） */
export interface ArticleContributorRecord {
  contributorId: number
  articleId: number
  userId: number
  /** 申请状态：PENDING/APPROVED/REJECTED */
  status: string
  /** 驳回原因，仅 REJECTED 填 */
  advice?: string
  /** 申请者 username 快照 */
  applyBy?: string
  applyTime?: string
  /** 审批人 username 快照（文章作者） */
  handleBy?: string
  handleTime?: string
  // ---- join 带出 ----
  articleTitle?: string
  /** 申请者昵称（我收到的列表展示申请人人用） */
  nickname?: string
  /** 文章作者昵称（我申请过的列表展示审批人用） */
  articleNickname?: string
  /** 文章作者 userId（前端判定用） */
  articleAuthorId?: number
}

/** 项目邀请记录（我收到的=受邀人侧） */
export interface ProjectInviteRecord {
  inviteId: number
  projectId: number
  inviteeUserId: number
  /** 邀请状态：PENDING/ACCEPTED/REJECTED */
  status: string
  inviterBy?: string
  inviteTime?: string
  handleBy?: string
  handleTime?: string
  // ---- join 带出 ----
  projectTitle?: string
  /** 邀请人昵称（即项目作者/负责人） */
  inviterNickname?: string
  projectAuthorId?: number
}

/** 章节审核入参体（与 reviewChapterAuthoringApi 对齐，独立模块受 page 复用方便） */
export interface ChapterReviewPayload {
  chapterId: number
  pass: boolean
  advice?: string
}