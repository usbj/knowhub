/**
 * 文件作用：
 * 集中管理审计模块对接后端的接口方法，包括花销主体 / 资金流水 / 物品借出 / 周期报表 四组
 * CRUD + 提交审批/通过/驳回/撤回/归还/重算/审核历史，供审计管理页统一调用。
 * 路由前缀 /audit（knowhub 命名空间，不套 /sys，纯后台 admin）。
 * 鉴权由后端 @PreAuthorize('knowhub:audit:*') 控制，前端只需带 Token 头（http.ts 已自动注入）。
 * ReviewVo 复用后端 pojo/common/vo/ReviewVo（blogId 承 flowId/loanId）。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  FlowReviewLogRecord,
  FundFlowListQuery,
  FundFlowPageResult,
  FundFlowRecord,
  FundFlowReviewPayload,
  LoanListQuery,
  LoanPageResult,
  LoanRecord,
  LoanReviewLogRecord,
  LoanReviewPayload,
  ReportListQuery,
  ReportPageResult,
  ReportRecord,
  ReportRegeneratePayload,
  SubjectListQuery,
  SubjectPageResult,
  SubjectRecord,
} from '@/types/api/knowhub/audit'

// ============================ 花销主体 /audit/subject ============================

/**
 * 方法效果：
 * 分页查询花销主体列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：主体查询条件与分页参数。
 * 返回值：
 * - 归一化后的主体分页结果（含 balance/monthExpense 聚合回填）。
 */
export const getAuditSubjectPageApi = (params: SubjectListQuery) =>
  getPage<SubjectRecord>('/audit/subject/list', {
    params,
  }) as Promise<SubjectPageResult>

/**
 * 方法效果：
 * 根据主体主键获取主体详情（含 balance/monthExpense/handlerNickname/projectName 聚合回填）。
 * 参数：
 * - `subjectId`：主体主键。
 * 返回值：
 * - 后端 Result 包裹的主体详情对象。
 */
export const getAuditSubjectDetailApi = (subjectId: number) =>
  get<ApiResult<SubjectRecord>>(`/audit/subject/${subjectId}`)

/**
 * 方法效果：
 * 查主体结余与当月开销（看板数据），接口同 detail 仅语义轻量。
 * 参数：
 * - `subjectId`：主体主键。
 * 返回值：
 * - 后端 Result 包裹的主体详情对象（看板字段）。
 */
export const getSubjectBalanceApi = (subjectId: number) =>
  get<ApiResult<SubjectRecord>>(`/audit/subject/balance/${subjectId}`)

/**
 * 方法效果：
 * 新增花销主体。
 * 参数：
 * - `data`：主体表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createAuditSubjectApi = (data: SubjectRecord) =>
  post<ApiResult<boolean>, SubjectRecord>('/audit/subject', data)

/**
 * 方法效果：
 * 编辑花销主体（budgetTotal/incomeTotal 为累计列，后端置空防覆盖）。
 * 参数：
 * - `data`：主体表单数据，subjectId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateAuditSubjectApi = (data: SubjectRecord) =>
  put<ApiResult<boolean>, SubjectRecord>('/audit/subject', data)

/**
 * 方法效果：
 * 删除花销主体（软删）。
 * 参数：
 * - `subjectId`：主体主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteAuditSubjectApi = (subjectId: number) =>
  del<ApiResult<boolean>>(`/audit/subject/${subjectId}`)

// ============================ 资金流水 /audit/flow ============================

/**
 * 方法效果：
 * 分页查询资金流水列表（三流合一：BUDGET/INCOME/EXPENSE）。
 * 参数：
 * - `params`：流水查询条件与分页参数。
 * 返回值：
 * - 归一化后的流水分页结果。
 */
export const getAuditFlowPageApi = (params: FundFlowListQuery) =>
  getPage<FundFlowRecord>('/audit/flow/list', {
    params,
  }) as Promise<FundFlowPageResult>

/**
 * 方法效果：
 * 根据流水主键获取流水详情。
 * 参数：
 * - `flowId`：流水主键。
 * 返回值：
 * - 后端 Result 包裹的流水详情对象。
 */
export const getAuditFlowDetailApi = (flowId: number) =>
  get<ApiResult<FundFlowRecord>>(`/audit/flow/${flowId}`)

/**
 * 方法效果：
 * 新增资金流水（三流合一入口，按 flowType 分流；EXPENSE 走阈值审批）。
 * 参数：
 * - `data`：流水表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createAuditFlowApi = (data: FundFlowRecord) =>
  post<ApiResult<boolean>, FundFlowRecord>('/audit/flow', data)

/**
 * 方法效果：
 * 编辑资金流水（仅草稿/待审核可编辑）。
 * 参数：
 * - `data`：流水表单数据，flowId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateAuditFlowApi = (data: FundFlowRecord) =>
  put<ApiResult<boolean>, FundFlowRecord>('/audit/flow', data)

/**
 * 方法效果：
 * 删除资金流水（仅草稿/已驳回/已撤回可删）。
 * 参数：
 * - `flowId`：流水主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteAuditFlowApi = (flowId: number) =>
  del<ApiResult<boolean>>(`/audit/flow/${flowId}`)

/**
 * 方法效果：
 * 提交花销审批（草稿→待审核）。
 * 参数：
 * - `flowId`：流水主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const submitAuditFlowApi = (flowId: number) =>
  post<ApiResult<boolean>>(`/audit/flow/submit/${flowId}`)

/**
 * 方法效果：
 * 审核通过花销（待审核→已通过）。
 * 参数：
 * - `payload`：审核入参（blogId 承 flowId / pass / advice）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const approveAuditFlowApi = (payload: FundFlowReviewPayload) =>
  post<ApiResult<boolean>, FundFlowReviewPayload>('/audit/flow/approve', payload)

/**
 * 方法效果：
 * 审核驳回花销（待审核→已驳回，advice 必填）。
 * 参数：
 * - `payload`：审核入参（blogId 承 flowId / pass=false / advice 必填）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const rejectAuditFlowApi = (payload: FundFlowReviewPayload) =>
  post<ApiResult<boolean>, FundFlowReviewPayload>('/audit/flow/reject', payload)

/**
 * 方法效果：
 * 撤回已通过花销（已通过→已撤回）。
 * 参数：
 * - `flowId`：流水主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeAuditFlowApi = (flowId: number) =>
  put<ApiResult<boolean>>(`/audit/flow/revoke/${flowId}`)

/**
 * 方法效果：
 * 获取流水审核历史（按动作时间升序），供详情弹窗审核记录折叠区展示。
 * 参数：
 * - `flowId`：流水主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表。
 */
