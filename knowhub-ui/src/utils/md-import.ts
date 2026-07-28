/**
 * 文件作用：
 * 前台 .md 文件导入博客草稿的纯前端解析工具（不碰后端、不传文件，遇到本地图片路径就地改写）。
 * <p>
 * 设计取舍（决策：先做纯前台，zip 打包导入留后期）：
 * - 复用既有 POST /authoring/blog/draft：把 md 文本读成字符串填进 BlogAuthoringPayload.content，
 *   标题取文件名去 .md 后缀，等级默认 L1、标签空——作者在创作页补元信息后再存草稿。
 *   等价于"作者把 md 粘进编辑器"，零新接口、零后端改动、分级创作闸与草稿闭环复用不变。
 * - 本地图片（Typora/Obsidian 导出的 ./assets/x.png、file:///、绝对盘符路径等）前台 v-md-preview
 *   会 404 裂图，且无 zip 上传做不了后端改写——故解析阶段把这类 image markdown 整体替换成
 *   单行占位标记 `[图片：alt]`（alt 为空则 `[图片]`），作者后续在编辑页对占位手动重插图。
 * - 外链（http/https）、自家已传的 `/file/resolve/{id}`、data: 内联图片保留原样不动。
 */

export interface ImportedMd {
  /** 博客标题：文件名去 .md 后缀截断 100 字符，空则占位"未命名博客" */
  title: string
  /** 博客正文：本地图片已改写为占位标记的 md 文本 */
  content: string
}

/** 导入 md 文本大小上限：5MB（作者本地 md 一般 KB 级，超此多半是内联 data: 大图，拒掉护内存） */
export const MD_IMPORT_MAX_SIZE = 5 * 1024 * 1024

/**
 * 判定 image markdown 里的 url 是否为"保留原样不替换"的外部/已就绪引用：
 * - http(s) 外链（CSDN/掘金等图床导出常见，v-md-preview 直接可显）
 * - 自家 /file/resolve/{id} 已预签名直传回的稳定引用
 * - data:image/ 内联图（体积可控时保留，避免误伤）
 * 其余（./、assets/、/绝对路径、file:///、盘符 C:\ 等）一律视为本地路径 → 需替换占位。
 */
export const isExternalImage = (url: string): boolean => {
  const u = url.trim()
  if (!u) return false
  if (/^https?:\/\//i.test(u)) return true
  if (u.startsWith('/file/resolve/')) return true
  if (/^data:image\//i.test(u)) return true
  return false
}

/**
 * 把 md 文本中指向本地路径的 image markdown 改写为单行占位标记。
 * 匹配 ![alt](url) 与 ![alt](url "title") 两种形态；url 含空白时按首段取链接。
 * 保留外链/自家/内联图不动。作者后续在创作页对占位手动重插图。
 */
export const sanitizeLocalImages = (md: string): string =>
  md.replace(
    /!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g,
    (whole: string, alt: string, url: string) => {
      if (isExternalImage(url)) return whole
      const a = alt.trim()
      return a ? `[图片：${a}]` : '[图片]'
    },
  )

/**
 * 解析 .md File 为可填入创作表单的 {title, content}。
 * @throws 文件超 MD_IMPORT_MAX_SIZE 或读为空时抛错，由调用方提示用户。
 */
export const parseMdFile = async (file: File): Promise<ImportedMd> => {
  if (file.size > MD_IMPORT_MAX_SIZE) {
    throw new Error(`md 文件过大（>${MD_IMPORT_MAX_SIZE / 1024 / 1024}MB），请压缩后重试`)
  }
  const raw = await file.text()
  if (!raw || !raw.trim()) {
    throw new Error('md 文件内容为空')
  }
  const content = sanitizeLocalImages(raw)
  const title =
    file.name.replace(/\.md$/i, '').trim().slice(0, 100) || '未命名博客'
  return { title, content }
}