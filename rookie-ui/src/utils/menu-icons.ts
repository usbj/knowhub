import { markRaw, type Component } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

export interface MenuIconOption {
  label: string
  value: string
  component: Component
  keywords: string[]
}

const legacyMenuIconAliases: Record<string, string> = {
  dashboard: 'DataBoard',
  workbench: 'Monitor',
  project: 'FolderOpened',
  files: 'Files',
  system: 'Operation',
  user: 'User',
  role: 'UserFilled',
  menu: 'Menu',
  dict: 'CollectionTag',
  about: 'InfoFilled',
  overview: 'Grid',
  setting: 'Setting',
}

const toKebabCase = (value: string) =>
  value.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()

const rawMenuIconOptions = Object.entries(ElementPlusIconsVue)
  .filter(([, component]) => typeof component === 'object' || typeof component === 'function')
  .map(([iconName, component]) => ({
    label: iconName,
    value: iconName,
    component: markRaw(component as Component),
    keywords: [iconName.toLowerCase(), toKebabCase(iconName)],
  }))
  .sort((previous, next) => previous.label.localeCompare(next.label))

const menuIconOptionsByValue = new Map(
  rawMenuIconOptions.map((item) => [item.value, item]),
)
const fallbackMenuIconComponent = markRaw(ElementPlusIconsVue.Setting as Component)

export const menuIconOptions: MenuIconOption[] = rawMenuIconOptions

export const resolveCanonicalMenuIconCode = (iconCode?: string) => {
  const normalizedIconCode = String(iconCode ?? '').trim()

  if (!normalizedIconCode) {
    return 'Menu'
  }

  if (menuIconOptionsByValue.has(normalizedIconCode)) {
    return normalizedIconCode
  }

  return legacyMenuIconAliases[normalizedIconCode] ?? 'Setting'
}

export const resolveMenuIconComponent = (iconCode?: string) =>
  menuIconOptionsByValue.get(resolveCanonicalMenuIconCode(iconCode))?.component ??
  fallbackMenuIconComponent
