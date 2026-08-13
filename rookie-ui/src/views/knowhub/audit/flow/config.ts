/**
 * 文件作用：
 * 承接资金流水管理页面的字段配置与表单默认值，
 * 统一定义流水列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - flowType 走字典（audit_flow_type），category 走字典（audit_expense_category），
 *   status 走字典（audit_flow_status），reviewStatus 走字典（review_status）。
 * - occurDate 为业务发生日（仅 date），表单用 date 类型。
 * - subjectName/handlerNickname/categoryLabel 为非表展示字段，由后端 join/字典翻译带出。
 * - amount BigDecimal 前端按 number 处理，展示 toFixed(2)。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { FundFlowRecord } from '@/types/api/knowhub/audit'
import { formatDateTime, formatDate } from '@/utils/format'

/**
 * 金额展示格式化：BigDecimal 前端按 number，空值回落占位文本，否则保留两位小数千分位。
 */
export const formatAmount = (value: unknown): string => {
  if (value == null || value === '') {
    return '--'
  }
  const num = Number(value)
  if (Number.isNaN(num)) {
    return '--'
  }
  return num
    .toFixed(2)
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

export interface FundFlowQueryFormState {
  subjectName: string
  flowType: string | undefined
  category: string | undefined
  status: string | undefined
  reviewStatus: string | undefined
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成流水查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 流水查询表单的默认对象。
 */
export const createDefaultFlowQuery = (): FundFlowQueryFormState => ({
  subjectName: '',
  flowType: undefined,
  category: undefined,
  status: undefined,
  reviewStatus: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 生成流水弹窗表单的初始状态，新建即草稿（DRAFT）。
 * flowType 默认 EXPENSE（花销，最高频）；status/reviewStatus 由后端写不参与表单。
 * 参数：
 * - 无。
 * 返回值：
 * - 流水表单的默认对象。
 */
export const createDefaultFlowForm = (): FundFlowRecord => ({
  subjectId: undefined as unknown as number,
  flowType: 'EXPENSE',
  amount: undefined as unknown as number,
  occurDate: '',
  category: undefined,
  handlerId: undefined,
  voucherObjectId: undefined,
  note: '',
})

/**
 * 方法效果：
 * 构建流水列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createFlowQuerySchema = (): SharedFieldSchemaMap<FundFlowQueryFormState> => ({
  subjectName: {
    label: '主体名',
    inputType: 'text',
    placeholder: '按主体名模糊查',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 4,
    props: { style: { width: '100%' } },
  },
  flowType: {
    label: '流水类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'audit_flow_type',
    props: { style: { width: '100%' } },
  },
  category: {
    label: '分类',
    inputType: 'select',
    placeholder: '请选择分类',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'audit_expense_category',
    props: { style: { width: '100%' } },
  },
  status: {
    label: '流水状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    clearable: true,
    dictKey: 'audit_flow_status',
    props: { style: { width: '100%' } },
  },
  reviewStatus: {
    label: '审核状态',
    inputType: 'select',
    placeholder: '请选择审核状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 4,
    clearable: true,
    dictKey: 'review_status',
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '发生日期',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 4,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建流水表格列与弹窗表单共用的字段配置。
 * 返回值：
 * - 流水字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createFlowSchema = (): SharedFieldSchemaMap<FundFlowRecord> => ({
  flowId: {
    label: '流水编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  subjectId: {
    label: '主体',
    // 由 index.vue 的 #field-subjectId 插槽用 SubjectPicker 接管（remote 搜主体名），
    // 不走默认输入，故去掉 inputType；此处只保留布局占位。
    placeholder: '搜索主体名选择',
    tableVisible: false,
    formVisible: true,
    formOrder: 0,
    span: 24,
  },
  occurDate: {
    label: '发生日期',
    inputType: 'date',
    placeholder: '请选择发生日期',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 3,
    tableWidth: 120,
    span: 12,
    formatter: (value) => formatDate(value),
  },
  subjectName: {
    label: '主体',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableMinWidth: 140,
  },
  flowType: {
    label: '流水类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 1,
    tableWidth: 100,
    span: 12,
    dictKey: 'audit_flow_type',
  },
  category: {
    label: '分类',
    inputType: 'select',
    placeholder: '请选择分类',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    tableWidth: 110,
    span: 12,
    dictKey: 'audit_expense_category',
  },
  amount: {
    label: '金额',
    inputType: 'number',
    placeholder: '请输入金额（元）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 6,
    formOrder: 2,
    tableWidth: 120,
    span: 12,
    formatter: (value) => formatAmount(value),
    props: { style: { width: '100%' }, controlsPosition: 'right', min: 0, precision: 2 },
  },
  handlerNickname: {
    label: '经办人',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 120,
  },
  handlerId: {
    label: '经办人',
    // 选人由 index.vue 的 #field-handlerId 插槽用 UserPicker 接管（remote 搜昵称），
    // 不走默认 number 输入，故去掉 inputType；此处只保留布局占位。
    placeholder: '搜索昵称选择经办人',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 12,
  },
  voucherObjectId: {
    label: '票据凭证',
    // 由 index.vue 的 #field-voucherObjectId 插槽用 VoucherUploader 接管（弹窗内直传图片/PDF），
    // 不走默认 number 输入，故去掉 inputType；此处只保留布局占位。
    placeholder: '上传图片或PDF',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 12,
  },
  status: {
    label: '流水状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 100,
    dictKey: 'audit_flow_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  note: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注（可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 7,
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

/**
 * 方法效果：
 * 按当前流水类型动态构建弹窗表单的校验规则。
 * - subjectId/flowType/amount/occurDate/handlerId 始终必填。
 * - amount 必须大于 0。
 * 参数：
 * - 无（必填字段不依赖 flowType）。
 * 返回值：
 * - 与 SharedFormPanel mergedRules 合并的校验规则对象。
 */
export const buildFlowFormRules = (): FormRules => ({
  subjectId: [{ required: true, message: '请选择主体', trigger: 'change' }],
  flowType: [{ required: true, message: '请选择流水类型', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number(value) <= 0) {
          callback(new Error('金额必须大于 0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  handlerId: [{ required: true, message: '请选择经办人', trigger: 'change' }],
})