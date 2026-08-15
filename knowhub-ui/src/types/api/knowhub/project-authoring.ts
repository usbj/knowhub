/**
 * 文件作用：
 * 定义项目创作对接后端 /authoring/project/** 的接口类型，与后端 ProjectVo 对齐。
 * 创作提交载荷（ProjectAuthoringPayload）只带后端接纳的创作相关字段（只读计数/审核快照/作者字段不带）；
 * 编辑回填（ProjectAuthoringDetail）消费后端 getProjectInfo 返回的 ProjectVo 子集（含权限态 canView/canDownload/canEdit/myMemberRole）。
 * 成员管理（ProjectMemberRecord）与文件树（ProjectFileRecord/ProjectFileTreeNode）类型复用平台口径，与后台 ProjectMemberVo / ProjectFileVo 对齐。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 * 项目无标签体系，故无 tagIds（与 BlogAuthoringPayload 差异点）。
 */
import type { NormalizedPageResult } from '../common'

/**
 * 创作提交载荷（与后端 ProjectVo 创作相关字段对齐，只读字段不带）。
 * - projectId：编辑时带（后端 editProjectInfo 需校验归属+canOp(edit)），草稿新建不带。
 * - level：1公开 2内部 3机密；后端 addProjectInfo 分级创作闸拦越级创建（需自身 view 等级>=新 level）。
 * - type=COMPETITION 时配套提交 competitionName/competitionLevel/awardLevel/awardTime/competitionTime 子表字段。
 *   （后端 ProjectVo 含 competition 子表嵌套对象，前端按 type 动态渲染；暂以扁平字段表 stretch，初版仅落主表字段，
 *    子表对接留后续 iteration。）
 * - description：项目正文（markdown），编辑器走 presignedUploadFlow，businessType=PROJECT_DOC。
 */
export interface ProjectAuthoringPayload {
  projectId?: number
  title: string
  type: string
  level: number
  summary?: string
  description?: string
  /** 关联文章管理ID，非必填（文章管理模块开发时关联，初期可空） */
  articleId?: number
  // ---- 比赛项目子表字段（type=COMPETITION 时启用，后端按 type 写子表） ----
  competitionName?: string
  competitionLevel?: string
  awardLevel?: string
  awardTime?: string
  competitionTime?: string
  /** 评论区开关 1开/0关，作者在创作页勾选 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，开启后新评论需作者同意后对他人展示 */
  commentCurated?: number
}

/**
 * 编辑回填数据（getProjectInfo 返回的 ProjectVo 子集，前端创作页只消费这些字段）。
 * - status：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED，驱动按钮态（PUBLISHED 须先撤回才可编辑）。
 * - canView/canDownload/canEdit/myMemberRole：后端回填权限态，前端据此判断是否允许编辑、文件树/下载按钮显隐。
 * 复用后台 getProjectInfo（含全态+权限态），与博客 getBlogForEditApi 同构。
 */
export interface ProjectAuthoringDetail {
  projectId: number
  title: string
  type: string
  level: number
  summary?: string
  description?: string
  articleId?: number
  authorId?: number
  authorNickname?: string
  status?: string
  reviewStatus?: string
  publishTime?: string
  // ---- 权限态（后端详情回填） ----
  canView?: boolean
  canDownload?: boolean
  canEdit?: boolean
  myMemberRole?: string
  /** 评论区开关 1开/0关，编辑回填用 */
  commentEnabled?: number
  /** 评论精选开关 0关1开，编辑回填用 */
  commentCurated?: number
}

/** 我的项目列表查询参数（前台 /authoring/project/list） */
export interface MyProjectListQuery {
  /** 可选状态过滤（DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED），留空取全部我可看的 */
  status?: string
  /** 标题关键字（后端 quarryProject 走 LIKE，与后台一致） */
  title?: string
  /** 项目类型过滤 */
  type?: string
  pageNum?: number
  pageSize?: number
}

/**
 * 我的项目列表项（后台 ProjectVo 子集，前台消费列表展示字段）。
 * 复用 quarryProject 出参，只取展示需要的字段，其它字段（description/审核/权限态/计数）忽略或按需。
 */
export interface MyProjectRecord {
  projectId: number
  title: string
  type: string
  level: number
  summary?: string
  status?: string
  reviewStatus?: string
  authorNickname?: string
  publishTime?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  downloadCount?: number
  createTime?: string
  updateTime?: string
}

/**
 * 项目成员记录，与后端 ProjectMemberVo 对齐（复用后台口径）。
 * memberRole: LEADER总裁/MENTOR导师/MEMBER参与者（字典 project_member_role）。
 * canView/canDownload/canEdit 为项目内权限标志位（单项目生效，与他项目无关，不分等级；LEADER 不看标志位全权）。
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
  nickname?: string
  username?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

/**
 * 项目文件树节点记录（扁平，带 parentId），与后端 ProjectFileVo 对齐（复用后台口径）。
 * isDir: 1=目录(objectId=null) / 0=文件(objectId 指向 file_object)。
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
  /** 文件业务类型（join file_object 带出 PROJECT_SRC/PKG/DOC） */
  businessType?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

/** 文件树形节点（后端 listFileTree 出参，带 children，与后台 ProjectFileTreeVo 对齐） */
export interface ProjectFileTreeNode extends Omit<ProjectFileRecord, 'fileId' | 'projectId' | 'parentId'> {
  fileId?: number
  projectId?: number
  parentId?: number
  children?: ProjectFileTreeNode[]
}

/** 文件类型常量（与后端 ProjectFileType 枚举对齐）：1=目录 / 0=文件 */
export const PROJECT_FILE_IS_DIR = 1
export const PROJECT_FILE_IS_FILE = 0

/** 项目文件业务类型常量（与后端 FileBusinessType 枚举对齐，项目文件三类） */
export const PROJECT_BUSINESS_TYPE = {
  SRC: 'PROJECT_SRC',
  PKG: 'PROJECT_PKG',
  DOC: 'PROJECT_DOC',
} as const

export type MyProjectPageResult = NormalizedPageResult<MyProjectRecord>