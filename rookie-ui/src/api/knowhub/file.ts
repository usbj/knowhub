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
  MigrationApplyPayload,
  MigrationProgressRecord,
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
  post<ApiResult<boolean>>(`/file/confirm/${objectId}`, undefined, {
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
 * 不调后端，靠浏览器对 /file/public/{id} 的同源请求拉取后端中转回写的图片字节流。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 形如 /file/public/{objectId} 的相对路径。
 */
export const buildFilePublicUrl = (objectId: number): string => `/file/public/${objectId}`

/**
 * 方法效果：
 * 拼接 PUBLIC 对象的稳定解析引用 /file/resolve/{objectId}，供博客封面/正文插图/文件预览统一存储。
 * 不调后端，仅拼相对路径；渲染时 <img src> 命中后端 resolve 接口，由后端按当前 knowhub.file.access_mode
 * 动态 302 分发（中转→/file/public/{id} 字节流回显；直链→OSS 公开读直链或私有预签名）。
 * 库里（cover_url、正文 markdown 图片）统一存此引用而非按模式拼死链接，切模式时历史数据回显自动跟着切。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 形如 /file/resolve/{objectId} 的相对路径。
 */
export const buildFileResolveUrl = (objectId: number): string => `/file/resolve/${objectId}`

/**
 * 方法效果：
 * 取 PUBLIC 对象按当前访问模式的回显链接（中转模式→/file/public/{id}；直链模式→OSS/nginx 直链地址）。
 * 上传成功后调此接口拿按模式的链接回填，使前端不关心 OSS 地址、迁移零改动。
 * 参数：
 * - `objectId`：文件对象主键。
 * 返回值：
 * - 后端 Result 包裹的回显链接字符串。
 */
export const getPublicAccessUrlApi = (objectId: number) =>
  get<ApiResult<string>>(`/file/url/${objectId}`)

// ---- 扩展点1：OSS 数据打包下载 ----

/**
 * 方法效果：
 * 打包下载预估总字节数。后端累加所有 deleted=0 + CONFIRMED 的 file_object.content_length。
 * 供前端在发起打包下载前做大小预估、超 50G 弹警告确认。
 * 返回值：
 * - 后端 Result 包裹的总字节数。
 */
export const packSizeApi = () => get<ApiResult<number>>('/file/pack-size')

/**
 * 方法效果：
 * 打包下载到服务器本地磁盘（SERVER 模式）。后端写 zip 到 storage.local-base-path 下，返回落盘绝对路径。
 * 中转模式下打包强制走本端点（用户浏览器不可达 OSS，打包到客户端意义不大，落服务器本地后再人工取）。
 * 返回值：
 * - 后端 Result 包裹的落盘绝对路径字符串。
 */
export const packDownloadServerApi = () =>
  post<ApiResult<string>>('/file/pack-download-server')

// ---- 扩展点2：OSS 数据迁移 ----

/**
 * 方法效果：
 * 启动 OSS 数据迁移。前端传源/目标 OSS 连接参数（凭证内存用完即弃、不落库），后端建任务行 + @Async 线程拷贝。
 * 目录结构一致：源 objectKey 原样作目标 objectKey。
 * 参数：
 * - `data`：源/目标 OSS 连接参数（endpoint/region/accessKey/secretKey/bucket/pathStyleAccess）。
 * 返回值：
 * - 后端 Result 包裹的迁移任务 ID。
 */
export const startMigrationApi = (data: MigrationApplyPayload) =>
  post<ApiResult<number>, MigrationApplyPayload>('/file/migration/start', data)

/**
 * 方法效果：
 * 查迁移进度。前端轮询（setInterval 2-3 秒）展示进度条 done/total + 状态，完成/失败停轮询。
 * 参数：
 * - `taskId`：startMigrationApi 返回的任务 ID。
 * 返回值：
 * - 后端 Result 包裹的迁移进度记录。
 */
export const getMigrationProgressApi = (taskId: number) =>
  get<ApiResult<MigrationProgressRecord>>(`/file/migration/progress/${taskId}`)
