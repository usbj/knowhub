/**
 * 文件作用：
 * 定义日志管理模块（操作日志 / 错误日志）与后端对接的接口类型，
 * 与后端 SysOperLogVo / SysErrorLogVo、OperLogQuarry / ErrorLogQuarry 一一对齐。
 * 设计要点：
 * - 操作日志与错误日志均为「只读 + 删除」型业务，不涉及新增编辑表单实体，
 *   因此 Record 即 VO 同构，Query 仅承载分页查询参数。
 * - 操作日志列表行附带 errorLogId，供前端在失败行上点「错误日志」按钮跳转错误日志详情；
 *   错误日志列表行附带 operLogId，供反向跳转操作日志详情。
 */

import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'

/**
 * 操作日志展示对象，对齐后端 SysOperLogVo。
 * businessType / deviceType / status 走字典系统（sys_oper_business_type / sys_oper_device_type / sys_oper_status）渲染标签。
 */
export interface SysOperLogRecord {
  operId: number
  /** 模块标题，如「用户管理」「登录管理」 */
  title: string
  /** 业务类型枚举值（INSERT/UPDATE/DELETE/GRANT/EXPORT/IMPORT/CLEAN/OTHER），字典 sys_oper_business_type */
  businessType: string
  /** 执行方法全限定名 */
  method: string
  /** 请求方式 GET/POST/PUT/DELETE */
  requestMethod: string
  /** 操作人员昵称（未登录操作为空字符串） */
  operName: string
  /** 请求 URL */
  operUrl: string
  /** 操作 IP */
  operIp: string
  /** 操作系统（来源 UA 解析） */
  operOs: string
  /** 浏览器（来源 UA 解析） */
  operBrowser: string
  /** 设备类型枚举值（PC/MOBILE/TABLET/UNKNOWN），字典 sys_oper_device_type */
  deviceType: string
  /** 请求参数 JSON 文本（可能为空） */
  operParam: string
  /** 返回结果 JSON 文本（可能为空） */
  jsonResult: string
  /** 操作状态：0 正常 1 异常，字典 sys_oper_status */
  status: number
  /** 操作时间 */
  operTime: string
  /** 耗时（毫秒） */
  costTime: number
  /** 关联错误日志主键：仅 status=1 且存在错误日志时有值，供前端跳转错误日志详情 */
  errorLogId: number | null
}

/**
 * 错误日志展示对象，对齐后端 SysErrorLogVo。
 * sourceType 走字典系统（sys_error_source_type）渲染标签。
 */
export interface SysErrorLogRecord {
  errorId: number
  /** 错误来源枚举值（REQUEST/SCHEDULED/ASYNC/EVENT/INIT/OTHER），字典 sys_error_source_type */
  sourceType: string
  /** 关联操作日志主键：仅请求来源错误有值，供前端跳转操作日志详情 */
  operLogId: number | null
  /** 错误简述（一般为请求 URL 或任务标识） */
  title: string
  /** 操作人员（未登录或非请求来源时为空） */
  operName: string
  /** 异常类型全限定名 */
  exceptionType: string
  /** 异常消息 */
  exceptionMsg: string
  /** 完整异常堆栈（详情页展示） */
  exceptionStack: string
  /** 错误发生时间 */
  errorTime: string
}

/**
 * 操作日志分页查询参数，对齐后端 OperLogQuarry。
 * beginTime / endTime 与日期范围选择器联动，二选一时为空字符串。
 */
export interface SysOperLogListQuery extends Partial<PageQueryParams> {
  title?: string
  businessType?: string
  operName?: string
  status?: number | undefined
  requestMethod?: string
  beginTime?: string
  endTime?: string
}

/**
 * 错误日志分页查询参数，对齐后端 ErrorLogQuarry。
 */
export interface SysErrorLogListQuery extends Partial<PageQueryParams> {
  sourceType?: string
  title?: string
  operName?: string
  exceptionType?: string
  beginTime?: string
  endTime?: string
}

export type SysOperLogPageResult = NormalizedPageResult<SysOperLogRecord>
export type SysErrorLogPageResult = NormalizedPageResult<SysErrorLogRecord>