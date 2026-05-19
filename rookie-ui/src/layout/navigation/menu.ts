import type { SysMenuRecord } from '@/types/api/system/menu'
import type { NavigationMenuItem } from '@/types/components/navigation'
import { resolveCanonicalMenuIconCode, resolveMenuIconComponent } from '@/utils/menu-icons'

/**
 * 方法效果：
 * 判断当前路由字符串是否已经是完整外链地址。
 * 参数：
 * - `route`：待判断的原始路由字符串。
 * 返回值：
 * - `true` 表示是外链地址；
 * - `false` 表示仍属于内部路由片段。
 */
const isExternalRoute = (route: string) => /^https?:\/\//i.test(route)

/**
 * 方法效果：
 * 清理路由片段首尾的斜杠，便于父子菜单安全拼接完整路径。
 * 参数：
 * - `route`：原始路由片段。
 * 返回值：
 * - 清洗后的单段路由字符串。
 */
const trimRouteSegment = (route: string) => route.trim().replace(/^\/+|\/+$/g, '')

/**
 * 方法效果：
 * 根据父级路由片段、当前节点路由片段和外链标记，生成最终用于前端跳转的完整路径。
 * 参数：
 * - `parentRoute`：父级累计路由。
 * - `currentRoute`：当前菜单或目录的原始路由片段。
 * - `backlinks`：后端外链标记，`1` 表示外链。
 * 返回值：
 * - 当前节点的完整前端跳转路径。
 */
const joinMenuRoute = (parentRoute: string, currentRoute: string, backlinks: number) => {
  if (!currentRoute) {
    return parentRoute
  }

  if (backlinks === 1 || isExternalRoute(currentRoute)) {
    return currentRoute
  }

  const normalizedParent = trimRouteSegment(parentRoute)
  const normalizedCurrent = trimRouteSegment(currentRoute)
  const segments = [normalizedParent, normalizedCurrent].filter(Boolean)

  return segments.length > 0 ? `/${segments.join('/')}` : '/'
}

/**
 * 方法效果：
 * 把后端菜单树标准化为前端导航树，并递归补齐完整路由和图标组件。
 * 参数：
 * - `menus`：后端返回的当前层菜单集合。
 * - `parentRoute`：父级累计路由片段，递归时内部使用。
 * 返回值：
 * - 适合前端侧边栏、标签页和面包屑直接消费的导航树。
 */
export const normalizeMenuTree = (
  menus: SysMenuRecord[],
  parentRoute = '',
): NavigationMenuItem[] => {
  return menus
    .filter((menu) => menu.status === 1 && menu.menuType !== 3)
    .map((menu) => {
      /**
       * 目录节点会把自身完整路由继续向下传递给子菜单，
       * 菜单节点则到自己为止，不再继续扩展父级片段。
       */
      const resolvedRoute = joinMenuRoute(parentRoute, menu.route, menu.backlinks)
      const nextParentRoute = menu.menuType === 1 ? resolvedRoute : parentRoute

      return {
        menuId: menu.menuId,
        menuName: menu.menuName,
        permKey: menu.permKey,
        parentId: menu.parentId,
        menuType: menu.menuType,
        routeSegment: menu.route,
        route: resolvedRoute,
        path: menu.path,
        backlinks: menu.backlinks,
        icon: resolveCanonicalMenuIconCode(menu.icon),
        iconComponent: resolveMenuIconComponent(menu.icon),
        status: menu.status,
        children: normalizeMenuTree(menu.sonMenus ?? [], nextParentRoute),
      }
    })
}
