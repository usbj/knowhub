/**
 * 文件作用：
 * 系统设置（system_config）模块的前台后端接口，前台版只承接"按 key 取值"。
 * 后台另有分页/详情/增改/删除/刷新缓存（管理端用），前台暂不需要，按需再扩。
 */
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'

/**
 * 按设置键获取当前设置值，供前端按需读取运用。
 * 公共读取接口，仅需登录即可，不返回 valueType 等元信息，避免全量暴露关键设置。
 * 命中返回值字符串，未命中或停用返回 null。
 */
export const getSysConfigValueApi = (configKey: string) =>
  get<ApiResult<string | null>>(`/sys/system-config/configKey/${configKey}`)