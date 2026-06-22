import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUserRoutesApi } from '@/api/system/user'
import { normalizeMenuTree } from '@/layout/navigation/menu'
import type { SysMenuRecord } from '@/types/api/system/menu'
import type { NavigationMenuItem, LayoutTabItem } from '@/types/components/navigation'

/**
 * 布局导航状态统一由这个 store 管理。
 * 这样侧边栏、面包屑、标签页和目录展开状态都围绕一份菜单树同步变化。
 */
export const useLayoutNavigationStore = defineStore('layout-navigation', () => {
  /**
   * 当前用户菜单树改为由后端接口驱动。
   * store 中同时保留原始树和标准化后的导航树，方便后续扩展。
   */
  const rawMenuTree = ref<SysMenuRecord[]>([])
  const menuTree = ref<NavigationMenuItem[]>([])
  const menuLoaded = ref(false)
  const dynamicRoutesReady = ref(false)

  /**
   * 当前路由路径收口到 store，避免多个组件各自监听 route 后状态不一致。
   */
  const currentPath = ref('')

  /**
   * 顶部标签页保留用户访问过的菜单页面，不记录目录节点。
   */
  const visitedTabs = ref<LayoutTabItem[]>([])

  /**
   * 目录展开状态单独维护，满足“目录可以折叠隐藏其子菜单”的交互要求。
   */
  const expandedDirectoryIds = ref<number[]>([])

  /**
   * 把树形菜单拍平，便于快速定位当前命中的菜单或父目录。
   */
  const flattenMenus = (menus: NavigationMenuItem[]): NavigationMenuItem[] =>
    menus.flatMap((menu) => [menu, ...flattenMenus(menu.children)])

  const flatMenuList = computed(() => flattenMenus(menuTree.value))

  const flattenRawMenus = (menus: SysMenuRecord[]): SysMenuRecord[] =>
    menus.flatMap((menu) => [menu, ...flattenRawMenus(menu.sonMenus ?? [])])

  const buttonPermissionKeys = computed(() => {
    const permissionKeys = flattenRawMenus(rawMenuTree.value)
      .filter((menu) => menu.menuType === 3 && menu.status === 1 && menu.permKey.trim())
      .map((menu) => menu.permKey.trim())

    return new Set(permissionKeys)
  })

  /**
   * 把后端菜单树写入导航 store，并顺手重置展开与标签状态。
   * 登录切换账号时，旧账号的导航痕迹不应该继续残留。
   */
  const setMenuTree = (menus: SysMenuRecord[]) => {
    rawMenuTree.value = menus
    menuTree.value = normalizeMenuTree(menus)
    expandedDirectoryIds.value = []
    visitedTabs.value = []
    menuLoaded.value = true
    dynamicRoutesReady.value = false
  }

  const fetchUserMenuTree = async () => {
    const result = await getCurrentUserRoutesApi()
    setMenuTree(result.data)
    return result.data
  }

  const markDynamicRoutesReady = () => {
    dynamicRoutesReady.value = true
  }

  /**
   * 退出登录或登录态失效时，清空当前账号留下的导航状态。
   */
  const resetNavigationState = () => {
    rawMenuTree.value = []
    menuTree.value = []
    menuLoaded.value = false
    dynamicRoutesReady.value = false
    currentPath.value = ''
    visitedTabs.value = []
    expandedDirectoryIds.value = []
  }

  /**
   * 当前页面只可能命中真实菜单，不会命中目录或按钮。
   */
  const currentMenu = computed(
    () => flatMenuList.value.find((menu) => menu.menuType === 2 && menu.route === currentPath.value) ?? null,
  )

  /**
   * 构建从顶层目录到直接父目录的完整祖先链。
   * 通过 parentId 在扁平菜单表里逐层向上反查，兼容"目录套目录"的任意深度嵌套，
   * 保证三级及以上嵌套时面包屑和自动展开都能拿到整条祖先路径，而非只取直接父级。
   * 参数：
   * - `menuId`：当前命中的菜单或目录主键。
   * 返回值：
   * - 祖先目录数组，顺序为顶层在前、直接父级在后；无祖先时返回空数组。
   */
  const buildAncestorTrail = (menuId: number): NavigationMenuItem[] => {
    const idToMenu = new Map<number, NavigationMenuItem>()
    flatMenuList.value.forEach((menu) => idToMenu.set(menu.menuId, menu))

    const trail: NavigationMenuItem[] = []
    // 从当前节点向上找父级，parentId<=0 表示已到顶层之外，停止追溯
    let cursor = idToMenu.get(menuId)
    while (cursor && cursor.parentId > 0) {
      const ancestor = idToMenu.get(cursor.parentId)

      if (!ancestor) {
        break
      }

      // 仅收集目录节点作为祖先，跳过理论上不会出现的非目录父级
      if (ancestor.menuType === 1) {
        trail.unshift(ancestor)
      }

      cursor = ancestor
    }

    return trail
  }

  /**
   * 当前页面所属的完整祖先目录链，用于面包屑生成和侧边栏自动展开。
   * 三级嵌套（如 系统模块 > 通知管理 > 通知内容）时会返回 [系统模块, 通知管理]。
   */
  const activeDirectoryTrail = computed(() => {
    if (!currentMenu.value) {
      return [] as NavigationMenuItem[]
    }

    return buildAncestorTrail(currentMenu.value.menuId)
  })

  /**
   * 面包屑与侧边栏共享同一套层级语义：
   * - 有祖先目录时，按"顶层目录 > ... > 直接父目录 > 当前菜单"完整展示
   * - 无祖先目录（顶层菜单）时，只显示菜单自己
   */
  const breadcrumbs = computed(() => {
    if (!currentMenu.value) {
      return []
    }

    return [...activeDirectoryTrail.value, currentMenu.value]
  })

  /**
   * 当前路由同步到 store 后，需要顺带完成两件事：
   * 1. 确保当前页面加入标签页
   * 2. 自动展开当前页面所属目录，避免用户刷新后看不到当前位置
   */
  const syncByPath = (path: string) => {
    currentPath.value = path

    if (!currentMenu.value) {
      return
    }

    const hasOpened = visitedTabs.value.some((tab) => tab.route === currentMenu.value?.route)
    if (!hasOpened) {
      visitedTabs.value.push({
        menuId: currentMenu.value.menuId,
        title: currentMenu.value.menuName,
        route: currentMenu.value.route,
      })
    }

    /**
     * 自动展开当前页面的整条祖先目录链，保证刷新或直进深层菜单时，
     * 从顶层到直接父级全部展开，用户始终能看到当前位置。
     * 嵌套目录下只展开直接父级会丢失上层目录可见性，因此遍历完整 trail。
     */
    activeDirectoryTrail.value.forEach((ancestor) => {
      if (!expandedDirectoryIds.value.includes(ancestor.menuId)) {
        expandedDirectoryIds.value.push(ancestor.menuId)
      }
    })
  }

  /**
   * 固定基础页不在后端菜单里时，也需要正常进入顶部标签。
   */
  const syncByRoute = (route: { path: string; meta?: Record<string, unknown> }) => {
    currentPath.value = route.path

    if (currentMenu.value) {
      syncByPath(route.path)
      return
    }

    const staticTitle = String(route.meta?.title ?? '').trim()
    const requiresAuth = Boolean(route.meta?.requiresAuth)

    if (!requiresAuth || !staticTitle) {
      return
    }

    const hasOpened = visitedTabs.value.some((tab) => tab.route === route.path)
    if (!hasOpened) {
      visitedTabs.value.push({
        menuId: 0,
        title: staticTitle,
        route: route.path,
      })
    }
  }

  /**
   * 目录点击时只控制展开/收起，不承担选中态。
   */
  const toggleDirectory = (menuId: number) => {
    if (expandedDirectoryIds.value.includes(menuId)) {
      expandedDirectoryIds.value = expandedDirectoryIds.value.filter((id) => id !== menuId)
      return
    }

    expandedDirectoryIds.value.push(menuId)
  }

  const isDirectoryExpanded = (menuId: number) => expandedDirectoryIds.value.includes(menuId)

  /**
   * 关闭标签后返回建议跳转路由，供顶栏在关闭当前页时完成自动跳转。
   */
  const closeTab = (route: string) => {
    if (visitedTabs.value.length <= 1) {
      return null
    }

    const closingIndex = visitedTabs.value.findIndex((tab) => tab.route === route)
    const fallbackTab =
      visitedTabs.value[closingIndex + 1] ?? visitedTabs.value[closingIndex - 1] ?? null

    visitedTabs.value = visitedTabs.value.filter((tab) => tab.route !== route)

    return fallbackTab?.route ?? null
  }

  /**
   * 标签栏的操作区会用到“关闭其他”“关闭全部”。
   */
  const closeOtherTabs = (route: string) => {
    visitedTabs.value = visitedTabs.value.filter((tab) => tab.route === route)
  }

  const closeAllTabs = () => {
    if (!currentMenu.value) {
      const currentTab = visitedTabs.value.find((tab) => tab.route === currentPath.value) ?? null

      visitedTabs.value = currentTab ? [currentTab] : []
      return currentTab?.route ?? null
    }

    visitedTabs.value = [
      {
        menuId: currentMenu.value.menuId,
        title: currentMenu.value.menuName,
        route: currentMenu.value.route,
      },
    ]

    return currentMenu.value.route
  }

  return {
    rawMenuTree,
    menuTree,
    buttonPermissionKeys,
    menuLoaded,
    dynamicRoutesReady,
    currentPath,
    currentMenu,
    activeDirectoryTrail,
    breadcrumbs,
    visitedTabs,
    expandedDirectoryIds,
    setMenuTree,
    fetchUserMenuTree,
    markDynamicRoutesReady,
    resetNavigationState,
    syncByPath,
    syncByRoute,
    toggleDirectory,
    isDirectoryExpanded,
    closeTab,
    closeOtherTabs,
    closeAllTabs,
  }
})
