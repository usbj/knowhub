/**
 * 文件作用：
 * 封装文件预签名直传流程（申请令牌 → PUT 直传 RustFS → 确认），
 * 供博客封面上传、文件管理页上传入口等场景复用。
 * 关键约定：
 * - 直传用原生 XMLHttpRequest（支持 upload.onprogress 进度回调），
 *   带 Content-Type / Content-Length 头，**不带 Token 头**——RustFS 预签名自带鉴权，
 *   带 Token 反而可能被预签名条件校验拒绝。
 * - PUBLIC 对象上传成功后回填 /file/public/{objectId} 供 <img> 直引；
 *   PRIVATE 对象只返回 objectId，取用走 /file/download/{id} 鉴权预签名。
 * - 这是 knowhub 二开新增工具，不修改任何既有 utils 文件。
 */
import { applyUploadTokenApi, confirmUploadApi, buildFilePublicUrl } from '@/api/knowhub/file'
import type { UploadApplyPayload } from '@/types/api/knowhub/file'

/** 上传进度回调参数，取值 0–100。 */
export type UploadProgressHandler = (percent: number) => void

/** 预签名直传选项。 */
export interface PresignedUploadOptions {
  /** 待上传文件 */
  file: File
  /** 业务类型（FileBusinessType 枚举 code，决定 objectKey 前缀/白名单/默认 access） */
  businessType: string
  /** 访问语义；缺省按 businessType 默认值（BLOG_*→PUBLIC，其余→PRIVATE） */
  access?: string
  /** 业务关联 ID；可空，上传时业务行可能还没建，确认/绑定后回填 */
  bizRefId?: number
  /** 上传进度回调，取值 0–100 */
  onProgress?: UploadProgressHandler
}

/** 预签名直传结果。 */
export interface PresignedUploadResult {
  /** 文件对象元数据行主键，confirm 时回传后端 */
  objectId: number
  /** PUBLIC 对象的回显相对路径；PRIVATE 对象为 undefined，取用走下载接口 */
  publicUrl?: string
  /** 对象 key，仅供调试展示 */
  objectKey?: string
}

/**
 * 方法效果：
 * 用原生 XMLHttpRequest 执行 PUT 直传到预签名 URL，支持进度回调。
 * 数据流转：
 * - 直接 PUT 文件二进制到 uploadUrl，带 Content-Type（须与申请令牌时一致，否则预签名校验失败）
 *   和 Content-Length 头；不带 Token（RustFS 预签名自带鉴权）。
 * - 通过 upload.onprogress 计算已上传百分比回传 onProgress。
 * - 非 2xx 状态码抛错，错误信息包含 RustFS 返回体便于排查。
 * 参数：
 * - `uploadUrl`：后端签发的 PutObject 预签名 URL。
 * - `file`：待上传文件。
 * - `contentType`：与申请令牌时一致的 MIME 类型。
 * - `onProgress`：可选进度回调。
 * 返回值：
 * - Promise<void>，resolve 即直传完成。
 */
const putToPresignedUrl = (
  uploadUrl: string,
  file: File,
  contentType: string,
  onProgress?: UploadProgressHandler,
): Promise<void> =>
  new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl, true)
    // Content-Type 必须与申请令牌时一致，否则预签名条件校验失败
    xhr.setRequestHeader('Content-Type', contentType)

    if (onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
      } else {
        reject(new Error(`直传失败：HTTP ${xhr.status} ${xhr.statusText}`))
      }
    }

    xhr.onerror = () => reject(new Error('直传失败：网络异常'))
    xhr.send(file)
  })

/**
 * 方法效果：
 * 执行完整的预签名直传三步流程：申请令牌 → PUT 直传 RustFS → 确认。
 * 数据流转：
 * - 1. 用 file 的 type/size + businessType/access/bizRefId 调 applyUploadTokenApi 拿 uploadUrl/objectId。
 * - 2. 用 putToPresignedUrl 把文件直传到 uploadUrl。
 * - 3. 调 confirmUploadApi 让后端 HeadObject 核对真实值并置 CONFIRMED。
 * - 4. access=PUBLIC 时回填 publicUrl(/file/public/{objectId})供 <img> 直引。
 * 参数：
 * - `options`：预签名直传选项（file / businessType / access / bizRefId / onProgress）。
 * 返回值：
 * - 上传结果（objectId / publicUrl / objectKey）。
 * 副作用：
 * - 任意一步失败即抛错；直传成功但 confirm 失败时，对象已在 RustFS 但元数据状态异常，
 *   由后端 FileGcTask 定时 GC 清理超时 PENDING，前端仅需提示用户重试。
 */
export const presignedUploadFlow = async (
  options: PresignedUploadOptions,
): Promise<PresignedUploadResult> => {
  const { file, businessType, access, bizRefId, onProgress } = options

  // 1. 申请上传令牌：声明 contentType/size，后端校验落白名单后签发预签名 URL
  const applyPayload: UploadApplyPayload = {
    businessType,
    contentType: file.type,
    size: file.size,
    originalName: file.name,
  }
  if (access) {
    applyPayload.access = access
  }
  if (bizRefId !== undefined) {
    applyPayload.bizRefId = bizRefId
  }

  const tokenResult = await applyUploadTokenApi(applyPayload)
  const token = tokenResult.data

  // 2. PUT 直传 RustFS：用原生 XHR 支持进度回调，不带 Token（预签名自带鉴权）
  await putToPresignedUrl(token.uploadUrl, file, file.type, onProgress)

  // 3. 上传确认：后端 HeadObject 核对真实值并置 CONFIRMED
  await confirmUploadApi(token.objectId, bizRefId)

  // 4. PUBLIC 对象回填回显相对路径供 <img> 直引
  return {
    objectId: token.objectId,
    objectKey: token.objectKey,
    publicUrl: access === 'PUBLIC' ? buildFilePublicUrl(token.objectId) : undefined,
  }
}

export { buildFilePublicUrl }
