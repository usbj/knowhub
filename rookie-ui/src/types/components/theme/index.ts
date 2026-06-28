/**
 * 基础主题控制整体明暗。
 */
export type ThemeMode = 'light' | 'dark'

/**
 * 主题设置项统一放在这里，方便设置面板和主题 store 共享同一份结构。
 */
export interface ThemeSettings {
  mode: ThemeMode
  primaryColor: string
  fontSize: number
  radius: number
}

/**
 * 内部主题预设主要影响菜单、标签、焦点等品牌色表达。
 */
export interface ThemePresetItem {
  name: string
  color: string
}

/**
 * 顶部通知下拉使用的统一结构。
 * 由 notice store 的 SysNoticeRecord 映射而来，供头导航下拉与详情弹窗复用。
 */
export interface NotificationItem {
  id: number
  title: string
  summary: string
  time: string
  unread: boolean
  publisher?: string
  category?: string
  /** 完整正文，供详情弹窗展示；下拉列表只用 summary 摘要 */
  content?: string
  /** 是否需要用户确认，用于详情弹窗决定是否展示确认按钮 */
  needConfirm?: boolean
  /** 是否置顶，用于下拉项展示置顶标记 */
  isTop?: boolean
}
