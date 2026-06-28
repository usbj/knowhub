/**
 * 文件作用：
 * 根据后端返回的菜单树生成并注册动态路由，
 * 同时把菜单里的组件定位字段映射到真实的 view 组件。
 */
import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import PlaceholderView from '@/views/PlaceholderView.vue'
import type { SysMenuRecord } from '@/types/api/system/menu'
import RouteView from './RouteView'

const viewModules = import.meta.glob([
  '../views/**/*.vue',
  '!../views/login.vue',
  '!../views/PlaceholderView.vue',
])

const dynamicRouteNames = new Set<string>()

/**
 * 方法效果：
 * 判断当前菜单是否属于外链节点。
 * 参数：
 * - `menu`：后端返回的单个菜单节点。
 * 返回值：
 * - `true` 表示当前节点属于外链，不参与内部动态路由注册。
 */
const isExternalLink = (menu: SysMenuRecord) => menu.backlinks === 1

/**
 * 方法效果：
 * 判断当前菜单是否属于真正可参与路由树的节点。
 * 参数：
 * - `menu`：后端返回的单个菜单节点。
 * 返回值：
 * - `true` 表示目录或菜单节点；
 * - `false` 表示按钮节点或其他不应注册为页面路由的节点。
 */
const isRoutableMenu = (menu: SysMenuRecord) => menu.menuType === 1 || menu.menuType === 2

/**
 * 方法效果：
 * 清洗单段路由字符串，去掉首尾多余斜杠，便于后续安全拼接。
 * 参数：
 * - `value`：原始路由片段。
 * 返回值：
 * - 清洗后的路由片段。
 */
const trimSegment = (value: string) => value.trim().replace(/^\/+|\/+$/g, '')

/**
 * 方法效果：
 * 将后端提供的组件定位字段标准化为 `src/views` 下可匹配的相对路径。
 * 参数：
 * - `componentPath`：后端菜单 `path` 字段。
 * 返回值：
 * - 去除前导斜杠和 `.vue` 后缀后的组件定位字符串。
 */
const normalizeComponentPath = (componentPath: string) => {
  const trimmed = componentPath.trim().replace(/^\/+/, '').replace(/\.vue$/i, '')
  return trimmed
}

/**
 * 方法效果：
 * 根据后端菜单里的组件定位字段，解析出实际要渲染的前端组件。
 * 参数：
 * - `componentPath`：后端菜单 `path` 字段。
 * 返回值：
 * - 命中时返回异步组件加载器；
 * - 未命中时返回占位页组件，保证路由仍可落地。
 */
const resolveViewComponent = (componentPath: string): Component | (() => Promise<unknown>) => {
  const normalized = normalizeComponentPath(componentPath)

  if (!normalized) {
    return PlaceholderView
  }

  const candidates = [
    `../views/${normalized}.vue`,
    `../views/${normalized}/index.vue`,
  ]

  for (const candidate of candidates) {
    const loader = viewModules[candidate]

    if (loader) {
      return loader
    }
  }

  return PlaceholderView
}

/**
 * 方法效果：
 * 为动态菜单路由生成稳定唯一的路由名称。
 * 参数：
 * - `menu`：后端返回的单个菜单节点。
 * 返回值：
 * - 以菜单主键为基础的动态路由名称。
 */
const buildRouteName = (menu: SysMenuRecord) => `dynamic-menu-${menu.menuId}`

/**
 * 方法效果：
 * 把后端菜单节点递归转换为 Vue Router 路由记录。
 * 参数：
 * - `menu`：后端返回的单个菜单节点。
 * 返回值：
 * - 可注册的 `RouteRecordRaw`；
 * - 如果当前节点是外链或按钮，则返回 `null`，表示不注册内部路由。
 */
