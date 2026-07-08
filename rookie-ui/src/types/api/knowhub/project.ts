/**
 * 文件作用：
 * 定义项目管理模块对接后端所需的接口类型，
 * 与后端 ProjectVo / ProjectQuarry / ProjectReviewVo / ProjectReviewLogVo /
 * ProjectMemberVo / ProjectFileVo / ProjectFileTreeVo / ProjectCompetitionVo 对齐，
 * 供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 Date 经 jackson 序列化输出的 string 形式（契约不变），展示用 formatDateTime。
 * - type 取值见字典 project_type（当前仅 COMPETITION）；level 见字典 project_level（1/2/3）；
 *   status 见字典 project_status；reviewStatus 见字典 review_status（复用，NONE/PENDING/APPROVED/REJECTED）；
 *   action 见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客/资源/项目共用）；
 *   memberRole 见字典 project_member_role（LEADER/MENTOR/MEMBER）。
 * - 权限态 canView/canDownload/canEdit/myMemberRole 由后端详情接口回填，供前端控制按钮显隐。
 * - articleId 关联文章管理模块（待开发，非必填）。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 项目记录，与后端 ProjectVo 字段对齐。
 * type=COMPETITION 时配套有 ProjectCompetitionRecord 子表字段（详情/编辑回显）。
 */
export interface ProjectRecord {
  projectId?: number
  title: string
  /** 项目类型：COMPETITION 等（字典 project_type） */
  type: string
  /** 项目等级 1公开/2内部/3机密（字典 project_level，对标权限等级） */
  level: number
  summary?: string
  description?: string
  /** 关联文章管理ID，非必填。TODO: 文章管理模块开发时关联 */
  articleId?: number
  authorId?: number
  /** 负责人昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED（字典 project_status） */
  status?: string
  /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
  reviewStatus?: string
  publishTime?: string
  // ---- 当前用户对该项目的权限态（详情接口回填，列表不回填） ----
  canView?: boolean
  canDownload?: boolean
  canEdit?: boolean
  /** 当前用户在该项目的成员角色（null=非成员；LEADER/MENTOR/MEMBER） */
  myMemberRole?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 比赛项目子表记录，与后端 ProjectCompetitionVo 对齐（按 type=COMPETITION 取）。
 * 前端表单按 type 动态渲染对应子表字段，主表 type 切换时子表字段跟着切换。
 */
export interface ProjectCompetitionRecord {
  projectId?: number
  competitionName: string
  /** 比赛级别：校级/省级/国家级/国际级 */
  competitionLevel?: string
  /** 获奖等级：特等/一等/二等/三等/优秀/无 */
  awardLevel?: string
  awardTime?: string
  competitionTime?: string
}

/**
 * 项目列表查询参数，与后端 ProjectQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 * userViewLevel / userId 为后端 service 层回填字段，前端不传。
 */
export interface ProjectListQuery extends Partial<PageQueryParams> {
  title?: string
  type?: string
  level?: number
  status?: string
  reviewStatus?: string
  createBy?: string
  beginTime?: string
  endTime?: string
}

export type ProjectPageResult = NormalizedPageResult<ProjectRecord>

/**
 * 项目审核入参，与后端 ProjectReviewVo 对齐。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 */
export interface ProjectReviewPayload {
  projectId: number
  pass: boolean
  advice?: string
}

/**
 * 项目审核流水记录，与后端 ProjectReviewLogVo 字段对齐。
 * action 取值见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH，博客/资源/项目共用）。
 * operator 为操作人用户名快照，operatorNickname 由后端 join sys_user 带出便于直接展示。
 * role 为审核业务身份（AUTHOR作者/REVIEWER审核员/SYSTEM系统直通），按动作类型定非系统角色。
 */
export interface ProjectReviewLogRecord {
  reviewLogId?: number
  projectId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/**
 * 项目成员记录，与后端 ProjectMemberVo 对齐。
 * memberRole: LEADER/MENTOR/MEMBER（字典 project_member_role）。
 * canView/canDownload/canEdit 为项目内权限标志位（单项目生效，与他项目无关，不分等级）。
 * nickname/username 由后端 join sys_user 带出。
 */
export interface ProjectMemberRecord {
  memberId?: number
  projectId?: number
  userId: number
  memberRole: string
  canView?: number
  canDownload?: number
  canEdit?: number
  /** 成员昵称（join sys_user 带出） */
  nickname?: string
  /** 成员用户名（join sys_user 带出） */
  username?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 项目文件树节点记录（扁平，带 parentId），与后端 ProjectFileVo 对齐。
 * isDir: 1=目录(objectId 为空) / 0=文件(objectId 指向 file_object)。
 * originalName/contentLength/contentType/businessType 由后端 join file_object 带出（目录为空）。
 */
export interface ProjectFileRecord {
  fileId?: number
  projectId?: number
  parentId?: number
  name: string
  isDir: number
  objectId?: number
  sort?: number
  originalName?: string
  contentLength?: number
  contentType?: string
  /** 文件业务类型（join file_object 带出 PROJECT_SRC/PKG/DOC，供前端分组展示） */
  businessType?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 项目文件树形记录，与后端 ProjectFileTreeVo 对齐，供前端 GitHub 式侧边栏递归渲染。
 * 字段与 ProjectFileRecord 对齐，额外带 children 列表（目录才有，文件为 undefined）。
 */
export interface ProjectFileTreeNode extends Omit<ProjectFileRecord, 'fileId' | 'projectId' | 'parentId'> {
  fileId?: number
  projectId?: number
  parentId?: number
  /** 子节点（目录才有）；前端递归渲染展开/折叠 */
  children?: ProjectFileTreeNode[]
}

/** 项目文件类型常量（与后端 ProjectFileType 枚举对齐）：1=目录 / 0=文件 */
export const PROJECT_FILE_IS_DIR = 1
export const PROJECT_FILE_IS_FILE = 0

/** 文件业务类型常量（与后端 FileBusinessType 枚举对齐，项目文件三类） */
export const PROJECT_BUSINESS_TYPE = {
  SRC: 'PROJECT_SRC',
  PKG: 'PROJECT_PKG',
  DOC: 'PROJECT_DOC',
} as const
