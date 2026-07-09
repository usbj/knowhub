/**
 * 文件作用：
 * 承接博客文章管理页面的字段配置与表单默认值，
 * 统一定义博客列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - status / reviewStatus 走字典（blog_status / review_status），表格自动渲染为 DictTag，
 *   表单/筛选自动渲染为字典驱动的 select，标签样式由字典数据项统一控制。
 * - tagIds 在筛选区与表单都用 custom 插槽接管（ElSelect multiple），选项由页面拉取启用标签。
 * - coverUrl 在表单用 custom 插槽接管（封面上传组件，预签名直传后回填 /file/public/{id}）。
 * - coverUrl 在表格用 custom 插槽渲染 <img> 缩略图。
 * - content 走 markdown 输入（MarkdownEditor 已由 SharedFormPanel 内置支持）。
 * - publishTime/reviewStatus/reviewer/reviewTime/reviewAdvice/统计量 由后端写，不参与表单。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { BlogRecord } from '@/types/api/knowhub/blog'
import { formatDateTime } from '@/utils/format'

/** 标签下拉选项（由页面拉取启用标签后传入，供筛选区与表单 tagIds 复用） */
export type TagOption = { label: string; value: number }

export interface BlogQueryFormState {
  title: string
  keyword: string
  tagIds: number[]
  level: number | undefined
  status: string | undefined
  reviewStatus: string | undefined
  createBy: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成博客查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 博客查询表单的默认对象。
 */
export const createDefaultBlogQuery = (): BlogQueryFormState => ({
  title: '',
  keyword: '',
  tagIds: [],
  level: undefined,
  status: undefined,
  reviewStatus: undefined,
  createBy: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 生成博客弹窗表单的初始状态，新建即草稿，status/统计量由后端写不参与表单。
 * level 默认 L1 公开（对标系统 view/edit:lN 权限等级）。
 * 参数：
 * - 无。
 * 返回值：
 * - 博客表单的默认对象。
 */
export const createDefaultBlogForm = (): BlogRecord => ({
  title: '',
  content: '',
  summary: '',
  coverUrl: '',
  tagIds: [],
  level: 1,
})

/**
 * 方法效果：
 * 构建博客列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 参数：
 * - `tagOptions`：启用标签下拉选项，供 tagIds 多选字段复用。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createBlogQuerySchema = (
  tagOptions: TagOption[],
): SharedFieldSchemaMap<BlogQueryFormState> => ({
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
  keyword: {
    label: '全文关键词',
    inputType: 'text',
    placeholder: '命中标题/正文',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
    props: { style: { width: '100%' } },
  },
  tagIds: {
    label: '标签',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 6,
    // 由页面 #field-tagIds 插槽接管为 ElSelect multiple；选项由页面传入
    options: tagOptions,
  },
  level: {
    label: '等级',
    inputType: 'select',
    placeholder: '请选择等级',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    clearable: true,
    dictKey: 'blog_level',
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 4,
    clearable: true,
    dictKey: 'blog_status',
    props: { style: { width: '100%' } },
  },
  reviewStatus: {
    label: '审核状态',
    inputType: 'select',
    placeholder: '请选择审核状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
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
    formOrder: 7,
    span: 4,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 8,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建博客表格列与弹窗表单共用的字段配置。
 * 参数：
 * - `tagOptions`：启用标签下拉选项，供弹窗内 tagIds 多选字段复用。
 * 返回值：
 * - 博客字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createBlogSchema = (
  tagOptions: TagOption[],
): SharedFieldSchemaMap<BlogRecord> => ({
  blogId: {
    label: '博客编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入标题',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 180,
    span: 24,
  },
  level: {
    label: '等级',
    inputType: 'select',
    placeholder: '请选择等级',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 5,
    tableWidth: 90,
    span: 12,
    dictKey: 'blog_level',
  },
  summary: {
    label: '摘要',
    inputType: 'textarea',
    placeholder: '请输入摘要（可选，留空可由正文截取）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 2,
    tableMinWidth: 200,
    span: 24,
    props: {
      rows: 2,
    },
  },
  coverUrl: {
    label: '封面图',
    inputType: 'custom',
    // 表格不展示封面列：SharedTablePanel 单元格不支持 custom 插槽（不修改原组件），
    // 封面只在表单（上传组件）与详情弹窗中展示。
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 24,
    // 表单由页面 #field-coverUrl 插槽接管为封面上传组件（预签名直传后回填 /file/public/{id}）
  },
  tagIds: {
    label: '标签',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 24,
    // 由页面 #field-tagIds 插槽接管为 ElSelect multiple
    options: tagOptions,
  },
  content: {
    // 正文改由「编辑正文」按钮打开全屏编辑器（BlogContentEditor）编辑，
    // 表单内不再内联 markdown 编辑器（正文较长，独立全屏编辑页体验更接近 CSDN）。
    label: '正文',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 24,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 100,
    dictKey: 'blog_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  tagNames: {
    label: '关联标签',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableMinWidth: 140,
    formatter: (value) => (Array.isArray(value) && value.length ? value.join('、') : '--'),
  },
  viewCount: {
    label: '浏览',
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
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 11,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  createBy: {
    label: '作者',
    tableVisible: true,
    formVisible: false,
    tableOrder: 12,
    tableWidth: 120,
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 13,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const blogFormRules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度需在 2 到 200 位之间', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }],
  level: [{ required: true, message: '请选择博客等级', trigger: 'change' }],
}
