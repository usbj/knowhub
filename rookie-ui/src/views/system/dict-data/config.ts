import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysDictDataRecord } from '@/types/api/system/dict'
import { formatDateTime } from '@/utils/format'

export interface DictDataQueryFormState {
  dictId?: number
  dictDataLabel: string
}

export const createDefaultDictDataQuery = (): DictDataQueryFormState => ({
  dictId: undefined,
  dictDataLabel: '',
})

export const createDefaultDictDataForm = (): SysDictDataRecord => ({
  dictId: undefined,
  dictKey: '',
  dictDataLabel: '',
  dictDataValue: '',
  remark: '',
  dictDataSort: '1',
  tagType: 'info',
  tagEffect: 'plain',
  cssClass: '',
  extJson: '',
})

export const createDictDataQuerySchema = (
  dictOptions: Array<{ label: string; value: number }>,
): SharedFieldSchemaMap<DictDataQueryFormState> => ({
  dictId: {
    label: '所属字典',
    inputType: 'select',
    placeholder: '请选择字典',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    options: dictOptions,
    props: { style: { width: '100%' } },
  },
  dictDataLabel: {
    label: '数据标签',
    inputType: 'text',
    placeholder: '请输入数据标签',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 6,
    props: { style: { width: '100%' } },
  },
})

export const createDictDataSchema = (
  dictOptions: Array<{ label: string; value: number }>,
): SharedFieldSchemaMap<SysDictDataRecord> => ({
  dictDataId: {
    label: '数据编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  dictId: {
    label: '所属字典',
    inputType: 'select',
    placeholder: '请选择所属字典',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    span: 12,
    options: dictOptions,
    formatter: (value) => dictOptions.find((item) => item.value === Number(value))?.label ?? '--',
  },
  dictDataLabel: {
    label: '数据标签',
    inputType: 'text',
    placeholder: '请输入数据标签',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
  },
  dictDataValue: {
    label: '数据值',
    inputType: 'text',
    placeholder: '请输入数据值',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
  },
  dictDataSort: {
    label: '排序',
    inputType: 'text',
    placeholder: '请输入排序值',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    span: 12,
  },
  tagType: {
    label: '标签类型',
    inputType: 'select',
    placeholder: '请选择标签类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 6,
    formOrder: 5,
    span: 12,
    options: [
      { label: '默认', value: 'info' },
      { label: '主要', value: 'primary' },
      { label: '成功', value: 'success' },
      { label: '警告', value: 'warning' },
      { label: '危险', value: 'danger' },
    ],
  },
  tagEffect: {
    label: '标签风格',
    inputType: 'select',
    placeholder: '请选择标签风格',
    tableVisible: true,
    formVisible: true,
    tableOrder: 7,
    formOrder: 6,
    span: 12,
    options: [
      { label: '描边', value: 'plain' },
      { label: '浅色', value: 'light' },
      { label: '深色', value: 'dark' },
    ],
  },
  cssClass: {
    label: '样式类名',
    inputType: 'text',
    placeholder: '请输入自定义类名',
    tableVisible: true,
    formVisible: true,
    tableOrder: 8,
    formOrder: 7,
    span: 12,
    tableMinWidth: 160,
  },
  extJson: {
    label: '扩展配置',
    inputType: 'textarea',
    placeholder: '请输入扩展 JSON',
    tableVisible: true,
    formVisible: true,
    tableOrder: 9,
    formOrder: 8,
    span: 12,
    tableMinWidth: 180,
    props: {
      rows: 3,
    },
  },
  remark: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注',
    tableVisible: true,
    formVisible: true,
    tableOrder: 10,
    formOrder: 9,
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
    tableOrder: 11,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})

export const dictDataFormRules: FormRules = {
  dictId: [{ required: true, message: '请选择所属字典', trigger: 'change' }],
  dictDataLabel: [{ required: true, message: '请输入数据标签', trigger: 'blur' }],
  dictDataValue: [{ required: true, message: '请输入数据值', trigger: 'blur' }],
  dictDataSort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
}
