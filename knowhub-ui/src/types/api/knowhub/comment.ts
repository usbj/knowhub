/**
 * 文件作用：
 * 前台评论类型，与后端 com.knowhub.pojo.comment.vo.{CommentPortalVo,CommentReplyVo,CommentCreateVo,CommentReviewVo} 对齐。
 * 时间字段后端 java.util.Date 经 jackson 全局格式化，前端一律标 string。
 * 私加字段标「【前端私加】」运行时由前端从其他字段派生（不进后端 VO），照 blog.ts 块注释范式。
 */

/** 评论业务类型（四类作品共用） */
export type CommentBizType = 'BLOG' | 'ARTICLE' | 'PROJECT' | 'RESOURCE'

/** 顶级评论列表项（/portal/comment/list） */
export interface CommentRecord {
  commentId: number
  /** 评论发起人 userId */
  authorId: number
  /** 评论人昵称（后端 join sys_user 带出） */
  authorNickname?: string
  /** 评论内容（Markdown，含配图 inline ![](/file/resolve/{id}) 相对引用；渲染走 v-md-preview） */
  content: string
  /** 创建时间（后端 Date→前端 string "yyyy-MM-dd HH:mm:ss"） */
  createTime: string
  /** 点赞数（冗余列读快） */
  likeCount: number
  /** 当前用户是否已点赞（登录态回填，未登录为 null） */
  hasLiked?: boolean | null
  /** 该顶级评论下的回复数（service 批量回填） */
  replyCount?: number
  /** 审核状态：NONE 直接可见 / PENDING 待作者确认(仅本人+作者) / APPROVED 通过 / REJECTED 已拒绝 */
  reviewStatus: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  /** 审核意见（REJECTED 时本人可见原因，可空） */
  reviewAdvice?: string
}

/** 回复列表项（/portal/comment/replies/{commentId}） */
export interface CommentReplyRecord {
  commentId: number
  authorId: number
  authorNickname?: string
  /** 回复内容（Markdown，含配图 inline ![](/file/resolve/{id})；渲染走 v-md-preview） */
  content: string
  createTime: string
  likeCount: number
  hasLiked?: boolean | null
  /** @某楼内某用户的 userId（直接回复楼主为 null） */
  replyToUserId?: number
  /** @人昵称快照（展示用） */
  replyToNickname?: string
  reviewStatus: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewAdvice?: string
}

/** 发评论入参（POST /authoring/comment） */
export interface CommentCreatePayload {
  bizType: CommentBizType
  bizId: number
  /** 评论内容（Markdown，含配图 inline ![](/file/resolve/{id})；限长 2000） */
  content: string
  /** 回复顶级评论时填，留空即顶级 */
  parentId?: number
  /** @某楼内某用户的 userId（仅 parentId 非空且非楼主时填） */
  replyToUserId?: number
}

/** 作者 inline 精选入参（POST /authoring/comment/{commentId}/review） */
export interface CommentReviewPayload {
  commentId: number
  /** 动作：APPROVE 同意展示 / REJECT 拒绝 */
  action: 'APPROVE' | 'REJECT'
  /** 审核意见（可选） */
  advice?: string
}

/** 评论列表查询参数（PageHelper 读 pageNum/pageSize） */
export interface CommentListQuery {
  pageNum?: number
  pageSize?: number
  /** 排序：new=时间倒序（默认），like=点赞倒序 */
  order?: 'new' | 'like'
}

/** 评论业务类型 union 用于详情页组件 props */
export type CommentBizTypeLiteral = CommentBizType