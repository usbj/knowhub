/**
 * 文件作用：
 * 定义审计模块对接后端所需的接口类型，与后端 AuditXxxVo / AuditXxxQuarry / ReviewVo 对齐，
 * 供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 VO 序列化后的字符串形式（全局 jackson date-format 统一格式化）。
 * - 金额字段（BigDecimal）前端按 number 处理，展示时 toFixed(2)。
 * - status 取值见各业务字典（audit_flow_status / audit_loan_status 等）；reviewStatus 见 review_status；
 *   审核流水 action 见 review_action（复用博客/资源同款）。
 * - 流水/借出审核入参统一 { blogId 承 flowId/loanId, pass, advice }，与后端 ReviewVo 对齐，
 *   博客页面已有 ReviewPayload 同形，本模块各自命名以贴近语义。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 花销主体记录，与后端 AuditSubjectVo 字段对齐。
 * balance/monthExpense 为非表聚合回填字段（Service 算），看板与列表展示用。
 * handlerNickname/projectName 为非表字段（Mapper join sys_user / project 带出）。
 */
export interface SubjectRecord {
  subjectId?: number
  name: string
  /** 主体范围：LAB 实验室 / PROJECT 项目赛事（字典 audit_subject_scope） */
  scope?: string
  /** 关联赛事项目ID（scope=PROJECT 时填，可空） */
  projectId?: number
  /** 预算累计（BUDGET 写入时累加） */
  budgetTotal?: number
  /** 实到累计（INCOME 写入时累加） */
  incomeTotal?: number
  /** 负责人 userId */
  handlerId?: number
  /** 主体状态：ACTIVE 活跃 / CLOSED 关闭 */
  status?: string
  note?: string
  /** 逻辑删除标记（后端 @TableLogic） */
  deleted?: number
  /** 当前结余 = income_total − 历史已通过 EXPENSE 合计（非表聚合回填） */
  balance?: number
  /** 当月已花合计（非表聚合回填） */
  monthExpense?: number
  /** 负责人昵称（非表 join 回填） */
  handlerNickname?: string
  /** 关联项目名（非表，scope=PROJECT 时回填） */
  projectName?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 花销主体列表查询参数，与后端 AuditSubjectQuarry + 分页参数对齐。
 * beginTime / endTime 作用列 audit_subject.create_time；前端 daterange 传 yyyy-MM-dd。
 */
export interface SubjectListQuery extends Partial<PageQueryParams> {
  name?: string
  scope?: string
  status?: string
  handlerName?: string
  beginTime?: string
  endTime?: string
}

export type SubjectPageResult = NormalizedPageResult<SubjectRecord>

/**
 * 资金流水记录，与后端 AuditFundFlowVo 字段对齐。
 * flowType 见字典 audit_flow_type（BUDGET/INCOME/EXPENSE）；
 * status 见字典 audit_flow_status（DRAFT/PENDING/APPROVED/REJECTED/REVOKED）；
 * reviewStatus 见字典 review_status（NONE/PENDING/APPROVED/REJECTED）；
 * category 见字典 audit_expense_category（EXPENSE 花销分类；BUDGET/INCOME 复用此列记来源/用途）。
 */
export interface FundFlowRecord {
  flowId?: number
  subjectId: number
  flowType: string
  amount: number
  occurDate?: string
  category?: string
  handlerId?: number
  /** 票据附件 file_object.object_id（business_type=AUDIT_VOUCHER） */
  voucherObjectId?: number
  note?: string
  status?: string
  reviewStatus?: string
  /** 关联主体名（非表 join 回填） */
  subjectName?: string
  /** 经办人昵称（非表 join 回填） */
  handlerNickname?: string
  /** 花销分类 label（非表，字典翻译） */
  categoryLabel?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 资金流水分页查询参数，与后端 AuditFundFlowQuarry + 分页参数对齐。
 * beginTime / endTime 作用列 audit_fund_flow.occur_date（业务日过滤）。
 */
export interface FundFlowListQuery extends Partial<PageQueryParams> {
  /** 主体ID精确（周期流水明细弹窗按主体拉本期 APPROVED 流水用） */
  subjectId?: number
  /** 主体名模糊（管理台筛选区按主体名搜） */
  subjectName?: string
  flowType?: string
  category?: string
  status?: string
  reviewStatus?: string
  /** 经办人姓名模糊（管理台筛选区按昵称搜） */
  handlerName?: string
  beginTime?: string
  endTime?: string
}

export type FundFlowPageResult = NormalizedPageResult<FundFlowRecord>

/**
 * 资金流水审核入参，与后端 ReviewVo 对齐。
 * blogId 承载 flowId（后端 ReviewVo 复用字段名，本模块透传）。
 * pass=true 通过 → APPROVED；pass=false 驳回 → REJECTED（advice 必填）。
 */
export interface FundFlowReviewPayload {
  blogId: number
  pass: boolean
  advice?: string
}

/**
 * 资金流水审核流水记录，与后端 AuditFlowReviewLogVo 对齐。
 * action 见字典 review_action（SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH）；
 * role 为审核业务身份（AUTHOR作者/REVIEWER审核员/SYSTEM）。
 */
export interface FlowReviewLogRecord {
  reviewLogId?: number
  flowId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/**
 * 借出记录，与后端 AuditLoanVo 字段对齐。
 * itemType 见字典 audit_loan_item_type（ASSET/CONSUMABLE）；
 * status 见字典 audit_loan_status（REQUEST/BORROWED/RETURNED/OVERDUE/REJECTED）。
 * wearLossAmount 仅 return 接口入参用（>0 触发损耗扣费），不入库。
 */
export interface LoanRecord {
  loanId?: number
  subjectId: number
  itemName: string
  itemType: string
  assetNo?: string
  quantity?: number
  /** 借用人姓名（外部人员，非系统用户） */
  borrowerName: string
  /** 借用人联系电话 */
  borrowerPhone: string
  /** 借用人所属单位/部门（实验室/班级/外单位，选填） */
  borrowerOrg?: string
  /** 借用人补充备注（选填） */
  borrowerRemark?: string
  borrowDate?: string
  /** 预计归还时间（空=无限期） */
  expectedReturnDate?: string
  /** 实际归还时间（归还时回填） */
  actualReturnDate?: string
  status?: string
  /** 归还损耗扣费指向 audit_fund_flow.flow_id（无损耗为空） */
  relatedFlowId?: number
  /** 附件 file_object.object_id（借出凭证/损耗票据） */
  voucherObjectId?: number
  note?: string
  /** 归还损耗金额（元，仅 return 入参用） */
  wearLossAmount?: number
  /** 关联主体名（非表 join 回填） */
  subjectName?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 借出分页查询参数，与后端 AuditLoanQuarry + 分页参数对齐。
 * beginTime / endTime 作用列 audit_loan.borrow_date。
 * pendingReturn=true 时仅看待归还（status IN (BORROWED, OVERDUE) 且未归还）。
 */
export interface LoanListQuery extends Partial<PageQueryParams> {
  subjectName?: string
  itemType?: string
  status?: string
  borrowerName?: string
  itemName?: string
  beginTime?: string
  endTime?: string
  pendingReturn?: boolean
}

export type LoanPageResult = NormalizedPageResult<LoanRecord>

/**
 * 借出审核入参，与后端 ReviewVo 对齐（blogId 承载 loanId）。
 */
export interface LoanReviewPayload {
  blogId: number
  pass: boolean
  advice?: string
}

/**
 * 借出审核流水记录，与后端 AuditLoanReviewLogVo 对齐。
 * action 见字典 review_action（借出复用 SUBMIT/APPROVE/REJECT 三值，无 REVOKE）。
 */
export interface LoanReviewLogRecord {
  reviewLogId?: number
  loanId?: number
  action: string
  operatorId?: number
  operator: string
  operatorNickname?: string
  role: string
  advice?: string
  createTime?: string
}

/**
 * 周期报表记录，与后端 AuditPeriodReportVo 字段对齐。
 * periodType 见字典 audit_period_type（MONTH 月度 / WEEK 周记）；
 * periodKey 格式：MONTH "2026-07" / WEEK "2026-W32"。
 * expenseByCategory 为 JSON 字符串（{"耗材":100.00}），详情弹窗解析展示。
 * periodEnded 为非表回填（Service 回填：period_end < now 时 true，前端据此禁当期重算按钮）。
 */
export interface ReportRecord {
  reportId?: number
  subjectId: number
  periodType: string
  periodKey: string
  periodStart?: string
  periodEnd?: string
  budgetAmount?: number
  incomeAmount?: number
  expenseAmount?: number
  expenseByCategory?: string
  balanceEnd?: number
  loanOutCount?: number
  loanUnreturned?: number
  generateTime?: string
  /** 关联主体名（非表 join 回填） */
  subjectName?: string
  /** 负责人昵称（非表 join 回填） */
  handlerNickname?: string
  /** 周期是否已结束（非表回填，前端据此禁当期重算按钮） */
  periodEnded?: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 周期报表分页查询参数，与后端 AuditPeriodReportQuarry + 分页参数对齐。
 * beginTime / endTime 作用列 generate_time。
 */
export interface ReportListQuery extends Partial<PageQueryParams> {
  subjectName?: string
  periodType?: string
  periodKey?: string
  beginTime?: string
  endTime?: string
}

export type ReportPageResult = NormalizedPageResult<ReportRecord>

/**
 * 周期报表重算入参（仅三键定位），与后端 regenerateReport 对齐。
 */
export interface ReportRegeneratePayload {
  subjectId: number
  periodType: string
  periodKey: string
}