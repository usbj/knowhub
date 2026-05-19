import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'

export interface SysDictRecord {
  dictId?: number
  dictName: string
  dictKey: string
  status: number
  remake: string
  createTime?: string
}

export interface SysDictListQuery extends Partial<PageQueryParams> {
  dictName?: string
  dictKey?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

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

export interface SysDictDataListQuery extends Partial<PageQueryParams> {
  dictId?: number
  dictKey?: string
  dictDataLabel?: string
}

export type SysDictPageResult = NormalizedPageResult<SysDictRecord>
export type SysDictDataPageResult = NormalizedPageResult<SysDictDataRecord>
