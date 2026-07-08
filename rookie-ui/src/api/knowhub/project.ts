/**
 * 文件作用：
 * 集中管理项目管理模块对接后端的接口方法，
 * 包括项目 CRUD、发布/撤回/审核、成员管理、文件树管理、下载、审核历史，
 * 供项目管理页统一调用。
 * 路由前缀 /project（knowhub 命名空间，不套 /sys）。
 * 鉴权由后端 @PreAuthorize('knowhub:project:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 * 等级权限(view/download/edit:lN)由后端 ProjectPermissionResolver 扫 perms 取最高等级判定，
 * 前端不直接处理等级，列表可见性由后端 SQL 过滤、详情按钮显隐由后端回填的 canView/canDownload/canEdit 控制。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  ProjectFileRecord,
  ProjectFileTreeNode,
  ProjectListQuery,
  ProjectMemberRecord,
  ProjectPageResult,
  ProjectRecord,
  ProjectReviewLogRecord,
  ProjectReviewPayload,
} from '@/types/api/knowhub/project'

/**
 * 方法效果：
 * 分页查询项目列表，并在请求层完成分页结果归一化。
 * 列表可见性由后端按权限过滤（level<=userViewLevel OR 参与的项目 can_view=1）。
 * 参数：
 * - `params`：项目查询条件与分页参数。
 * 返回值：
 * - 归一化后的项目分页结果。
 */
export const getProjectPageApi = (params: ProjectListQuery) =>
  getPage<ProjectRecord>('/project/list', {
    params,
  }) as Promise<ProjectPageResult>

/**
 * 方法效果：
 * 根据项目主键获取项目详情（含权限态 canView/canDownload/canEdit/myMemberRole，供前端控制按钮显隐）。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的项目详情对象。
 */
export const getProjectDetailApi = (projectId: number) =>
  get<ApiResult<ProjectRecord>>(`/project/${projectId}`)

/**
 * 方法效果：
 * 新增项目（新建即草稿，status=DRAFT）。创建者默认 LEADER，按 type 配套写子表。
 * 参数：
 * - `data`：项目表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createProjectApi = (data: ProjectRecord) =>
  post<ApiResult<boolean>, ProjectRecord>('/project', data)

/**
 * 方法效果：
 * 编辑项目（PUBLISHED 禁止编辑须先撤回；改 level 需自身编辑等级>=新等级）。
 * 参数：
 * - `data`：项目表单数据，projectId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateProjectApi = (data: ProjectRecord) =>
  put<ApiResult<boolean>, ProjectRecord>('/project', data)

/**
 * 方法效果：
 * 批量删除项目。后端路径变量接收 Long[]，按逗号自动分割；软删。
 * 级联软删 member + project_file + file_object 三类（对象本体由 FileGcTask 异步清）。
 * 仅 LEADER 或拥有 knowhub:project:delete 权限可删。
 * 参数：
 * - `projectIds`：待删除的项目主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteProjectsApi = (projectIds: number[]) =>
  del<ApiResult<boolean>>(`/project/${projectIds.join(',')}`)

/**
 * 方法效果：
 * 发布项目。受全局审核开关 knowhub.project.review_enabled（默认 true 开启）控制：
 * 开关关→直接 PUBLISHED；开关开→PENDING_REVIEW 待审。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishProjectApi = (projectId: number) =>
  put<ApiResult<boolean>>(`/project/publish/${projectId}`)

/**
 * 方法效果：
 * 撤回项目，状态置 REVOKED。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeProjectApi = (projectId: number) =>
  put<ApiResult<boolean>>(`/project/revoke/${projectId}`)

/**
 * 方法效果：
 * 审核项目。pass=true 通过→PUBLISHED；pass=false 驳回→REJECTED（advice 必填）。
 * 负责人不能审核自己项目（回避）。
 * 参数：
 * - `payload`：审核入参（projectId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const reviewProjectApi = (payload: ProjectReviewPayload) =>
  put<ApiResult<boolean>, ProjectReviewPayload>('/project/review', payload)

/**
 * 方法效果：
 * 获取项目审核历史流水（按动作时间升序），供详情弹窗「审核历史」折叠区展示。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表（含操作人昵称 operatorNickname）。
 */
export const getProjectReviewLogApi = (projectId: number) =>
  get<ApiResult<ProjectReviewLogRecord[]>>(`/project/review-log/${projectId}`)

// ---- 成员管理 ----

/**
 * 方法效果：
 * 获取项目成员列表（含昵称，按 LEADER→MENTOR→MEMBER 排序）。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的成员列表。
 */
