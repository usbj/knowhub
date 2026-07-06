/**
 * 文件作用：
 * 承接文件管理页面的字段配置，
 * 统一定义文件列表筛选、表格列的字段元数据。文件对象**无编辑表单**，详情走独立只读弹窗。
 * 关键约定：
 * - businessType / access / uploadStatus 走字典（file_business_type / file_access / upload_status），
 *   表格自动渲染为 DictTag，筛选自动渲染为字典驱动的 select，标签样式由字典数据项统一控制。
 * - contentLength 为字节数，表格用 formatFileSize 友好化展示（KB/MB）。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { FileObjectRecord } from '@/types/api/knowhub/file'
import { formatDateTime } from '@/utils/format'

export interface FileQueryFormState {
  businessType: string | undefined
  uploadStatus: string | undefined
  access: string | undefined
  createBy: string
  dateRange: string[]
}

/**
 * 方法效果：
 * 把字节数友好化为 KB/MB/GB 展示，空值回退占位文本。
 * 参数：
 * - `value`：字节数。
 * 返回值：
 * - 友好化后的体积字符串，或占位文本。
 */
export const formatFileSize = (value: unknown): string => {
  const bytes = Number(value)

  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '--'
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let size = bytes

  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }

  // 小于 1KB 时不保留小数，否则保留一位小数
  return index === 0 ? `${size} ${units[index]}` : `${size.toFixed(1)} ${units[index]}`
}

/**
 * 方法效果：
 * 生成文件查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 文件查询表单的默认对象。
 */
export const createDefaultFileQuery = (): FileQueryFormState => ({
  businessType: undefined,
  uploadStatus: undefined,
  access: undefined,
  createBy: '',
  dateRange: [],
})

/**
 * 方法效果：
 * 构建文件列表筛选区的字段配置，只在筛选区展示，不参与表格。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createFileQuerySchema = (): SharedFieldSchemaMap<FileQueryFormState> => ({
  businessType: {
    label: '业务类型',
    inputType: 'select',
    placeholder: '请选择业务类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 5,
    clearable: true,
    dictKey: 'file_business_type',
    props: { style: { width: '100%' } },
  },
  uploadStatus: {
    label: '上传状态',
    inputType: 'select',
    placeholder: '请选择上传状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'upload_status',
    props: { style: { width: '100%' } },
  },
  access: {
    label: '访问语义',
    inputType: 'select',
    placeholder: '请选择访问语义',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'file_access',
    props: { style: { width: '100%' } },
  },
  createBy: {
    label: '上传人',
    inputType: 'text',
    placeholder: '请输入上传人用户名',
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
    span: 7,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建文件表格列字段配置。文件无编辑表单，schema 仅驱动表格列展示。
 * 参数：
 * - 无。
 * 返回值：
 * - 文件字段配置映射，驱动表格列展示。
 */
export const createFileSchema = (): SharedFieldSchemaMap<FileObjectRecord> => ({
  objectId: {
    label: '对象编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  originalName: {
    label: '文件名',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableMinWidth: 180,
  },
  businessType: {
    label: '业务类型',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableWidth: 140,
    dictKey: 'file_business_type',
  },
  access: {
    label: '访问语义',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableWidth: 100,
    dictKey: 'file_access',
  },
  uploadStatus: {
    label: '上传状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 100,
    dictKey: 'upload_status',
  },
  contentLength: {
    label: '大小',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    formatter: (value) => formatFileSize(value),
  },
  contentType: {
    label: '类型',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableWidth: 140,
  },
  bizRefId: {
    label: '业务关联',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableWidth: 100,
    formatter: (value) => (value === null || value === undefined || value === '' ? '--' : String(value)),
  },
  createBy: {
    label: '上传人',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableWidth: 110,
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
