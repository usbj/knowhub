/**
 * 文件作用：
 * 字典类型与字典数据项的前台接口类型，与后端 SysDictDto / SysDictDataDto 对齐。
 * 与后台 rookie-ui 的 types/api/system/dict.ts 同源；前台只消费读取场景，
 * 省略列表查询/分页 query 类型（前台无字典管理页）。后续若做管理端再来补。
 */
import type { NormalizedPageResult } from './common'

/** 字典类型记录（消费子集）。 */
export interface SysDictRecord {
  dictId?: number
  dictName: string
  dictKey: string
  status: number
  remake: string
  createTime?: string
}

/**
 * 字典数据项记录，与后端 SysDictDataDto 字段对齐。
 * tagType / tagEffect / cssClass / extJson 供通用标签与下拉渲染复用。
 * dictDataSort 后端为字符串，前台照搬保持兼容。
 */
export interface SysDictDataRecord {
  dictDataId?: number
  dictId?: number
  dictKey?: string
  dictDataLabel: string
  dictDataValue: string
  remark: string
  dictDataSort: string
  tagType?: string
  tagEffect?: string
  cssClass?: string
  extJson?: string
  createTime?: string
}

/** 仅供后续可能的管理页复用：字典数据分页结果别名。 */
export type SysDictDataPageResult = NormalizedPageResult<SysDictDataRecord>