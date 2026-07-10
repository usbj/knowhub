import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  SysDictDataListQuery,
  SysDictDataPageResult,
  SysDictDataRecord,
  SysDictListQuery,
  SysDictPageResult,
  SysDictRecord,
} from '@/types/api/system/dict'

export const getSysDictPageApi = (params: SysDictListQuery) =>
  getPage<SysDictRecord>('/sys/dict/list', {
    params,
  }) as Promise<SysDictPageResult>

/**
 * 全量查询启用字典类型，供前端登录后初始化消费。
 * 公共读取接口，仅需登录即可，不加按钮权限，对标系统设置按 key 取值接口的公开读语义。
 * 返回字典类型基础信息（不含数据项），前端拿到 dictKey 后再逐个调 getSysDictDataByTypeApi。
 */
export const getSysDictAllApi = () =>
  get<ApiResult<SysDictRecord[]>>('/sys/dict/all')

export const getSysDictDetailApi = (dictId: number) => get<ApiResult<SysDictRecord>>(`/sys/dict/${dictId}`)

export const createSysDictApi = (data: SysDictRecord) =>
  post<ApiResult<boolean>, SysDictRecord>('/sys/dict', data)

export const updateSysDictApi = (data: SysDictRecord) =>
  put<ApiResult<boolean>, SysDictRecord>('/sys/dict', data)

export const deleteSysDictApi = (dictId: number) =>
  del<ApiResult<boolean>>(`/sys/dict/${dictId}`)

export const getSysDictDataPageApi = (params: SysDictDataListQuery) =>
  getPage<SysDictDataRecord>('/sys/dist/data/list', {
    params,
  }) as Promise<SysDictDataPageResult>

export const getSysDictDataDetailApi = (dictDataId: number) =>
  get<ApiResult<SysDictDataRecord>>(`/sys/dist/data/${dictDataId}`)

export const createSysDictDataApi = (data: SysDictDataRecord) =>
  post<ApiResult<boolean>, SysDictDataRecord>('/sys/dist/data', data)

export const updateSysDictDataApi = (data: SysDictDataRecord) =>
  put<ApiResult<boolean>, SysDictDataRecord>('/sys/dist/data', data)

export const deleteSysDictDataApi = (dictDataId: number) =>
  del<ApiResult<boolean>>(`/sys/dist/data/${dictDataId}`)

export const getSysDictDataByTypeApi = (dictKey: string) =>
  get<ApiResult<SysDictDataRecord[]>>(`/sys/dist/data/type/${dictKey}`)
