/**
 * 文件作用：
 * 定义公共表单、公共表格共享的字段元数据与操作配置类型，
 * 让页面可以通过统一配置对象声明字段展示、编辑和操作行为。
 */

/**
 * 字段输入类型统一约定在这里，方便公共表单按类型切换控件。
 */
export type SharedFieldInputType =
  | 'text'
  | 'password'
  | 'textarea'
  | 'number'
  | 'select'
  | 'date'
  | 'datetime'
  | 'daterange'
  | 'switch'
  | 'markdown'
  | 'custom'

/**
 * 公共按钮风格与 Element Plus 的主要类型对齐。
 */
export type SharedActionButtonType = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'

/**
 * 选项型控件统一使用该结构描述可选值。
 */
export interface SharedFieldOptionItem {
  label: string
  value: string | number | boolean
  children?: SharedFieldOptionItem[]
}

/**
 * 表格单元格的渲染类型，控制单元格以何种组件形态呈现。
 * - `text`：默认纯文本，走统一格式化逻辑。
 * - `tag`：按标签样式渲染，常用于类型、状态等枚举字段的直观展示。
 */
export type SharedFieldRenderType = 'text' | 'tag'

/**
 * `renderType` 为 `tag` 时使用的标签展示配置。
 * 支持从本字段或跨字段读取标签的文字、颜色、风格与自定义类名，
 * 既能把枚举字段直接渲染成标签，也能让"标签展示"这类预览列
 * 借用本行其他字段（数据标签、标签类型、标签风格、样式类名）组合出真实标签。
 */
export interface SharedFieldTagRenderOptions {
  /**
   * 标签文字的来源字段路径；默认取本字段值。
   * 命中 `options` 时用选项 `label` 作为文字，否则用原始值的字符串形式。
   */
  labelField?: string
  /**
   * 标签颜色（Element Plus 标签类型）的来源字段路径；默认取本字段值，再经 `tagTypeMap` 映射。
   * 来源为空时回落到 `info`，用于无标签类型的下拉选项型数据。
   */
  typeField?: string
  /**
   * 标签风格的来源字段路径；优先级高于 `effect`。
   * 未提供时按 `effect` → 本字段值（若为合法风格）→ `plain` 顺序回落。
   */
  effectField?: string
  /** 标签风格，作为 `effectField` 未命中合法值时的回落值，默认 `plain` 描边。 */
  effect?: 'plain' | 'light' | 'dark'
  /** 自定义类名的来源字段路径，读取后会裁剪空白。 */
  classField?: string
  /** 选项 value 到 Element Plus 标签类型的映射；缺省时以原始值作为标签类型。 */
  tagTypeMap?: Record<string, SharedFieldTagType>
  /** 未命中选项或值为空时的占位文本。 */
  placeholder?: string
}

/**
 * 与 Element Plus 的 ElTag type 对齐，保证标签类型可被静态校验。
 */
export type SharedFieldTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/**
 * 每个字段对应一条配置，既能控制表格展示，也能控制表单展示。
 * 其中：
 * - `dictKey` 表示该字段的数据来源于字典缓存
 * - `dictValueType` 控制下拉值按字符串还是数字回传
 * - `renderType` 控制表格单元格的渲染形态，配合 `tagRender` 进一步描述标签细节
 */
export interface SharedFieldSchemaItem<RowData = Record<string, unknown>> {
  label: string
  inputType?: SharedFieldInputType
  dictKey?: string
  dictValueType?: 'string' | 'number'
  renderType?: SharedFieldRenderType
  tagRender?: SharedFieldTagRenderOptions
  placeholder?: string
  tableVisible?: boolean
  formVisible?: boolean
  /** 动态控制表单字段的显隐，接收当前表单模型，返回 true 表示该字段可见。 */
  visibleWhen?: (model: Record<string, unknown>) => boolean
  tableWidth?: number | string
  tableMinWidth?: number | string
  span?: number
  formOrder?: number
  tableOrder?: number
  disabled?: boolean
  clearable?: boolean
  options?: SharedFieldOptionItem[]
  rules?: Arrayable<FormItemRule>
  props?: Record<string, unknown>
  formatter?: (value: unknown, row: RowData) => string
}

type Arrayable<T> = T | T[]

/**
 * 页面通过字段键值和配置对象的映射，描述一整块业务字段。
 */
export type SharedFieldSchemaMap<RowData = any> = Record<
  string,
  SharedFieldSchemaItem<RowData>
>

/**
 * 公共操作按钮配置，同时兼容表格行操作与表单底部操作。
 */
export interface SharedActionConfig<Payload = Record<string, unknown>> {
  key: string
  label: string
  permKey?: string | string[] | readonly string[]
  buttonType?: SharedActionButtonType
  plain?: boolean
  text?: boolean
  visible?: boolean | ((payload: Payload) => boolean)
  disabled?: boolean | ((payload: Payload) => boolean)
  onClick?: (payload: Payload) => void | Promise<void>
}
import type { FormItemRule } from 'element-plus'
