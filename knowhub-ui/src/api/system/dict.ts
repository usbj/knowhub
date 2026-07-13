/**
 * 文件作用：
 * 字典模块对接后端的接口，前台版只承接读取场景：
 * - 全量查启用字典类型（登录后初始化用）
 * - 按 dictKey 查字典数据项
 * 后台另有字典类型/数据项 CRUD 与分页（管理端用），前台暂不需要，按需再扩。
 * 注意：后端字典数据接口路径拼写为 /sys/dist/data（历史 typo），前端必须与之对齐。
 */
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysDictDataRecord, SysDictRecord } from '@/types/api/dict'

/**
 * 全量查询启用字典类型，供前台登录后初始化消费。
 * 公共读取接口，仅需登录即可，不加按钮权限。
 * 返回字典类型基础信息（不含数据项），前端拿到 dictKey 后再逐个调 getSysDictDataByTypeApi。
 */
export const getSysDictAllApi = () => get<ApiResult<SysDictRecord[]>>('/sys/dict/all')

/**
 * 按 dictKey 查询该字典类型下的全部数据项。
 */
export const getSysDictDataByTypeApi = (dictKey: string) =>
  get<ApiResult<SysDictDataRecord[]>>(`/sys/dist/data/type/${dictKey}`)