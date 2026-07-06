/**
 * 文件作用：
 * 承接博客标签管理页面的字段配置与表单默认值，
 * 统一定义标签列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - 标签列表**不分页**（后端返回 List<TagVo>），表格直接渲染全量数组，不挂分页条。
 * - status 为 int（0 禁用 / 1 启用），用静态 options 而非字典（与 dict 管理页 status 口径一致）。
 * - sort 为数字排序，表单用 number 输入。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { TagRecord } from '@/types/api/knowhub/tag'
import { formatDateTime } from '@/utils/format'

export interface TagQueryFormState {
  tagName: string
  status: number | undefined
}

/** 标签状态静态选项，0 禁用 / 1 启用，与后端 tag.status 列口径一致 */
export const tagStatusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

/**
 * 方法效果：
 * 生成标签查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 标签查询表单的默认对象。
 */
export const createDefaultTagQuery = (): TagQueryFormState => ({
  tagName: '',
  status: undefined,
})

/**
 * 方法效果：
 * 生成标签弹窗表单的初始状态，枚举字段给后端默认值，避免新增时漏传。
 * 参数：
 * - 无。
 * 返回值：
 * - 标签表单的默认对象。
 */
export const createDefaultTagForm = (): TagRecord => ({
  tagName: '',
  description: '',
  sort: 0,
  status: 1,
})

/**
 * 方法效果：
 * 构建标签列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createTagQuerySchema = (): SharedFieldSchemaMap<TagQueryFormState> => ({
  tagName: {
    label: '标签名',
    inputType: 'text',
    placeholder: '请输入标签名',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    props: { style: { width: '100%' } },
    options: tagStatusOptions,
  },
})

/**
 * 方法效果：
 * 构建标签表格列与弹窗表单共用的字段配置。
 * 参数：
 * - 无。
 * 返回值：
 * - 标签字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createTagSchema = (): SharedFieldSchemaMap<TagRecord> => ({
  tagId: {
    label: '标签编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  tagName: {
    label: '标签名',
    inputType: 'text',
    placeholder: '请输入标签名',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 160,
    span: 12,
  },
  description: {
    label: '说明',
    inputType: 'textarea',
    placeholder: '请输入标签说明（可选）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableMinWidth: 200,
    span: 24,
    props: {
      rows: 3,
    },
  },
  sort: {
    label: '排序',
    inputType: 'number',
    placeholder: '请输入排序值',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 100,
    span: 12,
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    tableWidth: 100,
    span: 12,
    options: tagStatusOptions,
    formatter: (value) => (Number(value) === 1 ? '启用' : '禁用'),
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const tagFormRules: FormRules = {
  tagName: [
    { required: true, message: '请输入标签名', trigger: 'blur' },
    { min: 1, max: 64, message: '标签名长度需在 1 到 64 位之间', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}
