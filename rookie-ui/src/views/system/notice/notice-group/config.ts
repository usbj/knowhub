/**
 * 文件作用：
 * 承接通知分组管理页面的字段配置与表单默认值，
 * 统一定义分组列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - 分组状态为 1 启用 / 0 停用（int），用 select + options 驱动，复用字典页的状态选项写法。
 * - 表格中"成员数量"通过 formatter 读取行内 members 数组长度计算，不对应真实字段。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysNoticeGroupRecord } from '@/types/api/system/notice'
import { formatDateTime } from '@/utils/format'

/**
 * 分组状态选项：启用 / 停用，与后端 sys_notice_group.status（0/1）对齐。
 */
export const noticeGroupStatusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

export interface NoticeGroupQueryFormState {
  groupName: string
  groupCode: string
  status: number | undefined
}

/**
 * 方法效果：
 * 生成分组查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 分组查询表单的默认对象。
 */
export const createDefaultNoticeGroupQuery = (): NoticeGroupQueryFormState => ({
  groupName: '',
  groupCode: '',
  status: undefined,
})

/**
 * 方法效果：
 * 生成分组弹窗表单的初始状态，状态默认启用。
 * 参数：
 * - 无。
 * 返回值：
 * - 分组表单的默认对象。
 */
export const createDefaultNoticeGroupForm = (): SysNoticeGroupRecord => ({
  groupName: '',
  groupCode: '',
  groupDesc: '',
  status: 1,
  members: [],
})

/**
 * 方法效果：
 * 构建分组列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createNoticeGroupQuerySchema = (): SharedFieldSchemaMap<NoticeGroupQueryFormState> => ({
  groupName: {
    label: '分组名称',
    inputType: 'text',
    placeholder: '请输入分组名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 7,
    props: { style: { width: '100%' } },
  },
  groupCode: {
    label: '分组编码',
    inputType: 'text',
    placeholder: '请输入分组编码',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 7,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 5,
    clearable: true,
    props: { style: { width: '100%' } },
    options: noticeGroupStatusOptions,
  },
})

/**
 * 方法效果：
 * 构建分组表格列与弹窗表单共用的字段配置。
 * 参数：
 * - 无。
 * 返回值：
 * - 分组字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createNoticeGroupSchema = (): SharedFieldSchemaMap<SysNoticeGroupRecord> => ({
  groupId: {
    label: '分组编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  groupName: {
    label: '分组名称',
    inputType: 'text',
    placeholder: '请输入分组名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 160,
    span: 12,
  },
  groupCode: {
    label: '分组编码',
    inputType: 'text',
    placeholder: '请输入分组编码',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableMinWidth: 160,
    span: 12,
  },
  groupDesc: {
    label: '分组描述',
    inputType: 'textarea',
    placeholder: '请输入分组描述',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 24,
    tableMinWidth: 200,
    props: {
      rows: 3,
    },
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
    options: noticeGroupStatusOptions,
    formatter: (value) => (Number(value) === 1 ? '启用' : '停用'),
  },
  memberCount: {
    label: '成员数量',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 110,
    // 该字段不对应真实属性，通过 row 读取 members 数组长度计算展示
    formatter: (_value, row) => String(Array.isArray(row?.members) ? row.members.length : 0),
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const noticeGroupFormRules: FormRules = {
  groupName: [
    { required: true, message: '请输入分组名称', trigger: 'blur' },
    { min: 2, max: 100, message: '分组名称长度需在 2 到 100 位之间', trigger: 'blur' },
  ],
  groupCode: [
    { required: true, message: '请输入分组编码', trigger: 'blur' },
    { min: 2, max: 100, message: '分组编码长度需在 2 到 100 位之间', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}
