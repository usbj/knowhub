/**
 * 文件作用：
 * 承接错误日志管理页面的字段配置与查询表单默认值，
 * 统一定义错误日志列表筛选 / 表格列的字段元数据。
 * 关键约定：
 * - sourceType 走字典系统（dictKey），表格自动渲染 DictTag、筛选自动渲染字典驱动的 select。
 * - 错误日志为只读业务，无新增/编辑表单，故所有字段 formVisible 均为 false。
 * - errorTime 走统一时间格式化。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysErrorLogListQuery, SysErrorLogRecord } from '@/types/api/system/log'
import { formatDateTime } from '@/utils/format'

export interface ErrorLogQueryFormState {
  sourceType: string | undefined
  title: string
  operName: string
  exceptionType: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成错误日志查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 错误日志查询表单的默认对象。
 */
export const createDefaultErrorLogQuery = (): ErrorLogQueryFormState => ({
  sourceType: undefined,
  title: '',
  operName: '',
  exceptionType: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 构建错误日志筛选区的字段配置，只在筛选区展示，不参与表格。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createErrorLogQuerySchema = (): SharedFieldSchemaMap<ErrorLogQueryFormState> => ({
  sourceType: {
    label: '错误来源',
    inputType: 'select',
    placeholder: '请选择错误来源',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 5,
    clearable: true,
    dictKey: 'sys_error_source_type',
    dictValueType: 'string',
    props: { style: { width: '100%' } },
  },
  title: {
    label: '错误简述',
    inputType: 'text',
    placeholder: '请输入错误简述',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
    props: { style: { width: '100%' } },
  },
  operName: {
    label: '操作人员',
    inputType: 'text',
    placeholder: '请输入操作人员',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    props: { style: { width: '100%' } },
  },
  exceptionType: {
    label: '异常类型',
    inputType: 'text',
    placeholder: '请输入异常类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 5,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '错误时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 5,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建错误日志表格列的字段配置。
 * 参数：
 * - 无。
 * 返回值：
 * - 错误日志字段配置映射，驱动表格列展示。
 */
export const createErrorLogSchema = (): SharedFieldSchemaMap<SysErrorLogRecord> => ({
  errorId: {
    label: '日志编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  sourceType: {
    label: '错误来源',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableWidth: 110,
    dictKey: 'sys_error_source_type',
  },
  title: {
    label: '错误简述',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableMinWidth: 200,
  },
  operName: {
    label: '操作人员',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableWidth: 120,
  },
  exceptionType: {
    label: '异常类型',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableMinWidth: 200,
  },
  errorTime: {
    label: '错误时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})