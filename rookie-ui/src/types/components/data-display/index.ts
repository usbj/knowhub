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
}

/**
 * 每个字段对应一条配置，既能控制表格展示，也能控制表单展示。
 */
export interface SharedFieldSchemaItem<RowData = Record<string, unknown>> {
  label: string
  inputType?: SharedFieldInputType
  placeholder?: string
  tableVisible?: boolean
  formVisible?: boolean
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
export type SharedFieldSchemaMap<RowData = Record<string, unknown>> = Record<
  string,
  SharedFieldSchemaItem<RowData>
>

/**
 * 公共操作按钮配置，同时兼容表格行操作与表单底部操作。
 */
export interface SharedActionConfig<Payload = Record<string, unknown>> {
  key: string
  label: string
  buttonType?: SharedActionButtonType
  plain?: boolean
  text?: boolean
  visible?: boolean | ((payload: Payload) => boolean)
  disabled?: boolean | ((payload: Payload) => boolean)
  onClick?: (payload: Payload) => void | Promise<void>
}
import type { FormItemRule } from 'element-plus'
