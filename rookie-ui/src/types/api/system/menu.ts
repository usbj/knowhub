/**
 * 与后端菜单管理模块 `SysMenuVo` 对齐的接口类型。
 * 当前约定：
 * - `route` 表示当前节点在菜单层级中的路由片段
 * - `path` 表示前端要加载的 view 组件定位字段
 */
export interface SysMenuRecord {
  menuId: number
  menuName: string
  permKey: string
  parentId: number
  menuType: number
  route: string
  backlinks: number
  path: string
  icon: string
  status: number
  sonMenus: SysMenuRecord[]
}
