/**
 * 文件作用：
 * 承接花销主体管理页面的字段配置与表单默认值，
 * 统一定义主体列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - scope 走字典（audit_subject_scope），表格自动渲染为 DictTag，表单/筛选自动渲染为字典下拉。
 * - status 为 ACTIVE/CLOSED 二值（后端枚举，前端 select 硬编码选项）。
 * - handlerId 在表单为 number 必填。
 * - balance/monthExpense 为非表聚合回填字段，列表展示用，不参与表单。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SubjectRecord } from '@/types/api/knowhub/audit'
import { formatDateTime } from '@/utils/format'

/**
 * 金额展示格式化：BigDecimal 前端按 number，空值回落占位文本，否则保留两位小数。
 */
export const formatAmount = (value: unknown): string =>
  value == null || value === '' ? '--' : Number(value).toFixed(2)

/**
 * 主体状态下拉选项（ACTIVE 活跃 / CLOSED 关闭）。
 * 后端枚举固定二值，未入字典故前端硬编码选项。
 */
const SUBJECT_STATUS_OPTIONS = [
  { label: '活跃', value: 'ACTIVE' },
  { label: '关闭', value: 'CLOSED' },
]

export interface SubjectQueryFormState {
  name: string
  scope: string | undefined
  status: string | undefined
  handlerName: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成主体查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 主体查询表单的默认对象。
 */
export const createDefaultSubjectQuery = (): SubjectQueryFormState => ({
  name: '',
  scope: undefined,
  status: undefined,
  handlerName: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 生成主体弹窗表单的初始状态，status 默认 ACTIVE 新建即活跃。
 * 参数：
 * - 无。
 * 返回值：
 * - 主体表单的默认对象。
 */
export const createDefaultSubjectForm = (): SubjectRecord => ({
  name: '',
  scope: 'LAB',
  handlerId: undefined,
  status: 'ACTIVE',
  note: '',
})

/**
 * 方法效果：
 * 构建主体列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createSubjectQuerySchema = (): SharedFieldSchemaMap<SubjectQueryFormState> => ({
  name: {
    label: '主体名',
    inputType: 'text',
    placeholder: '请输入主体名',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  scope: {
    label: '范围',
    inputType: 'select',
    placeholder: '请选择范围',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'audit_subject_scope',
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    options: SUBJECT_STATUS_OPTIONS,
    props: { style: { width: '100%' } },
  },
  handlerName: {
    label: '负责人',
    inputType: 'text',
    placeholder: '按昵称模糊查',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建主体表格列与弹窗表单共用的字段配置。
 * 返回值：
 * - 主体字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createSubjectSchema = (): SharedFieldSchemaMap<SubjectRecord> => ({
  subjectId: {
    label: '主体编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  name: {
    label: '主体名',
    inputType: 'text',
    placeholder: '请输入主体名',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 160,
    span: 12,
  },
  scope: {
    label: '范围',
    inputType: 'select',
    placeholder: '请选择范围',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableWidth: 100,
    span: 12,
    dictKey: 'audit_subject_scope',
  },
  projectId: {
    label: '关联赛事ID',
    inputType: 'number',
    placeholder: 'scope=PROJECT 时填赛事项目ID（可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 12,
    // visibleWhen 由 index.vue 在 computed schema 中按 scope=PROJECT 动态打开，
    // 此处默认 formVisible=false 以避免 scope=LAB 时仍展示。
    props: { style: { width: '100%' }, controlsPosition: 'right', min: 1 },
  },
  handlerId: {
    label: '负责人',
    // 选人由 index.vue 的 #field-handlerId 插槽用 UserPicker 接管（remote 搜昵称），
    // 不走默认 number 输入，故去掉 inputType；此处只保留布局占位。
    placeholder: '搜索昵称选择负责人',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 12,
  },
  handlerNickname: {
    label: '负责人',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableWidth: 120,
  },
  budgetTotal: {
    label: '预算累计',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 110,
    formatter: (value) => formatAmount(value),
  },
  incomeTotal: {
    label: '实到累计',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 110,
    formatter: (value) => formatAmount(value),
  },
  balance: {
    label: '当前结余',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 110,
    formatter: (value) => formatAmount(value),
  },
  monthExpense: {
    label: '当月花销',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 110,
    formatter: (value) => formatAmount(value),
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 9,
    formOrder: 5,
    tableWidth: 90,
    span: 12,
    options: SUBJECT_STATUS_OPTIONS,
  },
  note: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注（可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 24,
    props: {
      rows: 2,
    },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 10,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const subjectFormRules: FormRules = {
  name: [
    { required: true, message: '请输入主体名', trigger: 'blur' },
    { min: 2, max: 64, message: '主体名长度需在 2 到 64 位之间', trigger: 'blur' },
  ],
  scope: [{ required: true, message: '请选择主体范围', trigger: 'change' }],
  handlerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  status: [{ required: true, message: '请选择主体状态', trigger: 'change' }],
}