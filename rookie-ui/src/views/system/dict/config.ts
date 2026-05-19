import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysDictRecord } from '@/types/api/system/dict'
import { formatDateTime } from '@/utils/format'

export interface DictQueryFormState {
  dictName: string
  dictKey: string
  status: number | undefined
  dateRange: string[]
}

export const dictStatusOptions = [
  { label: '正常', value: 1 },
  { label: '停用', value: 0 },
]

export const createDefaultDictQuery = (): DictQueryFormState => ({
  dictName: '',
  dictKey: '',
  status: undefined,
  dateRange: [],
})

export const createDefaultDictForm = (): SysDictRecord => ({
  dictName: '',
  dictKey: '',
  status: 1,
  remake: '',
})

export const createDictQuerySchema = (): SharedFieldSchemaMap<DictQueryFormState> => ({
  dictName: {
    label: '字典名称',
    inputType: 'text',
    placeholder: '请输入字典名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 5,
    props: { style: { width: '100%' } },
  },
  dictKey: {
    label: '字典键值',
    inputType: 'text',
    placeholder: '请输入字典键值',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
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
    props: { style: { width: '100%' } },
    options: dictStatusOptions,
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 7,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

export const createDictSchema = (): SharedFieldSchemaMap<SysDictRecord> => ({
  dictId: {
    label: '字典编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  dictName: {
    label: '字典名称',
    inputType: 'text',
    placeholder: '请输入字典名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    span: 12,
  },
  dictKey: {
    label: '字典键值',
    inputType: 'text',
    placeholder: '请输入字典键值',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
    options: dictStatusOptions,
    formatter: (value) => (Number(value) === 1 ? '正常' : '停用'),
  },
  remake: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    span: 24,
    tableMinWidth: 180,
    props: {
      rows: 3,
    },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})

export const dictFormRules: FormRules = {
  dictName: [
    { required: true, message: '请输入字典名称', trigger: 'blur' },
    { min: 2, max: 30, message: '字典名称长度需在 2 到 30 位之间', trigger: 'blur' },
  ],
  dictKey: [
    { required: true, message: '请输入字典键值', trigger: 'blur' },
    { min: 2, max: 50, message: '字典键值长度需在 2 到 50 位之间', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}
