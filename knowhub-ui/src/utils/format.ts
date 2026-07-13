/**
 * 文件作用：
 * 提供前台展示用的时间格式化工具，与后台 rookie-ui/src/utils/format.ts 的
 * formatDateTime / formatDate 行为对齐（照搬核心实现）。
 * 后端 Date 字段经 jackson 序列化输出 `yyyy-MM-dd HH:mm:ss`，前端展示统一走这里。
 */

const DEFAULT_FALLBACK = '--'

const isEmptyDisplayValue = (value: unknown): boolean =>
  value === null || value === undefined || value === ''

/**
 * 将日期值格式化为本地时间字符串，并按数据完整度智能截断：
 * - 时分秒全为 0（即该字段只记录到日）时，只返回 `YYYY-MM-DD`；
 * - 否则返回完整的 `YYYY-MM-DD HH:mm:ss`。
 */
export const formatDateTime = (value: unknown, fallback = DEFAULT_FALLBACK): string => {
  if (isEmptyDisplayValue(value)) {
    return fallback
  }

  const normalizedValue =
    typeof value === 'string'
      ? value.trim().replace('T', ' ').replace(/\.\d+(?=(Z|[+-]\d{2}:\d{2})?$)/, '')
      : value

  const date = normalizedValue instanceof Date ? normalizedValue : new Date(String(normalizedValue))

  if (Number.isNaN(date.getTime())) {
    return fallback
  }

  const pad = (part: number) => String(part).padStart(2, '0')
  const datePart = [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join('-')

  if (date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0) {
    return datePart
  }

  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 将日期值强制格式化为只含年月日的 `YYYY-MM-DD` 字符串。
 */
export const formatDate = (value: unknown, fallback = DEFAULT_FALLBACK): string => {
  if (isEmptyDisplayValue(value)) {
    return fallback
  }

  const normalizedValue =
    typeof value === 'string'
      ? value.trim().replace('T', ' ').replace(/\.\d+(?=(Z|[+-]\d{2}:\d{2})?$)/, '')
      : value

  const date = normalizedValue instanceof Date ? normalizedValue : new Date(String(normalizedValue))

  if (Number.isNaN(date.getTime())) {
    return fallback
  }

  const pad = (part: number) => String(part).padStart(2, '0')

  return [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join('-')
}