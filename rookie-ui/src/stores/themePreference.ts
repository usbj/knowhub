import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { ThemePresetItem, ThemeSettings } from '@/types/components/theme'

const STORAGE_KEY = 'rookie-ui-theme-settings'

/**
 * 把十六进制颜色转成 RGB 字符串，方便生成透明色变量。
 */
const hexToRgb = (hex: string) => {
  const normalized = hex.replace('#', '')
  const value =
    normalized.length === 3
      ? normalized
          .split('')
          .map((char) => char + char)
          .join('')
      : normalized

  const number = Number.parseInt(value, 16)
  return {
    r: (number >> 16) & 255,
    g: (number >> 8) & 255,
    b: number & 255,
  }
}

/**
 * 生成更深或更浅的品牌色，给激活态和 hover 态复用。
 */
const shiftHex = (hex: string, amount: number) => {
  const { r, g, b } = hexToRgb(hex)
  const clamp = (value: number) => Math.max(0, Math.min(255, value))
  const next = [clamp(r + amount), clamp(g + amount), clamp(b + amount)]
  return `#${next.map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

/**
 * 所有主题可配置项都收口在这份默认值中，
 * 这样 reset、持久化恢复和设置面板初始化都能复用。
 */
const defaultThemeSettings: ThemeSettings = {
  mode: 'light',
  primaryColor: '#0f766e',
  fontSize: 14,
  radius: 12,
}

export const useThemePreferenceStore = defineStore('theme-preference', () => {
  /**
   * 预设色板给设置面板直接使用，避免每次都手敲颜色值。
   */
  const presets = ref<ThemePresetItem[]>([
    { name: '青绿', color: '#0f766e' },
    { name: '海蓝', color: '#0369a1' },
    { name: '墨青', color: '#155e75' },
    { name: '岩绿', color: '#166534' },
  ])

  const storedValue =
    typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_KEY) : null

  const initialSettings = storedValue
    ? ({ ...defaultThemeSettings, ...JSON.parse(storedValue) } as ThemeSettings)
    : defaultThemeSettings

  const settings = ref<ThemeSettings>(initialSettings)

  const isDarkMode = computed(() => settings.value.mode === 'dark')

  /**
   * 将设置同步为 CSS 变量，保证现有样式文件不需要改成大量的内联样式。
   */
  const applyThemeSettings = () => {
    if (typeof document === 'undefined') {
      return
    }

    const root = document.documentElement
    const { mode, primaryColor, fontSize, radius } = settings.value
    const primaryRgb = hexToRgb(primaryColor)
    const primaryStrong = shiftHex(primaryColor, mode === 'dark' ? 36 : -24)
    const hoverColor = mode === 'dark' ? 'rgba(255, 255, 255, 0.08)' : 'rgba(15, 23, 42, 0.05)'
    const activeHoverColor =
      mode === 'dark'
        ? `rgba(${primaryRgb.r}, ${primaryRgb.g}, ${primaryRgb.b}, 0.22)`
        : `rgba(${primaryRgb.r}, ${primaryRgb.g}, ${primaryRgb.b}, 0.18)`

    root.dataset.theme = mode
    root.style.setProperty('--rookie-font-size-base', `${fontSize}px`)
    root.style.setProperty('--rookie-radius-base', `${radius}px`)
    root.style.setProperty('--rookie-primary', primaryColor)
    root.style.setProperty('--rookie-primary-strong', primaryStrong)
    root.style.setProperty(
      '--rookie-primary-soft',
      `rgba(${primaryRgb.r}, ${primaryRgb.g}, ${primaryRgb.b}, 0.12)`,
    )
    root.style.setProperty('--rookie-hover-bg', hoverColor)
    root.style.setProperty('--rookie-active-hover-bg', activeHoverColor)

    if (mode === 'dark') {
      root.style.setProperty('--rookie-bg', '#0f172a')
      root.style.setProperty('--rookie-bg-elevated', '#111827')
      root.style.setProperty('--rookie-surface', '#111827')
      root.style.setProperty('--rookie-surface-muted', '#182235')
      root.style.setProperty('--rookie-card-bg', '#111827')
      root.style.setProperty('--rookie-border', '#243244')
      root.style.setProperty('--rookie-border-strong', '#334155')
      // 深色下斑马纹比卡片底略亮一档，保证奇偶行有可辨的微对比，而不是和 card-bg 完全相同
      root.style.setProperty(
        '--rookie-table-stripe-bg',
        'color-mix(in srgb, #ffffff 4%, var(--rookie-card-bg))',
      )
      root.style.setProperty('--rookie-text', '#e5edf8')
      root.style.setProperty('--rookie-text-secondary', '#b4c2d3')
      root.style.setProperty('--rookie-text-tertiary', '#7f90a8')
      root.style.setProperty('--rookie-danger', '#f87171')
      root.style.setProperty('--rookie-shadow', '0 18px 40px rgba(2, 6, 23, 0.38)')
      root.style.setProperty(
        '--rookie-danger-soft',
        'color-mix(in srgb, var(--rookie-danger) 14%, var(--rookie-card-bg))',
      )
      root.style.setProperty(
        '--rookie-danger-border',
        'color-mix(in srgb, var(--rookie-danger) 36%, var(--rookie-border))',
      )
      root.style.setProperty(
        '--rookie-login-gradient-end',
        'color-mix(in srgb, var(--rookie-bg) 84%, var(--rookie-surface-muted))',
      )
      root.style.setProperty('--el-mask-color', 'rgba(2, 6, 23, 0.42)')
      root.style.setProperty('--el-mask-color-extra-light', 'rgba(2, 6, 23, 0.12)')
      root.style.setProperty('--el-overlay-color-light', 'rgba(2, 6, 23, 0.42)')
      root.style.setProperty('--el-overlay-color', 'rgba(2, 6, 23, 0.42)')
    } else {
      root.style.setProperty('--rookie-bg', '#f4f7fb')
      root.style.setProperty('--rookie-bg-elevated', '#eef4fa')
      root.style.setProperty('--rookie-surface', '#ffffff')
      root.style.setProperty('--rookie-surface-muted', '#f8fbff')
      root.style.setProperty('--rookie-card-bg', '#ffffff')
      root.style.setProperty('--rookie-border', '#dbe3ef')
      root.style.setProperty('--rookie-border-strong', '#c7d6e8')
      root.style.setProperty('--rookie-text', '#172033')
      root.style.setProperty('--rookie-text-secondary', '#5b6780')
      root.style.setProperty('--rookie-text-tertiary', '#8a94a8')
      root.style.setProperty('--rookie-danger', '#d14343')
      root.style.setProperty('--rookie-shadow', '0 16px 40px rgba(23, 32, 51, 0.08)')
      root.style.setProperty(
        '--rookie-danger-soft',
        'color-mix(in srgb, var(--rookie-danger) 12%, var(--rookie-card-bg))',
      )
      root.style.setProperty(
        '--rookie-danger-border',
        'color-mix(in srgb, var(--rookie-danger) 32%, var(--rookie-border))',
      )
      root.style.setProperty(
        '--rookie-login-gradient-end',
        'color-mix(in srgb, var(--rookie-bg) 88%, #d8e2ef)',
      )
      root.style.setProperty('--el-mask-color', 'rgba(15, 23, 42, 0.28)')
      root.style.setProperty('--el-mask-color-extra-light', 'rgba(15, 23, 42, 0.08)')
      root.style.setProperty('--el-overlay-color-light', 'rgba(15, 23, 42, 0.28)')
      root.style.setProperty('--el-overlay-color', 'rgba(15, 23, 42, 0.28)')
    }
  }

  /**
   * 设置变更后自动持久化并刷新 CSS 变量。
   */
  watch(
    settings,
    (value) => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
      }
      applyThemeSettings()
    },
    { deep: true, immediate: true },
  )

  const toggleThemeMode = () => {
    settings.value.mode = settings.value.mode === 'light' ? 'dark' : 'light'
  }

  const resetThemeSettings = () => {
    settings.value = { ...defaultThemeSettings }
  }

  return {
    settings,
    presets,
    isDarkMode,
    applyThemeSettings,
    toggleThemeMode,
    resetThemeSettings,
  }
})
