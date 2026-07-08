/**
 * 文件作用：
 * 承接项目管理页面的字段配置与表单默认值，
 * 统一定义项目列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - type / status / reviewStatus / level 走字典（project_type / project_status / review_status / project_level），
 *   表格自动渲染为 DictTag，表单/筛选自动渲染为字典驱动的 select。
 * - description 走 markdown 输入（SharedFormPanel 内置 markdown 类型支持）。
 * - 比赛子表字段（competitionName 等）按 type=COMPETITION 动态渲染（前端表单内嵌子表字段区块）。
 * - 权限态 canView/canDownload/canEdit/myMemberRole 由后端详情接口回填，列表不消费。
 * - publishTime/reviewStatus 由后端写，不参与表单。
 * - articleId 关联文章管理模块（待开发，非必填），表单暂不暴露输入，后端预留字段。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { ProjectRecord } from '@/types/api/knowhub/project'
import { formatDateTime } from '@/utils/format'

export interface ProjectQueryFormState {
  title: string
  type: string | undefined
  level: number | undefined
  status: string | undefined
  reviewStatus: string | undefined
  createBy: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成项目查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 返回值：
 * - 项目查询表单的默认对象。
 */
export const createDefaultProjectQuery = (): ProjectQueryFormState => ({
  title: '',
  type: undefined,
  level: undefined,
  status: undefined,
  reviewStatus: undefined,
  createBy: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 生成项目弹窗表单的初始状态，新建即草稿，status/统计量由后端写不参与表单。
 * level 默认 L1 公开。
 * 返回值：
 * - 项目表单的默认对象。
 */
export const createDefaultProjectForm = (): ProjectRecord => ({
  title: '',
  type: 'COMPETITION',
  level: 1,
  summary: '',
  description: '',
})

/**
 * 方法效果：
 * 构建项目列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createProjectQuerySchema = (): SharedFieldSchemaMap<ProjectQueryFormState> => ({
  title: {
    label: '名称',
    inputType: 'text',
    placeholder: '请输入项目名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  type: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'project_type',
    props: { style: { width: '100%' } },
  },
  level: {
    label: '等级',
    inputType: 'select',
    placeholder: '请选择等级',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'project_level',
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
    dictKey: 'project_status',
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
    label: '负责人',
    inputType: 'text',
    placeholder: '请输入负责人用户名',
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
 * 构建项目表格列与弹窗表单共用的字段配置。
 * 返回值：
 * - 项目字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createProjectSchema = (): SharedFieldSchemaMap<ProjectRecord> => ({
  projectId: {
    label: '项目编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  title: {
    label: '项目名称',
    inputType: 'text',
    placeholder: '请输入项目名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 180,
    span: 24,
  },
  type: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableWidth: 110,
    span: 12,
    dictKey: 'project_type',
  },
  level: {
    label: '等级',
    inputType: 'select',
    placeholder: '请选择等级',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 90,
    span: 12,
    dictKey: 'project_level',
  },
  summary: {
    label: '简介',
    inputType: 'textarea',
    placeholder: '请输入项目简介（可选）',
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
  description: {
    label: '详细介绍',
    inputType: 'markdown',
    placeholder: '请输入详细介绍（支持 Markdown，可选）',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 24,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    dictKey: 'project_status',
  },
  reviewStatus: {
    label: '审核状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 100,
    dictKey: 'review_status',
  },
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  authorNickname: {
    label: '负责人',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 120,
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
 * 构建项目弹窗表单的校验规则。
 * 数据流转：
 * - 项目名称必填（2-128）；
 * - 类型必填；
 * - 等级必填（1-3，字典驱动 select 已约束取值）。
 * 参数：
 * - 无（项目表单字段为静态规则，不像资源按类型切换）。
 * 返回值：
 * - 与 SharedFormPanel mergedRules 合并的校验规则对象。
 */
export const buildProjectFormRules = (): FormRules => ({
  title: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 2, max: 128, message: '名称长度需在 2 到 128 位之间', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择项目等级', trigger: 'change' }],
})

/** 比赛子表字段默认值（type=COMPETITION 时表单内嵌子表字段区块用） */
export const createDefaultCompetitionForm = () => ({
  competitionName: '',
  competitionLevel: '',
  awardLevel: '',
  awardTime: '',
  competitionTime: '',
})

/** 比赛子表字段配置（供表单内嵌子表区块渲染，非 SharedFormPanel 字段） */
export interface CompetitionFormState {
  competitionName: string
  competitionLevel: string
  awardLevel: string
  awardTime: string
  competitionTime: string
}
