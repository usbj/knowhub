/**
 * 文件作用：
 * 审计模块共享的票据/附件下载工具，供流水详情、借出详情、借出审核/归还弹窗里
 * 把 voucherObjectId 渲染为「点击下载」的入口复用。
 * 关键约定：
 * - PUBLIC/PRIVATE 统一走 getDownloadUrlApi 拿带 attachment;filename 的下载链接（后端按访问模式发：
 *   中转模式→/file/proxy/{objectId} 同源带 Token；直链模式→预签名绝对 URL 带 attachment）。
 * - 相对路径（中转模式）：同源鉴权接口，window.open 不带 Token 会 401，改用 fetch 带 Token 头取 blob 再 a.click()；
 * - 绝对 URL（直链模式）：直接 window.open 跳转拉取（预签名自带 attachment）。
 * - 仅依赖 @/api/knowhub/file 与 @/stores/user 的 token key，不改 rookie-ui 原有组件。
 */
import { ElMessage } from 'element-plus'
import { getDownloadUrlApi } from '@/api/knowhub/file'
import { USER_TOKEN_STORAGE_KEY } from '@/stores/user'

/**
 * 方法效果：
 * 按文件对象主键触发下载（中转取 blob 或直链跳转），失败弹错。
 * 参数：
 * - `objectId`：文件对象主键；空/非法直接返回。
 * 返回值：
 * - 无返回值；副作用是触发浏览器下载。
 */
export const downloadVoucher = async (objectId: number | null | undefined): Promise<void> => {
  if (objectId == null || Number.isNaN(Number(objectId))) {
    ElMessage.warning('未关联附件')
    return
  }
  const id = Number(objectId)

  const result = await getDownloadUrlApi(id)
  const downloadUrl = result.data?.downloadUrl
  if (!downloadUrl) {
    ElMessage.warning('附件暂不可下载')
    return
  }

  // 绝对 URL = 直链模式直接跳转（预签名自带 attachment）
  if (downloadUrl.startsWith('http://') || downloadUrl.startsWith('https://')) {
    window.open(downloadUrl, '_blank')
    return
  }

  // 中转模式：fetch 带 Token 头取 blob 再 a.click()
  const fallbackName = result.data?.originalName ?? `附件_${id}`
  try {
    const token = localStorage.getItem(USER_TOKEN_STORAGE_KEY)
    const resp = await fetch(downloadUrl, {
      headers: token ? { Token: token } : {},
    })
    if (!resp.ok) {
      ElMessage.error(`下载失败：HTTP ${resp.status}`)
      return
    }
    const blob = await resp.blob()
    const objUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objUrl
    const disposition = resp.headers.get('Content-Disposition') ?? ''
    const nameMatch = disposition.match(/filename="?([^";]+)"?/)
    a.download = nameMatch?.[1] ?? fallbackName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objUrl)
  } catch {
    ElMessage.error('下载失败：网络异常')
  }
}