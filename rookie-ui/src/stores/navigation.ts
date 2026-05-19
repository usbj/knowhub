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
   * 当前页面所属目录，用于目录标记和面包屑生成。
   */
  const activeDirectory = computed(() => {
    if (!currentMenu.value) {
      return null
    }

    return menuTree.value.find((menu) => menu.menuId === currentMenu.value?.parentId) ?? null
  })

  /**
   * 面包屑与侧边栏共享同一套层级语义：
   * - 如果页面属于目录，则显示“目录 > 菜单”
   * - 如果页面本身就是顶层菜单，则只显示菜单自己
   */
  const breadcrumbs = computed(() => {
    if (!currentMenu.value) {
      return []
    }

    if (activeDirectory.value) {
      return [activeDirectory.value, currentMenu.value]
    }

    return [currentMenu.value]
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

    if (
      activeDirectory.value &&
      !expandedDirectoryIds.value.includes(activeDirectory.value.menuId)
    ) {
      expandedDirectoryIds.value.push(activeDirectory.value.menuId)
    }
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
    activeDirectory,
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
