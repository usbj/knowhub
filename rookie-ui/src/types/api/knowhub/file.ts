/**
 * 文件作用：
 * 定义文件对象管理对接后端所需的接口类型，
 * 与后端 FileObjectVo / FileQuarry / UploadApplyVo / UploadTokenVo / DownloadVo / BindVo 对齐，
 * 供 api 层和页面层统一消费。
 * 关键约定：
 * - 时间字段沿用后端 FileObjectVo 的 String 形式（BeanUtil 复制 Date→String）。
 * - businessType / access / uploadStatus 取值见字典 file_business_type / file_access / upload_status。
 * - 预签名直传：上传令牌返回 uploadUrl，前端 PUT 直传 RustFS，再调 confirm 确认。
 */
import type { NormalizedPageResult, PageQueryParams } from '../system/common'

/**
 * 文件对象元数据记录，与后端 FileObjectVo 字段对齐。
 */
export interface FileObjectRecord {
  objectId?: number
  bucket?: string
  objectKey?: string
  originalName?: string
  contentLength?: number
  contentType?: string
  checksum?: string
  businessType?: string
  bizRefId?: number
  access?: string
  uploadStatus?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/**
 * 文件列表查询参数，与后端 FileQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 */
export interface FileListQuery extends Partial<PageQueryParams> {
  businessType?: string
  uploadStatus?: string
  access?: string
  createBy?: string
  beginTime?: string
  endTime?: string
}

export type FilePageResult = NormalizedPageResult<FileObjectRecord>

/**
 * 上传令牌申请入参，与后端 UploadApplyVo 对齐。
 * businessType 决定 objectKey 前缀/白名单/默认 access；
 * contentType 须落在该业务类型的类型白名单内；size 须 ≤ 该业务类型的体积上限。
 */
export interface UploadApplyPayload {
  businessType: string
  contentType: string
  size: number
  originalName?: string
  access?: string
  bizRefId?: number
}

/**
 * 上传令牌签发结果，与后端 UploadTokenVo 对齐。
 * uploadUrl 为预签名 PUT URL，前端直接 PUT 直传 RustFS；
 * objectId 为元数据行主键，confirm 时回传。
 */
export interface UploadTokenRecord {
  uploadUrl: string
  objectKey?: string
  objectId: number
  expires?: number
}

/**
 * PRIVATE 下载结果，与后端 DownloadVo 对齐。
 * downloadUrl 为带 attachment;filename 的短期 GET 预签名 URL，前端跳转/拉取。
 */
export interface DownloadRecord {
  downloadUrl: string
  expires?: number
  originalName?: string
}

/**
 * 文件业务关联绑定入参，与后端 BindVo 对齐。
 * 业务行创建后回填 file_object.biz_ref_id，便于删业务行时级联清文件。
 */
export interface BindPayload {
  objectId: number
  bizRefId: number
}
