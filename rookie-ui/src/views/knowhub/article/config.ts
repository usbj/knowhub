/**
 * 文件作用：
 * 承接文章管理页面的字段配置与表单默认值，
 * 统一定义文章列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - level / visibility / status / reviewStatus 走字典（article_level / article_visibility / article_status / review_status），
 *   表格自动渲染为 DictTag，表单/筛选自动渲染为字典驱动的 select。
 * - summary 走 textarea（摘要，列表不展示，与正文类似仅详情/编辑可见）；封面 coverObjectKey 走封面 uploader（编辑弹窗内）。
 * - 权限态 canView/canEdit/isAuthor 由后端详情接口回填，列表不消费。
 * - publishTime/reviewStatus 由后端写，不参与表单。
 * - 文章无成员表（轻量权限：系统级 + 作者归属），无 download；编辑复用 add 权限键。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { ArticleRecord } from '@/types/api/knowhub/article'
import { formatDateTime } from '@/utils/format'

export interface ArticleQueryFormState {
  title: string
  level: number | undefined
  visibility: string | undefined
  status: string | undefined
  reviewStatus: string | undefined
  createBy: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成文章查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 返回值：
 * - 文章查询表单的默认对象。
 */
export const createDefaultArticleQuery = (): ArticleQueryFormState => ({
  title: '',
  level: undefined,
  visibility: undefined,
  status: undefined,
  reviewStatus: undefined,
  createBy: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 生成文章弹窗表单的初始状态，新建即草稿，status/审核字段由后端写不参与表单。
 * level 默认 L1 公开；visibility 默认 PRIVATE 未公开（仅作者能写章节，章节提交免审）。
 * 返回值：
 * - 文章表单的默认对象。
 */
export const createDefaultArticleForm = (): ArticleRecord => ({
  title: '',
  level: 1,
  visibility: 'PRIVATE',
  summary: '',
  coverObjectKey: '',
})

/**
 * 方法效果：
 * 构建文章列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createArticleQuerySchema = (): SharedFieldSchemaMap<ArticleQueryFormState> => ({
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入文章标题',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  level: {
    label: '等级',
    inputType: 'select',
    placeholder: '请选择等级',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'article_level',
    props: { style: { width: '100%' } },
  },
  visibility: {
    label: '可见性',
    inputType: 'select',
    placeholder: '请选择可见性',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'article_visibility',
    props: { style: { width: '100%' } },
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
    dictKey: 'article_status',
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
 * 构建文章表格列与弹窗表单共用的字段配置。
 * 返回值：
 * - 文章字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createArticleSchema = (): SharedFieldSchemaMap<ArticleRecord> => ({
  articleId: {
    label: '文章编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  title: {
    label: '文章标题',
    inputType: 'text',
    placeholder: '请输入文章标题',
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
    formOrder: 2,
    tableWidth: 90,
    span: 12,
    dictKey: 'article_level',
  },
  visibility: {
    label: '可见性',
    inputType: 'select',
    placeholder: '请选择可见性',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 110,
    span: 12,
    dictKey: 'article_visibility',
  },
  summary: {
    label: '摘要',
    inputType: 'textarea',
    placeholder: '请输入摘要（可选，文章简介）',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 24,
    props: {
      rows: 3,
    },
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 100,
    dictKey: 'article_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  authorNickname: {
    label: '作者',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 120,
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

/**
 * 方法效果：
 * 构建文章弹窗表单的校验规则。
 * 数据流转：
 * - 文章标题必填（2-128）；
 * - 等级必填（1-3，字典驱动 select 已约束取值）；
 * - 可见性必填（PRIVATE/SEMIPUBLIC/PUBLIC，决定章节提交审不审）。
 * 返回值：
 * - 与 SharedFormPanel mergedRules 合并的校验规则对象。
 */
export const buildArticleFormRules = (): FormRules => ({
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { min: 2, max: 128, message: '标题长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
  level: [{ required: true, message: '请选择文章等级', trigger: 'change' }],
  visibility: [{ required: true, message: '请选择文章可见性', trigger: 'change' }],
})
