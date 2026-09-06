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
 * uploadUrl 由后端按访问模式决定：中转模式→/file/proxy-upload/{objectId}（同源带 Token）；
 * 直链模式→预签名绝对 URL（OSS/nginx，不带 Token）。前端按链接形态决定带不带 Token。
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
 * downloadUrl 由后端按访问模式决定：中转模式→/file/proxy/{objectId}（同源带 Token，前端 fetch 取 blob）；
 * 直链模式→带 attachment;filename 的预签名绝对 URL（前端 window.open 跳转）。
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

// ---- 扩展点1：OSS 数据打包下载 ----
// 后端 GET /file/pack-size 返回打包预估总字节数（Result<Long> 裸值，累加 deleted=0 + CONFIRMED 的 content_length），
// 供前端在发起打包下载前做大小预估、超 50G 弹警告确认。
// 后端 POST /file/pack-download-server 返回落盘绝对路径（Result<String> 裸值，SERVER 模式，中转模式强制走此）。

// ---- 扩展点2：OSS 数据迁移 ----
// 后端 POST /file/migration/start 传源类型 + 目标 OSS 连接参数（凭证内存用完即弃、不落库），返 taskId。
// sourceType：'OSS'（源 OSS→目标 OSS，需 source* 参数）/ 'LOCAL'（本地→目标 OSS，source* 留空，源是后端本地磁盘）。
export interface MigrationApplyPayload {
  /** 源端类型：OSS / LOCAL */
  sourceType: string
  // 源 OSS 连接参数（sourceType=OSS 时必填，=LOCAL 时留空）
  sourceEndpoint?: string
  sourceRegion?: string
  sourceAccessKey?: string
  sourceSecretKey?: string
  sourceBucket?: string
  sourcePathStyleAccess?: boolean
  // 目标 OSS 连接参数（始终必填）
  targetEndpoint: string
  targetRegion?: string
  targetAccessKey: string
  targetSecretKey: string
  targetBucket: string
  targetPathStyleAccess?: boolean
}

// 后端 GET /file/migration/progress/{taskId} 返回迁移进度（前端轮询展示进度条 + 状态）。
export interface MigrationProgressRecord {
  taskId: number
  status: string // PENDING / RUNNING / SUCCESS / FAILED / CANCELED
  totalCount: number
  doneCount: number
  failedCount: number
  errorMessage?: string
}
