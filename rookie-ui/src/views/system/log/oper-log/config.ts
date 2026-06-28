/**
 * 文件作用：
 * 承接操作日志管理页面的字段配置与查询表单默认值，
 * 统一定义操作日志列表筛选 / 表格列的字段元数据。
 * 关键约定：
 * - businessType / deviceType / status 均走字典系统（dictKey），
 *   表格自动渲染为 DictTag、筛选自动渲染为字典驱动的 select，
 *   标签样式与下拉选项由字典数据项的 tagType / tagEffect 统一控制，不在 config 中硬编码枚举。
 * - 操作日志为只读业务，无新增/编辑表单，故所有字段 formVisible 均为 false。
 * - costTime 以毫秒展示，operTime 走统一时间格式化。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysOperLogListQuery, SysOperLogRecord } from '@/types/api/system/log'
import { formatDateTime } from '@/utils/format'

export interface OperLogQueryFormState {
  title: string
  businessType: string | undefined
  operName: string
  status: number | undefined
  requestMethod: string | undefined
  dateRange: string[]
}

/**
 * 常量：请求方式下拉选项。
 * 操作日志的 requestMethod 来自后端切面采集的 HTTP 方法，取值固定且数量有限，无需进字典系统。
 */
const REQUEST_METHOD_OPTIONS = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
]

/**
 * 方法效果：
 * 生成操作日志查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 操作日志查询表单的默认对象。
 */
export const createDefaultOperLogQuery = (): OperLogQueryFormState => ({
  title: '',
  businessType: undefined,
  operName: '',
  status: undefined,
  requestMethod: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 构建操作日志筛选区的字段配置，只在筛选区展示，不参与表格。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createOperLogQuerySchema = (): SharedFieldSchemaMap<OperLogQueryFormState> => ({
  title: {
    label: '模块标题',
    inputType: 'text',
    placeholder: '请输入模块标题',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 4,
    props: { style: { width: '100%' } },
  },
  businessType: {
    label: '业务类型',
    inputType: 'select',
    placeholder: '请选择业务类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'sys_oper_business_type',
    dictValueType: 'string',
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
  status: {
    label: '操作状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    clearable: true,
    dictKey: 'sys_oper_status',
    // 状态存的是 '0'/'1' 字符串，下拉回传字符串避免类型不一致
    dictValueType: 'string',
    props: { style: { width: '100%' } },
  },
  requestMethod: {
    label: '请求方式',
    inputType: 'select',
    placeholder: '请选择请求方式',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 4,
    clearable: true,
    options: REQUEST_METHOD_OPTIONS,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '操作时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建操作日志表格列的字段配置。
 * 参数：
 * - 无。
 * 返回值：
 * - 操作日志字段配置映射，驱动表格列展示。
 */
export const createOperLogSchema = (): SharedFieldSchemaMap<SysOperLogRecord> => ({
  operId: {
    label: '日志编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  title: {
    label: '模块标题',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableMinWidth: 120,
  },
  businessType: {
    label: '业务类型',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableWidth: 110,
    dictKey: 'sys_oper_business_type',
  },
  requestMethod: {
    label: '请求方式',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableWidth: 100,
  },
  operName: {
    label: '操作人员',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 120,
  },
  deviceType: {
    label: '设备',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    dictKey: 'sys_oper_device_type',
  },
  operIp: {
    label: '操作 IP',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 140,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 90,
    dictKey: 'sys_oper_status',
  },
  costTime: {
    label: '耗时',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 90,
    formatter: (value) => `${value} ms`,
  },
  operTime: {
    label: '操作时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 10,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export { REQUEST_METHOD_OPTIONS }