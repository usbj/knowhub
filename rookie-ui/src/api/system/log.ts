/**
 * 文件作用：
 * 承接日志管理模块（操作日志 / 错误日志）与后端的接口请求方法，
 * 统一暴露列表分页、详情、批量删除、清空四类只读/删除接口。
 * 对接约定：
 * - Token 头由 http 实例统一注入，业务层不关心鉴权。
 * - 批量删除走路径参数 `/{ids}` 逗号拼接，对齐后端 @PathVariable Long[] 解析。
 * - 分页接口返回归一化后的分页结果（records/total/pageNum/pageSize）。
 */
import { del, get, getPage } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  SysErrorLogListQuery,
  SysErrorLogPageResult,
  SysErrorLogRecord,
  SysOperLogListQuery,
  SysOperLogPageResult,
  SysOperLogRecord,
} from '@/types/api/system/log'

/**
 * 方法效果：
 * 分页查询操作日志列表，失败行附带 errorLogId 供跳转错误日志。
 * 参数：
 * - `params`：操作日志分页查询条件。
 * 返回值：
 * - 归一化后的操作日志分页结果。
 */
export const getSysOperLogPageApi = (params: SysOperLogListQuery) =>
  getPage<SysOperLogRecord>('/sys/operLog/list', { params }) as Promise<SysOperLogPageResult>

/**
 * 方法效果：
 * 获取指定操作日志的详情。
 * 参数：
 * - `operId`：操作日志主键。
 * 返回值：
 * - 操作日志详情结果。
 */
export const getSysOperLogDetailApi = (operId: number) =>
  get<ApiResult<SysOperLogRecord>>(`/sys/operLog/${operId}`)

/**
 * 方法效果：
 * 批量删除操作日志（物理删除，不可恢复）。
 * 参数：
 * - `operIds`：待删除操作日志主键数组。
 * 返回值：
 * - 删除结果。
 */
export const deleteSysOperLogApi = (operIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/operLog/${operIds.join(',')}`)

/**
 * 方法效果：
 * 清空全部操作日志（TRUNCATE，不可恢复）。
 * 参数：
 * - 无。
 * 返回值：
 * - 清空结果。
 */
export const cleanSysOperLogApi = () => del<ApiResult<boolean>>('/sys/operLog/clean')

/**
 * 方法效果：
 * 分页查询错误日志列表，请求来源的错误日志附带 operLogId 供跳转操作日志。
 * 参数：
 * - `params`：错误日志分页查询条件。
 * 返回值：
 * - 归一化后的错误日志分页结果。
 */
export const getSysErrorLogPageApi = (params: SysErrorLogListQuery) =>
  getPage<SysErrorLogRecord>('/sys/errorLog/list', { params }) as Promise<SysErrorLogPageResult>

/**
 * 方法效果：
 * 获取指定错误日志的详情（含完整堆栈）。
 * 参数：
 * - `errorId`：错误日志主键。
 * 返回值：
 * - 错误日志详情结果。
 */
export const getSysErrorLogDetailApi = (errorId: number) =>
  get<ApiResult<SysErrorLogRecord>>(`/sys/errorLog/${errorId}`)

/**
 * 方法效果：
 * 批量删除错误日志（物理删除，不可恢复）。
 * 参数：
 * - `errorIds`：待删除错误日志主键数组。
 * 返回值：
 * - 删除结果。
 */
export const deleteSysErrorLogApi = (errorIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/errorLog/${errorIds.join(',')}`)

/**
 * 方法效果：
 * 清空全部错误日志（TRUNCATE，不可恢复）。
 * 参数：
 * - 无。
 * 返回值：
 * - 清空结果。
 */
export const cleanSysErrorLogApi = () => del<ApiResult<boolean>>('/sys/errorLog/clean')