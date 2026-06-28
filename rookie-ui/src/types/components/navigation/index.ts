import type { Component } from 'vue'

/**
 * 供布局导航层消费的标准化菜单节点。
 * 它不是后端原样数据，而是过滤掉按钮、补齐图标组件后的结构。
 */
export interface NavigationMenuItem {
  menuId: number
  menuName: string
  permKey: string
  parentId: number
  menuType: number
  routeSegment: string
  route: string
  path: string
  backlinks: number
  icon: string
  iconComponent: Component
  status: number
  children: NavigationMenuItem[]
}

/**
 * 顶部标签页只保留可真正跳转的菜单页面，
 * 因此字段控制在标题、路由和标识信息这几个最小集合。
 */
export interface LayoutTabItem {
  menuId: number
  title: string
  route: string
}

export interface SideBarMenuButtonProps {
  label: string
  icon: Component
  active?: boolean
  ancestorActive?: boolean
  collapsed?: boolean
  depth?: number
}

export interface SideBarSectionProps {
  item: NavigationMenuItem
  collapsed: boolean
  currentPath: string
  expanded: boolean
  expandedDirectoryIds: number[]
  depth?: number
}

export interface NavBreadcrumbProps {
  items: NavigationMenuItem[]
}

export interface NavTabsProps {
  tabs: LayoutTabItem[]
  activeRoute: string
}
