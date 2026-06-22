import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap, SharedFieldTagType } from '@/types/components/data-display'
import type { SysDictDataRecord } from '@/types/api/system/dict'
import { formatDateTime } from '@/utils/format'

/**
 * 标签类型选项 value 到 Element Plus 标签类型的映射，
 * "标签类型"与"标签风格"两列共用，保证两列颜色口径一致。
 */
const TAG_TYPE_MAP: Record<string, SharedFieldTagType> = {
  info: 'info',
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
}

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
  tagPreview: {
    label: '标签展示',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    // 表格中专用的标签预览列：把本行数据标签按其标签类型、标签风格、样式类名
    // 组合成一个真实标签展示，直观体现该字典数据在业务中渲染出的样式。
    // 文字、颜色、风格、类名均跨字段读取，本字段不对应实际数据属性，
    // 因此仅在表格展示、不参与表单编辑。
    renderType: 'tag',
    tagRender: {
      labelField: 'dictDataLabel',
      typeField: 'tagType',
      effectField: 'tagEffect',
      classField: 'cssClass',
      tagTypeMap: TAG_TYPE_MAP,
      placeholder: '--',
    },
  },
  tagType: {
    label: '标签类型',
    inputType: 'select',
    placeholder: '请选择标签类型',
    // 表格中已由"标签展示"列统一预览，不再单独成列；保留在表单供编辑。
    tableVisible: false,
    formVisible: true,
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
    // 表格中已由"标签展示"列统一预览，不再单独成列；保留在表单供编辑。
    tableVisible: false,
    formVisible: true,
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
    // 表格中已由"标签展示"列通过 classField 应用，不再单独成列；保留在表单供编辑。
    tableVisible: false,
    formVisible: true,
    formOrder: 7,
    span: 12,
  },
  extJson: {
    label: '扩展配置',
    inputType: 'textarea',
    placeholder: '请输入扩展 JSON',
    // 扩展配置属于详情级内容，表格中不再展示；保留在表单供编辑。
    tableVisible: false,
    formVisible: true,
    formOrder: 8,
    span: 12,
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
