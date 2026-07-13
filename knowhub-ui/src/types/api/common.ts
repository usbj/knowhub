/**
 * 文件作用：
 * 定义与后端统一的通用返回结构，供 HTTP 工具与各业务 API 类型消费。
 * 与 rookie-ui 后台对齐（code/msg/data 三段式）。
 */

/**
 * 通用后端返回结构。
 * 后端 Result<T> 序列化后的形态。
 */
export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

/**
 * 与后端 PageUtil / PageInfo 对齐的原始分页参数。
 */
export interface PageQueryParams {
  pageNum: number
  pageSize: number
}

/**
 * 与 PageHelper 返回的 PageInfo 结构对齐的原始分页数据。
 */
export interface RawPageInfoResult<T> {
  list: T[]
  pageNum: number
  pageSize: number
  pages: number
  total: number
}

/**
 * 前端归一化后的分页结果。
 * 页面层统一读取 records / total / pageNum / pageSize 即可，不再关心后端原始命名。
 */
export interface NormalizedPageResult<T> {
  records: T[]
  pageNum: number
  pageSize: number
  pages: number
  total: number
}