const buildRouteRecord = (menu: SysMenuRecord): RouteRecordRaw | null => {
  if (isExternalLink(menu) || !isRoutableMenu(menu)) {
    return null
  }

  /**
   * 这里先递归过滤按钮和外链，再保留可注册的子节点。
   * 这样即使后端在目录或菜单下挂了按钮权限节点，也不会污染内部页面路由树。
   */
  const pathSegment = trimSegment(menu.route)
  const children = (menu.sonMenus ?? [])
    .map((child) => buildRouteRecord(child))
    .filter((child): child is RouteRecordRaw => child !== null)

  const hasChildren = children.length > 0
  const component =
    menu.menuType === 1
      ? menu.path?.trim()
        ? resolveViewComponent(menu.path)
        : RouteView
      : resolveViewComponent(menu.path)

  return {
    path: pathSegment,
    name: buildRouteName(menu),
    component,
    children,
    meta: {
      requiresAuth: true,
      title: menu.menuName,
      menuId: menu.menuId,
      menuType: menu.menuType,
      permKey: menu.permKey,
      backlinks: menu.backlinks,
      componentPath: menu.path,
      hasChildren,
    },
  }
}

/**
 * 方法效果：
 * 从后端菜单树中找到当前用户第一条可访问的内部菜单路径。
 * 参数：
 * - `menus`：后端返回的菜单树。
 * - `parentRoute`：父级累计路由片段，递归时内部使用。
 * 返回值：
 * - 第一条可访问菜单的完整路径；
 * - 如果不存在可访问内部菜单，则返回 `null`。
 */
const findFirstAccessibleMenuPath = (menus: SysMenuRecord[], parentRoute = ''): string | null => {
  for (const menu of menus) {
    /**
     * 外链和按钮都不属于应用内部可跳转页面：
     * - 外链不应作为登录后的内部首页
     * - 按钮只是权限点，不应生成页面访问目标
     */
    if (isExternalLink(menu) || !isRoutableMenu(menu)) {
      continue
    }

    const fullPath = [trimSegment(parentRoute), trimSegment(menu.route)].filter(Boolean).join('/')
    const resolvedPath = `/${fullPath}`

    if (menu.menuType === 2) {
      return resolvedPath
    }

    const childPath = findFirstAccessibleMenuPath(menu.sonMenus ?? [], fullPath)
    if (childPath) {
      return childPath
    }
  }

  return null
}

/**
 * 方法效果：
 * 将当前用户的菜单树批量注册为 `layout` 下的动态子路由。
 * 参数：
 * - `router`：Vue Router 实例所需的最小路由注册接口。
 * - `menus`：后端返回的菜单树。
 * 返回值：
 * - 无返回值；副作用是向路由实例中追加动态子路由。
 */
export const registerDynamicRoutes = (
  router: { addRoute: (parentName: string, route: RouteRecordRaw) => void },
  menus: SysMenuRecord[],
) => {
  for (const menu of menus) {
    const routeRecord = buildRouteRecord(menu)

    if (!routeRecord) {
      continue
    }

    router.addRoute('layout', routeRecord)
    dynamicRouteNames.add(String(routeRecord.name))
  }
}

/**
 * 方法效果：
 * 清空此前注册过的所有动态菜单路由。
 * 参数：
 * - `router`：Vue Router 实例所需的最小路由移除接口。
 * 返回值：
 * - 无返回值；副作用是从路由实例中移除动态菜单路由。
 */
export const unregisterDynamicRoutes = (
  router: { hasRoute: (name: string) => boolean; removeRoute: (name: string) => void },
) => {
  for (const routeName of dynamicRouteNames) {
    if (router.hasRoute(routeName)) {
      router.removeRoute(routeName)
    }
  }

  dynamicRouteNames.clear()
}

/**
 * 方法效果：
 * 对外暴露当前用户第一条可访问内部菜单路径，并在缺省时提供根路径兜底。
 * 参数：
 * - `menus`：后端返回的菜单树。
 * 返回值：
 * - 可直接用于路由跳转的字符串路径。
 */
export const getFirstAccessibleMenuPath = (menus: SysMenuRecord[]) =>
  findFirstAccessibleMenuPath(menus) || '/'
