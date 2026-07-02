/**
 * 文件作用：
 * 集中管理文件对象管理对接后端的接口方法，
 * 包括上传令牌/确认、PRIVATE 下载、列表/详情、绑定、删除，供文件管理页统一调用。
 * 路由前缀 /file（knowhub 命名空间，不套 /sys）。
 * 关键约定：
 * - 预签名直传：applyUploadTokenApi 签发令牌 → 前端 PUT 直传 RustFS → confirmUploadApi 确认。
 * - PUBLIC 回显走相对路径 /file/public/{id}（由 buildFilePublicUrl 拼接，不走 axios），
 *   dev 环境 /file 代理已在 vite.config.ts 配好，prod 需 nginx 同名转发。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  DownloadRecord,
  FileListQuery,
  FileObjectRecord,
  FilePageResult,
  UploadApplyPayload,
  UploadTokenRecord,
  BindPayload,
} from '@/types/api/knowhub/file'

/**
 * 方法效果：
 * 签发上传令牌。后端校验 contentType/size 落白名单后下发 PutObject 预签名 URL。
 * 参数：
 * - `data`：上传申请入参（businessType / contentType / size / originalName / access / bizRefId）。
 * 返回值：
 * - 后端 Result 包裹的上传令牌（uploadUrl / objectKey / objectId / expires）。
 */
export const applyUploadTokenApi = (data: UploadApplyPayload) =>
  post<ApiResult<UploadTokenRecord>, UploadApplyPayload>('/file/upload-token', data)

/**
 * 方法效果：
 * 上传确认。后端用 HeadObject 核对真实值后置 CONFIRMED，不符置 FAILED。
 * 参数：
 * - `objectId`：上传令牌返回的元数据行主键。
 * - `bizRefId`：可选业务关联 ID，传则一并回填 file_object.biz_ref_id。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const confirmUploadApi = (objectId: number, bizRefId?: number) =>
  post<ApiResult<boolean>>(`/file/confirm/${objectId}`, {
    params: bizRefId !== undefined ? { bizRefId } : undefined,
  })

/**
 * 方法效果：
 * 获取 PRIVATE 下载预签名 URL。鉴权通过后返回短期 GET 预签名（带 attachment;filename）。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 后端 Result 包裹的下载结果（downloadUrl / expires / originalName）。
 */
export const getDownloadUrlApi = (objectId: number) =>
  get<ApiResult<DownloadRecord>>(`/file/download/${objectId}`)

/**
 * 方法效果：
 * 分页查询文件对象列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：文件查询条件与分页参数。
 * 返回值：
 * - 归一化后的文件分页结果。
 */
export const getFilePageApi = (params: FileListQuery) =>
  getPage<FileObjectRecord>('/file/list', {
    params,
  }) as Promise<FilePageResult>

/**
 * 方法效果：
 * 根据文件对象主键获取详情。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 后端 Result 包裹的文件对象详情。
 */
export const getFileDetailApi = (objectId: number) =>
  get<ApiResult<FileObjectRecord>>(`/file/${objectId}`)

/**
 * 方法效果：
 * 绑定业务关联。业务行创建后回填 file_object.biz_ref_id。
 * 参数：
 * - `data`：绑定入参（objectId / bizRefId）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const bindBizRefApi = (data: BindPayload) =>
  put<ApiResult<boolean>, BindPayload>('/file/bind', data)

/**
 * 方法效果：
 * 批量删除文件对象。软删 file_object(deleted=1)，对象本体由 FileGcTask 定时物理删。
 * 参数：
 * - `objectIds`：待删除的文件对象主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteFileObjectsApi = (objectIds: number[]) =>
  del<ApiResult<boolean>>(`/file/${objectIds.join(',')}`)

/**
 * 方法效果：
 * 拼接 PUBLIC 对象的回显相对路径，供博客封面 <img>、文件预览等直接引用。
 * 不调后端，靠浏览器对 /file/public/{id} 的 302 重定向自动去 RustFS 拉图。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 形如 /file/public/{objectId} 的相对路径。
 */
export const buildFilePublicUrl = (objectId: number): string => `/file/public/${objectId}`
