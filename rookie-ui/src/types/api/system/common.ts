/**
 * 文件作用：
 * 定义系统模块下多个接口都会复用的通用返回结构与分页结构，
 * 供 HTTP 工具和各业务 API 统一消费。
 */

/**
 * 通用后端返回结构。
 * 当前项目后端使用 code、msg、data 三段式响应体。
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
