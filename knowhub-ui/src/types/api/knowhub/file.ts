/**
 * 文件作用：
 * 定义文件上传对接后端所需的接口类型，与后端 UploadApplyVo / UploadTokenVo 对齐，
 * 供 api 层和创作页插图/封面上传统一消费。
 * 关键约定：
 * - 预签名直传：上传令牌返回 uploadUrl，前端 PUT 直传 OSS，再调 confirm 确认。
 * - businessType 取值见后端 FileBusinessType 枚举（BLOG_BODY / BLOG_COVER 等）。
 * - uploadUrl 由后端按访问模式决定：中转模式→/file/proxy-upload/{objectId}（同源带 Token）；
 *   直链模式→预签名绝对 URL（OSS/nginx，不带 Token）。前端按链接形态决定带不带 Token。
 * 照搬后台 rookie-ui 的 types/api/knowhub/file.ts，保留创作页用到的上传两类型 + 项目文件上传下载补回的 DownloadRecord / BindPayload。
 */

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