/**
 * 文件作用：
 * 项目创作接口（/authoring/project/**，走 /authoring/** authenticated 兜底，需登录态）。
 * 薄封装复用后台 ProjectService（addProjectInfo/editProjectInfo/publishProject/revokeProject/getProjectInfo/
 * quarryProject + 成员/文件树全量业务：状态机、回避、LEADER 唯一性、事务、分级创作闸、canOp 校验）。
 * 后端 controller 在 ProjectAuthoringController，前台无需按钮权限键——登录即可创作自己的项目。
 * - draft/edit：ProjectVo 体提交（编辑带 projectId，新建不带）。
 * - publish/revoke：仅 projectId 路径变量，后端状态机校验（DRAFT/REJECTED/REVOKED 可发布，PUBLISHED 须先撤回）。
 * - getForEdit：编辑回填，复用后台 getProjectInfo（canOp(view) 防越权，含全态+权限态 canView/canDownload/canEdit/myMemberRole）。
 * - myLevel：当前用户项目 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。
 * - member：列表/新增/批量新增/编辑/删除（路径 projectId + service 校验操作者权限，不依赖后台 knowhub:project:member 按钮权限）。
 * - file：树/扁平/文件夹/文件节点/编辑/删除/下载（同上，不依赖后台 knowhub:project:add 按钮）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult, NormalizedPageResult } from '@/types/api/common'
import type {
  ProjectAuthoringPayload,
  ProjectAuthoringDetail,
  MyProjectRecord,
  MyProjectListQuery,
  ProjectMemberRecord,
  ProjectFileRecord,
  ProjectFileTreeNode,
  MyProjectPageResult,
} from '@/types/api/knowhub/project-authoring'
import type { ProjectPortalRecord } from '@/types/api/knowhub/project-portal'

// ============================ 项目创作 ============================

/** 当前用户项目 view 等级（0/1/2/3），创作页等级选择器据此禁用不可选等级。 */
export const getMyProjectLevelApi = () =>
  get<ApiResult<number>>('/authoring/project/level')

/**
 * 前台我的项目列表（薄封装 quarryProject，service 内回填 userId 走 author_id 分支，
 * 天然只返回"自己创建/参与的 + 有权看的"）。可选 status/title/type 过滤。
 */
export const getMyProjectsApi = (query: MyProjectListQuery) =>
  getPage<MyProjectRecord>('/authoring/project/list', { params: query }) as Promise<MyProjectPageResult>

/** 编辑回填：复用后台 getProjectInfo（canOp(view) 防越权，返回含全态+权限态的 ProjectVo）。 */
export const getProjectForEditApi = (projectId: number) =>
  get<ApiResult<ProjectAuthoringDetail>>(`/authoring/project/${projectId}`)

/** 前台新建项目草稿（复用 addProjectInfo，创建者默认 LEADER + 分级创作闸）。返回后端 boolean。 */
export const draftProjectApi = (data: ProjectAuthoringPayload) =>
  post<ApiResult<boolean>, ProjectAuthoringPayload>('/authoring/project/draft', data)

/** 前台编辑项目（复用 editProjectInfo，canOp(edit) + 改 level 校验自身 edit 等级）。编辑体需带 projectId。 */
export const editProjectApi = (data: ProjectAuthoringPayload) =>
  put<ApiResult<boolean>, ProjectAuthoringPayload>('/authoring/project', data)

/** 前台发布项目（复用 publishProject，按审核开关 knowhub.project.review_enabled 决定 PUBLISHED 或 PENDING_REVIEW）。 */
export const publishProjectAuthoringApi = (projectId: number) =>
  put<ApiResult<boolean>>(`/authoring/project/${projectId}/publish`)

/** 前台撤回项目（复用 revokeProject → REVOKED，可再编辑/再发布）。仅 PUBLISHED 可撤回。 */
export const revokeProjectAuthoringApi = (projectId: number) =>
  put<ApiResult<boolean>>(`/authoring/project/${projectId}/revoke`)

/** 前台删除项目（软删 project.deleted=1 + 级联软删项目文件，仅作者或 admin；普通用户仅删自己创建的）。 */
export const deleteProjectAuthoringApi = (projectId: number) =>
  del<ApiResult<boolean>>(`/authoring/project/${projectId}`)

// ============================ 成员管理（前台创作者管理自己的项目成员） ============================

/** 前台项目成员列表（薄封装 listMembers，service 内 canOp(view) 校验防越权）。 */
export const listProjectMembersApi = (projectId: number) =>
  get<ApiResult<ProjectMemberRecord[]>>(`/authoring/project/${projectId}/member`)

/** 前台新增项目成员（薄封装 addMember，service 校验操作者 LEADER/canOp）。 */
export const addProjectMemberApi = (projectId: number, data: ProjectMemberRecord) =>
  post<ApiResult<boolean>, ProjectMemberRecord>(`/authoring/project/${projectId}/member`, data)