export const getProjectMembersApi = (projectId: number) =>
  get<ApiResult<ProjectMemberRecord[]>>(`/project/member/${projectId}`)

/**
 * 方法效果：
 * 新增项目成员。LEADER 唯一性：新增 LEADER 时原 LEADER 自动降为 MEMBER 并同步主表 author_id。
 * 参数：
 * - `data`：成员数据（projectId/userId/memberRole 必填，标志位未传按角色给默认）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const addProjectMemberApi = (data: ProjectMemberRecord) =>
  post<ApiResult<boolean>, ProjectMemberRecord>('/project/member', data)

/**
 * 方法效果：
 * 批量新增项目成员（参考通知分组，默认 MEMBER 角色，已存在的跳过）。
 * 单点编辑/权限微调走 updateProjectMemberApi（逐个改角色/标志位）。
 * 参数：
 * - `projectId`：项目主键。
 * - `userIds`：待加入的用户 userId 数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const addProjectMembersBatchApi = (projectId: number, userIds: number[]) =>
  post<ApiResult<boolean>, number[]>(`/project/member/batch/${projectId}`, userIds)

/**
 * 方法效果：
 * 编辑项目成员（调角色/标志位）。提升为 LEADER 时原 LEADER 自动降为 MEMBER。
 * 参数：
 * - `data`：成员数据，memberId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateProjectMemberApi = (data: ProjectMemberRecord) =>
  put<ApiResult<boolean>, ProjectMemberRecord>('/project/member', data)

/**
 * 方法效果：
 * 删除项目成员。LEADER 不可直接删（需先换负责人）。
 * 参数：
 * - `memberId`：成员记录主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteProjectMemberApi = (memberId: number) =>
  del<ApiResult<boolean>>(`/project/member/${memberId}`)

// ---- 文件树管理 ----

/**
 * 方法效果：
 * 获取项目文件树（树形，service 层按 parentId 组装），供 GitHub 式侧边栏递归渲染。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的文件树节点列表（带 children）。
 */
export const getProjectFileTreeApi = (projectId: number) =>
  get<ApiResult<ProjectFileTreeNode[]>>(`/project/file/tree/${projectId}`)

/**
 * 方法效果：
 * 获取项目文件列表（扁平，带 parentId），供前端自行组装树或平铺展示。
 * 参数：
 * - `projectId`：项目主键。
 * 返回值：
 * - 后端 Result 包裹的文件节点列表。
 */
export const getProjectFileListApi = (projectId: number) =>
  get<ApiResult<ProjectFileRecord[]>>(`/project/file/list/${projectId}`)

/**
 * 方法效果：
 * 新建文件夹（is_dir=1，object_id 为空）。
 * 参数：
 * - `data`：文件夹节点数据（projectId/name/parentId/sort）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createProjectFolderApi = (data: ProjectFileRecord) =>
  post<ApiResult<boolean>, ProjectFileRecord>('/project/file/folder', data)

/**
 * 方法效果：
 * 新增文件节点（is_dir=0，关联已上传的 file_object.object_id，后端绑定 biz_ref_id）。
 * 前端先走文件上传流程拿 objectId 再调本接口挂到文件树。
 * 参数：
 * - `data`：文件节点数据（projectId/name/objectId/parentId/sort）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const addProjectFileNodeApi = (data: ProjectFileRecord) =>
  post<ApiResult<boolean>, ProjectFileRecord>('/project/file/node', data)

/**
 * 方法效果：
 * 编辑文件节点（改名/移动/排序）。
 * 参数：
 * - `data`：文件节点数据，fileId 必填，其余字段部分更新（后端动态列，未传不动）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateProjectFileNodeApi = (data: Partial<ProjectFileRecord> & { fileId: number }) =>
  put<ApiResult<boolean>, Partial<ProjectFileRecord> & { fileId: number }>('/project/file/node', data)

/**
 * 方法效果：
 * 删除文件节点。目录级联软删子节点；文件叶子级联软删 file_object（对象本体由 FileGcTask 回收）。
 * 参数：
 * - `fileId`：文件节点主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteProjectFileNodeApi = (fileId: number) =>
  del<ApiResult<boolean>>(`/project/file/${fileId}`)

/**
 * 方法效果：
 * 获取文件下载链接（中转/预签名，后端校验 canOp(download) 权限）。
 * 目录不可下载。
 * 参数：
 * - `fileId`：文件节点主键。
 * 返回值：
 * - 后端 Result 包裹的下载链接字符串。
 */
export const downloadProjectFileApi = (fileId: number) =>
  get<ApiResult<string>>(`/project/file/download/${fileId}`)
