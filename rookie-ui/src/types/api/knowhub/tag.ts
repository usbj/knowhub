/**
 * 文件作用：
 * 定义博客受控标签管理对接后端所需的接口类型，
 * 与后端 TagVo 对齐，供 api 层和页面层统一消费。
 * 关键约定：
 * - 标签列表**不分页**，后端返回 List<TagVo>，前端用 ApiResult<TagRecord[]> 消费。
 * - status 为 int（0 禁用 / 1 启用），与字典数据型的数值口径一致，不走字符串字典。
 */
import type { PageQueryParams } from '../system/common'

/**
 * 受控标签记录，与后端 TagVo 字段对齐。
 */
export interface TagRecord {
  tagId?: number
  tagName: string
  description?: string
  sort?: number
  status: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 标签列表查询参数，与后端 TagVo（作为查询入参）对齐。
 * 后端 /tag/list 不分页，pageNum/pageSize 仅占位以兼容 PageQueryParams 约定，实际不传后端。
 */
export interface TagListQuery extends Partial<PageQueryParams> {
  tagName?: string
  status?: number
}

/**
 * 标签新增/编辑入参，与后端 TagVo 对齐。
 * 编辑时 tagId 必填。
 */
export type TagPayload = TagRecord