/** 前台批量新增项目成员（默认 MEMBER；薄封装 addMembersBatch）。 */
export const addProjectMembersBatchApi = (projectId: number, userIds: number[]) =>
  post<ApiResult<boolean>, number[]>(`/authoring/project/${projectId}/member/batch`, userIds)

/**
 * 前台发起项目成员邀请（POST /authoring/project/{projectId}/invite?userId=...，薄封装 inviteMember）。
 * 替代直加成员：负责人选人→PENDING 邀请→受邀人在「我的协作」页同意/拒绝，同意才落 project_member。
 * 后端 service 校验 canOp(edit) + 不邀请自己/已成员 + 去重/重邀幂等，并通知受邀人。
 */
export const inviteProjectMemberApi = (projectId: number, userId: number) =>
  post<ApiResult<boolean>>(`/authoring/project/${projectId}/invite`, undefined, { params: { userId } })

/** 前台编辑项目成员（薄封装 editMember，LEADER 唯一性 + 换负责人同步 author_id）。 */
export const editProjectMemberApi = (data: ProjectMemberRecord) =>
  put<ApiResult<boolean>, ProjectMemberRecord>('/authoring/project/member', data)

/** 前台删除项目成员（LEADER 不可删，需先换负责人；薄封装 deleteMember）。 */
export const deleteProjectMemberApi = (memberId: number) =>
  del<ApiResult<boolean>>(`/authoring/project/member/${memberId}`)

// ============================ 文件树管理（前台创作者管理自己项目文件） ============================

/** 前台项目文件树（树形，薄封装 listFileTree，service canOp(view) 校验）。 */
export const getProjectFileTreeAuthoringApi = (projectId: number) =>
  get<ApiResult<ProjectFileTreeNode[]>>(`/authoring/project/${projectId}/file/tree`)

/** 前台项目文件扁平列表（薄封装 listFiles）。 */
export const getProjectFileListAuthoringApi = (projectId: number) =>
  get<ApiResult<ProjectFileRecord[]>>(`/authoring/project/${projectId}/file/list`)

/** 前台新建文件夹（薄封装 addFolder）。 */
export const addProjectFolderApi = (data: ProjectFileRecord) =>
  post<ApiResult<boolean>, ProjectFileRecord>('/authoring/project/file/folder', data)

/** 前台新增文件节点（关联已上传 file_object，薄封装 addFileNode）。 */
export const addProjectFileNodeApi = (data: ProjectFileRecord) =>
  post<ApiResult<boolean>, ProjectFileRecord>('/authoring/project/file/node', data)

/** 前台编辑文件节点（改名/移动/排序，薄封装 editFileNode）。 */
export const editProjectFileNodeApi = (data: Partial<ProjectFileRecord> & { fileId: number }) =>
  put<ApiResult<boolean>, Partial<ProjectFileRecord> & { fileId: number }>('/authoring/project/file/node', data)

/** 前台删除文件节点（级联软删子节点/file_object，薄封装 deleteFileNode）。 */
export const deleteProjectFileNodeApi = (fileId: number) =>
  del<ApiResult<boolean>>(`/authoring/project/file/${fileId}`)

/** 前台获取文件下载链接（薄封装 downloadFile，canOp(download) 校验）。 */
export const downloadProjectFileAuthoringApi = (fileId: number) =>
  get<ApiResult<string>>(`/authoring/project/file/download/${fileId}`)

// ============================ 互动（收藏，2026-08-11 补齐） ============================

/**
 * 收藏/取消收藏项目（PUT /authoring/project/{id}/collect，collected 缺省 true；主表 collect_count 同步）。
 * 后端 ProjectService.toggleCollect 复用预留的 ProjectMapper.incrCollectCount + project_collect 事实表。
 */
export const toggleProjectCollectApi = (projectId: number, collected = true) =>
  put<ApiResult<boolean>>(`/authoring/project/${projectId}/collect`, null, { params: { collected } })

/**
 * 我的项目收藏列表（GET /authoring/project/collect/list，按收藏时间倒序，仅前台可见口径的已发布项目）。
 * ProjectPortalRecord 口径。后端 listMyCollected 照 article 范式实现（recommendHot 兜底可见性），
 * 实际一次性返回可见收藏全量（pageNum/pageSize 被忽略），前端取 records+total 用即可。
 */
export const listMyCollectedProjectsApi = (pageNum = 1, pageSize = 20) =>
  getPage<ProjectPortalRecord>('/authoring/project/collect/list', { params: { pageNum, pageSize } })

/** 类型再导出，供页面直接用 */
export type {
  ProjectAuthoringPayload,
  ProjectAuthoringDetail,
  MyProjectRecord,
  MyProjectListQuery,
  ProjectMemberRecord,
  ProjectFileRecord,
  ProjectFileTreeNode,
}
export type { NormalizedPageResult }