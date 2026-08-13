/**
 * 文件作用：
 * 承接物品借出管理页面的字段配置与表单默认值，
 * 统一定义借出列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - itemType 走字典（audit_loan_item_type），status 走字典（audit_loan_status）。
 * - assetNo 仅在 itemType=ASSET 时由 computed schema 切 formVisible=true。
 * - borrowDate / expectedReturnDate 为 datetime 类型；expectedReturnDate 可空（无限期）。
 * - subjectName 为非表展示字段，由后端 join 带出。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { LoanRecord } from '@/types/api/knowhub/audit'
import { formatDateTime } from '@/utils/format'

export interface LoanQueryFormState {
  subjectName: string
  itemType: string | undefined
  status: string | undefined
  borrowerName: string
  itemName: string
  pendingReturn: boolean
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成借出查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * pendingReturn 默认 false（不对待归还过滤）。
 */
export const createDefaultLoanQuery = (): LoanQueryFormState => ({
  subjectName: '',
  itemType: undefined,
  status: undefined,
  borrowerName: '',
  itemName: '',
  pendingReturn: false,
  dateRange: [],
})

/**
 * 方法效果：
 * 生成借出弹窗表单的初始状态。quantity 默认 1。
 * itemType 默认 ASSET（资产，含 assetNo）；borrowDate/expectedReturnDate 由后端写或用户选。
 */
export const createDefaultLoanForm = (): LoanRecord => ({
  subjectId: undefined as unknown as number,
  itemName: '',
  itemType: 'ASSET',
  assetNo: '',
  quantity: 1,
  borrowerName: '',
  borrowerPhone: '',
  borrowerOrg: '',
  borrowerRemark: '',
  borrowDate: '',
  expectedReturnDate: '',
  voucherObjectId: undefined,
  note: '',
})

/**
 * 方法效果：
 * 构建借出列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 */
export const createLoanQuerySchema = (): SharedFieldSchemaMap<LoanQueryFormState> => ({
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
  itemType: {
    label: '物品类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'audit_loan_item_type',
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
    dictKey: 'audit_loan_status',
    props: { style: { width: '100%' } },
  },
  borrowerName: {
    label: '借用人',
    inputType: 'text',
    placeholder: '按姓名模糊查',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    props: { style: { width: '100%' } },
  },
  itemName: {
    label: '物品名',
    inputType: 'text',
    placeholder: '请输入物品名',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 4,
    props: { style: { width: '100%' } },
  },
  pendingReturn: {
    label: '仅看待归还',
    inputType: 'switch',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 4,
  },
  dateRange: {
    label: '借出日期',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 7,
    span: 4,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建借出表格列与弹窗表单共用的字段配置。
 */
export const createLoanSchema = (): SharedFieldSchemaMap<LoanRecord> => ({
  loanId: {
    label: '借出编号',
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
  borrowDate: {
    label: '借出日期',
    inputType: 'datetime',
    placeholder: '请选择借出时间',
    tableVisible: true,
    formVisible: true,
    tableOrder: 10,
    formOrder: 6,
    tableWidth: 170,
    span: 12,
    formatter: (value) => formatDateTime(value),
  },
  subjectName: {
    label: '主体',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableMinWidth: 140,
  },
  itemName: {
    label: '物品名',
    inputType: 'text',
    placeholder: '请输入物品名',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableMinWidth: 140,
    span: 12,
  },
  itemType: {
    label: '物品类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 100,
    span: 12,
    dictKey: 'audit_loan_item_type',
  },
  assetNo: {
    label: '资产编号',
    inputType: 'text',
    placeholder: '请输入资产编号（ASSET 时填写）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    tableWidth: 120,
    span: 12,
    // formVisible 默认 true，index.vue 按 itemType=CONSUMABLE 时切 false
  },
  quantity: {
    label: '数量',
    inputType: 'number',
    placeholder: '请输入数量',
    tableVisible: true,
    formVisible: true,
    tableOrder: 6,
    formOrder: 5,
    tableWidth: 80,
    span: 12,
    props: { style: { width: '100%' }, controlsPosition: 'right', min: 1, precision: 0 },
  },
  borrowerName: {
    label: '借用人姓名',
    inputType: 'text',
    placeholder: '请输入借用人姓名（外部人员）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 7,
    formOrder: 7,
    tableWidth: 120,
    span: 12,
    props: { style: { width: '100%' } },
  },
  borrowerPhone: {
    label: '联系电话',
    inputType: 'text',
    placeholder: '请输入借用人联系电话',
    tableVisible: true,
    formVisible: true,
    tableOrder: 8,
    formOrder: 8,
    tableWidth: 140,
    span: 12,
    props: { style: { width: '100%' } },
  },
  borrowerOrg: {
    label: '所属单位',
    inputType: 'text',
    placeholder: '实验室/班级/外单位（选填）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 9,
    formOrder: 9,
    tableWidth: 140,
    span: 12,
    props: { style: { width: '100%' } },
  },
  borrowerRemark: {
    label: '借用人备注',
    inputType: 'text',
    placeholder: '借用人补充备注（选填）',
    tableVisible: false,
    formVisible: true,
    formOrder: 10,
    span: 12,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 13,
    tableWidth: 100,
    dictKey: 'audit_loan_status',
  },
  expectedReturnDate: {
    label: '预计归还',
    inputType: 'datetime',
    placeholder: '请选择预计归还时间（可空=无限期）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 11,
    formOrder: 10,
    tableWidth: 170,
    span: 12,
    formatter: (value) => formatDateTime(value),
  },
  actualReturnDate: {
    label: '实际归还',
    tableVisible: true,
    formVisible: false,
    tableOrder: 12,
    tableWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  voucherObjectId: {
    label: '附件',
    // 由 index.vue 的 #field-voucherObjectId 插槽用 VoucherUploader 接管（弹窗内直传图片/PDF），
    // 不走默认 number 输入，故去掉 inputType；此处只保留布局占位。
    placeholder: '上传图片或PDF',
    tableVisible: false,
    formVisible: true,
    formOrder: 11,
    span: 24,
  },
  note: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注（可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 12,
    span: 24,
    props: {
      rows: 2,
    },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 14,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

/**
 * 方法效果：
 * 构建借出弹窗表单校验规则。subjectId/itemName/itemType/borrowerName/borrowerPhone/borrowDate 必填。
 */
export const buildLoanFormRules = (): FormRules => ({
  subjectId: [{ required: true, message: '请选择主体', trigger: 'change' }],
  itemName: [
    { required: true, message: '请输入物品名', trigger: 'blur' },
    { min: 1, max: 64, message: '物品名长度需在 1 到 64 位之间', trigger: 'blur' },
  ],
  itemType: [{ required: true, message: '请选择物品类型', trigger: 'change' }],
  borrowerName: [
    { required: true, message: '请填写借用人姓名', trigger: 'blur' },
    { min: 1, max: 64, message: '借用人姓名长度需在 1 到 64 位之间', trigger: 'blur' },
  ],
  borrowerPhone: [
    { required: true, message: '请填写借用人联系电话', trigger: 'blur' },
    { max: 32, message: '联系电话长度不超过 32 位', trigger: 'blur' },
  ],
  borrowDate: [{ required: true, message: '请选择借出时间', trigger: 'change' }],
})

/** 借出归还损耗金额展示格式化（继承流水 formatAmount 风格，两位小数） */
export const formatLossAmount = (value: unknown): string =>
  value == null || value === '' ? '--' : Number(value).toFixed(2)