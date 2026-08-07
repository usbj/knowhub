/**
 * 文件作用：
 * 文件上传类型白名单前端预检工具。后端 FileServiceImpl.applyUploadToken 签发上传令牌时
 * 校验 contentType 落白名单（系统设置 knowhub.file.type_whitelist，JSON 对象，按业务类型 code
 * 分桶，逗号分隔的扩展名/MIME 列表，空=不限制）。本工具拉该设置项并按文件名扩展名/MIME 预检，
 * 失败给出中文提示，避免一次失败往返；上传 UI 据此灰字提示允许类型。
 * 与后端 isContentTypeAllowed（StorageConfigReader.java:117-137）镜像：MIME 项全匹配，
 * 扩展名项按文件名末尾比对（浏览器对未知文件 contentType 常给 application/octet-stream，
 * 故用文件名扩展名比对，比后端按 contentType 比对更准）。
 * localStorage 缓存由 sysConfig store 自带（knowhub-system-config-cache），无需重复造。
 * 仅登录态可用（getSysConfigValueApi 需登录），上传场景天然登录态。
 */
import { useSysConfigStore } from '@/stores/system-config'

/** 系统设置键：各业务类型类型白名单（与后端 StorageConfigReader.CONFIG_KEY_TYPE_WHITELIST 对齐） */
const CONFIG_KEY_TYPE_WHITELIST = 'knowhub.file.type_whitelist'

/**
 * 解析白名单设置项 JSON 字符串为 { businessTypeCode → 原始逗号串 }。
 * 拉取失败/置空/JSON 损坏 → 返回空对象（视为不限制，与后端降级口径一致）。
 */
const parseWhitelistMap = async (): Promise<Record<string, string>> => {
  const sysConfigStore = useSysConfigStore()
  let raw: string | null
  try {
    raw = await sysConfigStore.fetchSysConfig(CONFIG_KEY_TYPE_WHITELIST)
  } catch {
    return {}
  }
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(obj)) {
      if (typeof v === 'string') out[k] = v
    }
    return out
  } catch {
    return {}
  }
}

/**
 * 取某业务类型的白名单列表（小写，扩展名带前导点或 MIME）。
 * 空数组表示不限制（业务类型未配置或值为空），与后端 typeWhitelist 口径一致。
 */
export const getUploadWhitelist = async (businessType: string): Promise<string[]> => {
  const map = await parseWhitelistMap()
  const raw = map[businessType]
  if (!raw || !raw.trim()) return []
  return raw
    .split(',')
    .map((x) => x.trim())
    .filter((x) => x.length > 0)
    .map((x) => x.toLowerCase())
}

/** 预检结果：ok=false 时 reason 中文 + allowed 列表供 UI 提示 */
export interface UploadCheckResult {
  ok: boolean
  /** 不通过时的中文原因，含允许类型清单 */
  reason?: string
  /** 允许类型列表（小写），供 UI 灰字提示 */
  allowed?: string[]
}

/**
 * 预检文件是否落在该业务类型白名单内。
 * 白名单空 → ok=true（不限制）；否则按文件名扩展名 + contentType 双比对（镜像后端口径，且更准）：
 * - MIME 项（含 /）：contentType 全匹配或以 `{item};` 开头。
 * - 扩展名项（以 . 开头）：按文件名末尾扩展名比对（case-insensitive）。
 * 不命中 → ok=false + reason 中文（"该文件类型不在允许范围，允许：xxx"）。
 */
export const checkFileAllowed = async (file: File, businessType: string): Promise<UploadCheckResult> => {
  const allowed = await getUploadWhitelist(businessType)
  if (!allowed.length) return { ok: true, allowed: [] }
  const contentType = (file.type || '').toLowerCase()
  const fileName = (file.name || '').toLowerCase()
  for (const item of allowed) {
    if (item.includes('/')) {
      // MIME 项：按 contentType 匹配（含分号参数情形）
      if (contentType === item || contentType.startsWith(item + ';')) {
        return { ok: true, allowed }
      }
    } else {
      // 扩展名项（带不带前导点都兜底）：按文件名末尾比对
      const ext = item.startsWith('.') ? item : '.' + item
      if (fileName.endsWith(ext)) {
        return { ok: true, allowed }
      }
    }
  }
  // 不命中：拼中文提示。MIME 项原样，扩展名项补前导点更易读
  const display = allowed.map((x) => (x.includes('/') ? x : x.startsWith('.') ? x : '.' + x)).join(' / ')
  return {
    ok: false,
    allowed,
    reason: `该文件类型不在允许范围，允许：${display || '不限'}`,
  }
}

/**
 * 生成 UI 灰字提示用的允许类型文案。
 * 空白名单 → "不限类型"；否则"MIME/扩展名"列表。
 */
export const formatAllowedHint = async (businessType: string): Promise<string> => {
  const allowed = await getUploadWhitelist(businessType)
  if (!allowed.length) return '不限类型'
  return allowed
    .map((x) => (x.includes('/') ? x : x.startsWith('.') ? x : '.' + x))
    .join(' / ')
}