/**
 * 文件作用：
 * 承接资源管理页面的字段配置与表单默认值，
 * 统一定义资源列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - resourceType / status / reviewStatus 走字典（resource_type / resource_status / review_status），
 *   表格自动渲染为 DictTag，表单/筛选自动渲染为字典驱动的 select。
 * - resourceCategoryId 在筛选区与表单都用 custom 插槽接管（ElTreeSelect 分类树，含"其他"虚拟节点 -1）。
 * - fileObjectId / linkUrl 按 resourceType 切换显示：FILE 用 custom 插槽（文件上传组件），
 *   LINK 用 text 输入 URL。index.vue 用 v-if 按 type 切换插槽。
 * - description 走 markdown 输入（SharedFormPanel 内置 markdown 类型支持）。
 * - 互动计数(likeCount/collectCount/ratingAvg/ratingCount)/作者昵称/审核快照 由后端回填，不参与表单。
 * - publishTime/reviewStatus 由后端写，不参与表单。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { ResourceRecord } from '@/types/api/knowhub/resource'
import { formatDateTime } from '@/utils/format'

/** "其他"分类 ID 约定值（与后端 -1=其他 对齐，前端硬编码） */
export const CATEGORY_OTHER_VALUE = -1

export interface ResourceQueryFormState {
  title: string
  resourceType: string | undefined
  resourceCategoryId: number | undefined
  status: string | undefined
  reviewStatus: string | undefined
  createBy: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成资源查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 资源查询表单的默认对象。
 */
export const createDefaultResourceQuery = (): ResourceQueryFormState => ({
  title: '',
  resourceType: undefined,
  resourceCategoryId: undefined,
  status: undefined,
  reviewStatus: undefined,
  createBy: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 生成资源弹窗表单的初始状态，新建即草稿，status/统计量由后端写不参与表单。
 * FILE 类型默认 fileObjectId 置空（待上传），LINK 类型默认 linkUrl 置空。
 * 参数：
 * - 无。
 * 返回值：
 * - 资源表单的默认对象。
 */
export const createDefaultResourceForm = (): ResourceRecord => ({
  resourceType: 'FILE',
  resourceCategoryId: CATEGORY_OTHER_VALUE,
  title: '',
  summary: '',
  description: '',
  fileObjectId: undefined,
  linkUrl: '',
  linkIcon: '',
})

/**
 * 方法效果：
 * 构建资源列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createResourceQuerySchema = (): SharedFieldSchemaMap<ResourceQueryFormState> => ({
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入标题',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  resourceType: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'resource_type',
    props: { style: { width: '100%' } },
  },
  resourceCategoryId: {
    label: '分类',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 6,
    // 由页面 #field-resourceCategoryId 插槽接管为 ElTreeSelect（分类树 + "其他"虚拟节点）
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    clearable: true,
    dictKey: 'resource_status',
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
  createBy: {
    label: '作者',
    inputType: 'text',
    placeholder: '请输入作者用户名',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 4,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 7,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建资源表格列与弹窗表单共用的字段配置。
 * 返回值：
 * - 资源字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createResourceSchema = (): SharedFieldSchemaMap<ResourceRecord> => ({
  resourceId: {
    label: '资源编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入资源标题',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 180,
    span: 24,
  },
  resourceType: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableWidth: 90,
    span: 12,
    dictKey: 'resource_type',
  },
  resourceCategoryId: {
    label: '分类',
    inputType: 'custom',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 120,
    span: 12,
    // 表格展示用 categoryName（-1=其他时前端硬编码），表单由 #field-resourceCategoryId 插槽接管为 ElTreeSelect
  },
  summary: {
    label: '简介',
    inputType: 'textarea',
    placeholder: '请输入资源简介（可选）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    tableMinWidth: 180,
    span: 24,
    props: {
      rows: 2,
    },
  },
  // FILE 类型：文件上传（custom 插槽，index.vue 按 resourceType=FILE 显示）
  fileObjectId: {
    label: '文件',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 24,
    // 由页面 #field-fileObjectId 插槽接管为文件上传组件（RESOURCE_FILE businessType）
  },
  // LINK 类型：链接 URL（text 输入，仅 LINK 类型显示；index.vue 按 resourceType=LINK 控制 formVisible）
  linkUrl: {
    label: '链接URL',
    inputType: 'text',
    placeholder: '请输入外部链接URL（https://...）',
    tableVisible: false,
    formVisible: false,
    formOrder: 5,
    span: 24,
    // formVisible 默认 false，由 index.vue 按 resourceType=LINK 动态切 true（见 index.vue tableSchema 计算）
  },
  description: {
    label: '详细说明',
    inputType: 'markdown',
    placeholder: '请输入详细说明（支持 Markdown，可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 24,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    dictKey: 'resource_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  downloadCount: {
    label: '下载',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 80,
  },
  likeCount: {
    label: '点赞',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 80,
  },
  collectCount: {
    label: '收藏',
    tableVisible: true,
    formVisible: false,
    tableOrder: 10,
    tableWidth: 80,
  },
  ratingAvg: {
    label: '评分',
    tableVisible: true,
    formVisible: false,
    tableOrder: 11,
    tableWidth: 80,
    formatter: (value) => (value != null ? Number(value).toFixed(1) : '0.0'),
  },
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 12,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  authorNickname: {
    label: '作者',
    tableVisible: true,
    formVisible: false,
    tableOrder: 13,
    tableWidth: 120,
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
 * 按当前资源类型动态构建弹窗表单的校验规则。
 * 数据流转：
 * - 标题/类型始终必填；
 * - 详细说明始终必填（用户要求：必须描述资源内容）；
 * - FILE 类型：fileObjectId 必传（已上传文件才允许提交）；
 * - LINK 类型：linkUrl 必填且需是合法 URL。
 * 参数：
 * - `resourceType`：当前表单资源类型（FILE/LINK）。
 * 返回值：
 * - 与 SharedFormPanel mergedRules 合并的校验规则对象。
 * 注意：fileObjectId/linkUrl 是 custom 插槽字段，SharedFormPanel 对所有字段统一
 *   设 ElFormItem :prop=fieldKey，故此处规则同样能触发校验红字。
 */
export const buildResourceFormRules = (resourceType: string): FormRules => {
  const isFile = resourceType === 'FILE'
  return {
    title: [
      { required: true, message: '请输入资源标题', trigger: 'blur' },
      { min: 2, max: 128, message: '标题长度需在 2 到 128 位之间', trigger: 'blur' },
    ],
    resourceType: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
    description: [
      { required: true, message: '请输入详细说明，描述资源内容', trigger: 'blur' },
      { min: 2, message: '详细说明至少 2 个字符', trigger: 'blur' },
    ],
    // FILE 类型：必须上传文件（fileObjectId 为数字才算已上传）
    fileObjectId: isFile
      ? [
          {
            required: true,
            validator: (_rule, value, callback) => {
              if (value == null || value === 0) {
                callback(new Error('请上传资源文件'))
              } else {
                callback()
              }
            },
            trigger: 'change',
          },
        ]
      : [],
    // LINK 类型：链接 URL 必填且需是合法 http(s) URL
    linkUrl: !isFile
      ? [
          { required: true, message: '请输入链接URL', trigger: 'blur' },
          {
            pattern: /^https?:\/\/.+/,
            message: '链接需以 http:// 或 https:// 开头',
            trigger: 'blur',
          },
        ]
      : [],
  }
}
