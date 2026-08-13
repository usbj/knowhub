/**
 * 文件作用：
 * 承接周期报表管理页的字段配置与查询默认值，统一定义报表列表筛选、表格列的字段元数据。
 * 关键约定：
 * - periodType 走字典（audit_period_type），generateTime 走 daterange。
 * - 报表由定时任务生成，无 add/edit，故 config 不构建弹窗表单 schema / 校验规则。
 * - expenseByCategory 是 JSON 字符串（如 {"耗材":100.00}），前端在详情弹窗里 JSON.parse 展示为分类列表，
 *   不进表格列，保持列干净。
 * - periodEnded 为非表回填字段（后端 Service 回填），前端据此禁当期重算按钮 + tooltip "当前期未结束"。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { ReportListQuery, ReportRecord } from '@/types/api/knowhub/audit'
import { formatDate, formatDateTime } from '@/utils/format'

export interface ReportQueryFormState {
  subjectName: string
  periodType: string | undefined
  periodKey: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成报表查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 */
export const createDefaultReportQuery = (): ReportQueryFormState => ({
  subjectName: '',
  periodType: undefined,
  periodKey: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 构建报表列表筛选区的字段配置，只在筛选区展示，不参与表格。
 */
export const createReportQuerySchema = (): SharedFieldSchemaMap<ReportQueryFormState> => ({
  subjectName: {
    label: '主体名',
    inputType: 'text',
    placeholder: '按名称模糊查',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 4,
    props: { style: { width: '100%' } },
  },
  periodType: {
    label: '周期类型',
    inputType: 'select',
    placeholder: '请选择周期类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'audit_period_type',
    props: { style: { width: '100%' } },
  },
  periodKey: {
    label: '周期键',
    inputType: 'text',
    placeholder: '如 2026-07 / 2026-W32',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '生成时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 金额格式化（保留两位小数），与流水/借出页 formatAmount 风格一致。null/undefined 显示 '--'。
 */
export const formatReportMoney = (value: unknown): string =>
  value == null || value === '' ? '--' : Number(value).toFixed(2)

/**
 * 方法效果：
 * 构建报表表格列字段配置（无弹窗表单 schema，报表无新增/编辑入口）。
 * 列：周期键、周期类型、主体、负责人、周期起止、预算/实到/已花/结余、借出笔数、未归还、生成时间。
 */
export const createReportSchema = (): SharedFieldSchemaMap<ReportRecord> => ({
  reportId: {
    label: '报表编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  periodKey: {
    label: '周期键',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableWidth: 110,
  },
  periodType: {
    label: '周期类型',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableWidth: 100,
    dictKey: 'audit_period_type',
  },
  subjectName: {
    label: '主体',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableMinWidth: 130,
  },
  handlerNickname: {
    label: '负责人',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 120,
  },
  periodStart: {
    label: '周期起',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 120,
    formatter: (value) => formatDate(value),
  },
  periodEnd: {
    label: '周期止',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 120,
    formatter: (value) => formatDate(value),
  },
  budgetAmount: {
    label: '本期预算',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 110,
    formatter: (value) => formatReportMoney(value),
  },
  incomeAmount: {
    label: '本期实到',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 110,
    formatter: (value) => formatReportMoney(value),
  },
  expenseAmount: {
    label: '本期已花',
    tableVisible: true,
    formVisible: false,
    tableOrder: 10,
    tableWidth: 110,
    formatter: (value) => formatReportMoney(value),
  },
  balanceEnd: {
    label: '期末结余',
    tableVisible: true,
    formVisible: false,
    tableOrder: 11,
    tableWidth: 110,
    formatter: (value) => formatReportMoney(value),
  },
  loanOutCount: {
    label: '本期借出',
    tableVisible: true,
    formVisible: false,
    tableOrder: 12,
    tableWidth: 90,
  },
  loanUnreturned: {
    label: '未归还',
    tableVisible: true,
    formVisible: false,
    tableOrder: 13,
    tableWidth: 90,
  },
  generateTime: {
    label: '生成时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 14,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})