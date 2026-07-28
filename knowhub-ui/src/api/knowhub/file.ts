/**
 * 文件作用：
 * 集中管理文件上传对接后端的接口方法（预签名直传三步流：申请令牌 → PUT 直传 → 确认），
 * 供创作页 MD 编辑器插图、封面上传统一调用。路由前缀 /file（knowhub 命名空间）。
 * 关键约定：
 * - 预签名直传：applyUploadTokenApi 签发令牌 → 前端 PUT 直传 OSS → confirmUploadApi 确认。
 * - PUBLIC 回显走相对路径 /file/resolve/{id}（由 buildFileResolveUrl 拼接，不走 axios），
 *   渲染时 <img src> 命中后端 resolve 接口，由后端按当前 knowhub.file.access_mode 动态 302 分发。
 * 照搬后台 rookie-ui 的 api/knowhub/file.ts，仅保留创作页用到的上传三方法（下载/列表/绑定删掉）。
 * 权限：/file/upload-token 等需 knowhub:file:upload 按钮权限键——靠默认角色带该权限（见计划"前置条件"）。
 */
import { post } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { UploadApplyPayload, UploadTokenRecord } from '@/types/api/knowhub/file'

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
  post<ApiResult<boolean>>(`/file/confirm/${objectId}`, undefined, {
    params: bizRefId !== undefined ? { bizRefId } : undefined,
  })

/**
 * 方法效果：
 * 拼接 PUBLIC 对象的稳定解析引用 /file/resolve/{objectId}，供博客封面/正文插图统一存储。
 * 不调后端，仅拼相对路径；渲染时 <img src> 命中后端 resolve 接口，由后端按当前 knowhub.file.access_mode
 * 动态 302 分发（中转→/file/public/{id} 字节流回显；直链→OSS 公开读直链或私有预签名）。
 * 库里（cover_url、正文 markdown 图片）统一存此引用而非按模式拼死链接，切模式时历史数据回显自动跟着切。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 形如 /file/resolve/{objectId} 的相对路径。
 */
export const buildFileResolveUrl = (objectId: number): string => `/file/resolve/${objectId}`