export const getAuditFlowReviewLogApi = (flowId: number) =>
  get<ApiResult<FlowReviewLogRecord[]>>(`/audit/flow/review-log/${flowId}`)

// ============================ 物品借出 /audit/loan ============================

/**
 * 方法效果：
 * 分页查询物品借出列表。
 * 参数：
 * - `params`：借出查询条件与分页参数。
 * 返回值：
 * - 归一化后的借出分页结果。
 */
export const getAuditLoanPageApi = (params: LoanListQuery) =>
  getPage<LoanRecord>('/audit/loan/list', {
    params,
  }) as Promise<LoanPageResult>

/**
 * 方法效果：
 * 根据借出主键获取借出详情。
 * 参数：
 * - `loanId`：借出主键。
 * 返回值：
 * - 后端 Result 包裹的借出详情对象。
 */
export const getAuditLoanDetailApi = (loanId: number) =>
  get<ApiResult<LoanRecord>>(`/audit/loan/${loanId}`)

/**
 * 方法效果：
 * 新增物品借出（按借出审批开关决定 REQUEST 或 BORROWED）。
 * 参数：
 * - `data`：借出表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createAuditLoanApi = (data: LoanRecord) =>
  post<ApiResult<boolean>, LoanRecord>('/audit/loan', data)

/**
 * 方法效果：
 * 编辑借出（仅申请待审状态可编）。
 * 参数：
 * - `data`：借出表单数据，loanId 必填。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateAuditLoanApi = (data: LoanRecord) =>
  put<ApiResult<boolean>, LoanRecord>('/audit/loan', data)

/**
 * 方法效果：
 * 删除借出（仅申请待审/已驳回可删）。
 * 参数：
 * - `loanId`：借出主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteAuditLoanApi = (loanId: number) =>
  del<ApiResult<boolean>>(`/audit/loan/${loanId}`)

/**
 * 方法效果：
 * 审核通过借出（申请待审→已借出）。
 * 参数：
 * - `payload`：审核入参（blogId 承 loanId / pass=true）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const approveAuditLoanApi = (payload: LoanReviewPayload) =>
  post<ApiResult<boolean>, LoanReviewPayload>('/audit/loan/approve', payload)

/**
 * 方法效果：
 * 审核驳回借出（申请待审→已驳回，advice 必填）。
 * 参数：
 * - `payload`：审核入参（blogId 承 loanId / pass=false / advice 必填）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const rejectAuditLoanApi = (payload: LoanReviewPayload) =>
  post<ApiResult<boolean>, LoanReviewPayload>('/audit/loan/reject', payload)

/**
 * 方法效果：
 * 归还借出（已借出/逾期→已归还；wearLossAmount>0 时事务内插损耗花销流水走阈值审批）。
 * 参数：
 * - `data`：借出入参（loanId 必填，wearLossAmount>0 触发损耗扣费，note 可填）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const returnAuditLoanApi = (data: LoanRecord) =>
  post<ApiResult<boolean>, LoanRecord>('/audit/loan/return', data)

/**
 * 方法效果：
 * 获取借出审核历史（按动作时间升序），供详情弹窗审核记录折叠区展示。
 * 参数：
 * - `loanId`：借出主键。
 * 返回值：
 * - 后端 Result 包裹的审核流水列表。
 */
export const getAuditLoanReviewLogApi = (loanId: number) =>
  get<ApiResult<LoanReviewLogRecord[]>>(`/audit/loan/review-log/${loanId}`)

// ============================ 周期报表 /audit/report ============================

/**
 * 方法效果：
 * 分页查询周期报表列表（含 periodEnded 回填：前端据此禁当期重算按钮）。
 * 参数：
 * - `params`：报表查询条件与分页参数。
 * 返回值：
 * - 归一化后的报表分页结果。
 */
export const getAuditReportPageApi = (params: ReportListQuery) =>
  getPage<ReportRecord>('/audit/report/list', {
    params,
  }) as Promise<ReportPageResult>

/**
 * 方法效果：
 * 根据报表主键获取报表详情。
 * 参数：
 * - `reportId`：报表主键。
 * 返回值：
 * - 后端 Result 包裹的报表详情对象。
 */
export const getAuditReportDetailApi = (reportId: number) =>
  get<ApiResult<ReportRecord>>(`/audit/report/${reportId}`)

/**
 * 方法效果：
 * 重算周期报表（仅已结束期 period_end<now 可重算，当期拒绝）。
 * 参数：
 * - `payload`：重算入参（subjectId / periodType / periodKey 三键定位）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const regenerateAuditReportApi = (payload: ReportRegeneratePayload) =>
  post<ApiResult<boolean>, ReportRegeneratePayload>('/audit/report/regenerate', payload)