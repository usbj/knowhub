/**
 * 文件作用：
 * 承接章节管理页（二级路由页，无菜单）的字段配置与表单默认值，
 * 统一定义章节列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - status / reviewStatus 走字典（chapter_status / review_status），表格自动渲染为 DictTag。
 * - content 走 markdown 输入（章节正文，整页文档语义，列表不带此列）。
 * - 章节不分等级，可见性=文章可见性（articleVisibility join article 带出，列表展示用）。
 * - 权限态 canEdit/canReview 由后端详情接口回填，列表不消费。
 * - publishTime/reviewStatus 由后端写，不参与表单。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { ChapterRecord } from '@/types/api/knowhub/chapter'
import { formatDateTime } from '@/utils/format'

export interface ChapterQueryFormState {
  chapterName: string
  status: string | undefined
  reviewStatus: string | undefined
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成章节查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 返回值：
 * - 章节查询表单的默认对象。
 */
export const createDefaultChapterQuery = (): ChapterQueryFormState => ({
  chapterName: '',
  status: undefined,
  reviewStatus: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 生成章节弹窗表单的初始状态，新建即草稿，status/审核字段由后端写不参与表单。
 * sortOrder 默认 0（同级按此排序）。
 * 返回值：
 * - 章节表单的默认对象。
 */
export const createDefaultChapterForm = (): ChapterRecord => ({
  articleId: 0,
  chapterName: '',
  sortOrder: 0,
  content: '',
})

/**
 * 方法效果：
 * 构建章节列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createChapterQuerySchema = (): SharedFieldSchemaMap<ChapterQueryFormState> => ({
  chapterName: {
    label: '章节名',
    inputType: 'text',
    placeholder: '请输入章节名',
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
    clearable: true,
    dictKey: 'chapter_status',
    props: { style: { width: '100%' } },
  },
  reviewStatus: {
    label: '审核状态',
    inputType: 'select',
    placeholder: '请选择审核状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'review_status',
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建章节表格列与弹窗表单共用的字段配置。
 * 列表不带 content 大字段（避免拖列表），详情接口才带 content。
 * 返回值：
 * - 章节字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createChapterSchema = (): SharedFieldSchemaMap<ChapterRecord> => ({
  chapterId: {
    label: '章节编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  chapterName: {
    label: '章节名',
    inputType: 'text',
    placeholder: '请输入章节名（文档页面标题）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 180,
    span: 24,
  },
  sortOrder: {
    label: '排序',
    inputType: 'text',
    placeholder: '同级排序（数字，asc）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableWidth: 80,
    span: 12,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableWidth: 110,
    dictKey: 'chapter_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  articleVisibility: {
    label: '文章可见性',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 110,
    dictKey: 'article_visibility',
  },
  authorNickname: {
    label: '章节作者',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 120,
  },
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  content: {
    label: '正文',
    inputType: 'markdown',
    placeholder: '请输入章节正文（支持 Markdown，整页文档语义）',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 24,
  },
})

/**
 * 方法效果：
 * 构建章节弹窗表单的校验规则。
 * 数据流转：
 * - 章节名必填（2-128）；
 * - 排序为非负数字（可选，缺省 0）。
 * 返回值：
 * - 与 SharedFormPanel mergedRules 合并的校验规则对象。
 */
export const buildChapterFormRules = (): FormRules => ({
  chapterName: [
    { required: true, message: '请输入章节名', trigger: 'blur' },
    { min: 2, max: 128, message: '章节名长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
})
