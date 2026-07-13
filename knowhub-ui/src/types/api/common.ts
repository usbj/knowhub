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