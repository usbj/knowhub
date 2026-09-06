/**
 * 文件作用：
 * 系统设置（system_config）前台接口类型，与后端 SysConfigVo 对齐。
 * 与后台 rookie-ui/types/api/system/system-config.ts 同源；前台只消费"按 key 取值"场景，
 * 列表/详情/增改/刷新缓存等管理接口前台暂不需要。
 */
import type { NormalizedPageResult } from './common'

/**
 * 系统设置记录，对齐后端 SysConfigVo。
 * - valueType：值类型 STRING / BOOLEAN / NUMBER / JSON，前台如需翻译走字典 sys_config_value_type。
 * - isSystem：是否系统内置项（1是 0否），内置项前端禁用删除、禁用改键与类型。
 */
export interface SysConfigRecord {
  configId?: number
  configKey: string
  configName: string
  configValue: string
  valueType: string
  isSystem?: number
  remark?: string
  status: number
  createTime?: string
  updateTime?: string
}

/** 仅供后续可能的管理页复用：系统设置分页结果别名。 */
export type SysConfigPageResult = NormalizedPageResult<SysConfigRecord>