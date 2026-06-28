import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysMenuRecord } from '@/types/api/system/menu'
import type { SysRoleRecord } from '@/types/api/system/role'
import { formatDateTime } from '@/utils/format'

export interface RoleQueryFormState {
  roleName: string
  status: number | undefined
  dateRange: string[]
}

export const createDefaultRoleQuery = (): RoleQueryFormState => ({
  roleName: '',
  status: undefined,
  dateRange: [],
})

export const createDefaultRoleForm = (): SysRoleRecord => ({
  roleName: '',
  roleLevel: 1,
  roleKey: '',
  status: 1,
  isDefault: 0,
  permId: [],
  rolePerm: [],
})

export const createRoleQuerySchema = (): SharedFieldSchemaMap<RoleQueryFormState> => ({
  roleName: {
    label: '角色名称',
    inputType: 'text',
    placeholder: '请输入角色名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
    props: { style: { width: '100%' } },
    options: [
      { label: '正常', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 8,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

export const createRoleSchema = (): SharedFieldSchemaMap<SysRoleRecord> => ({
  roleId: {
    label: '角色编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  roleName: {
    label: '角色名称',
    inputType: 'text',
    placeholder: '请输入角色名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    span: 12,
  },
  roleKey: {
    label: '权限字符',
    inputType: 'text',
    placeholder: '请输入权限字符',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
  },
  roleLevel: {
    label: '角色级别',
    inputType: 'number',
    placeholder: '请输入角色级别',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
    props: {
      min: 1,
      max: 999,
      controlsPosition: 'right',
      style: { width: '100%' },
    },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    span: 12,
    options: [
      { label: '正常', value: 1 },
      { label: '停用', value: 0 },
    ],
    formatter: (value) => (Number(value) === 1 ? '正常' : '停用'),
  },
  isDefault: {
    label: '默认角色',
    inputType: 'select',
    placeholder: '请选择默认状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    options: [
      { label: '是', value: 1 },
      { label: '否', value: 0 },
    ],
    formatter: (value) => (Number(value) === 1 ? '是' : '否'),
  },
  permId: {
    label: '菜单权限',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 24,
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})

export const roleFormRules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 20, message: '角色名称长度需在 2 到 20 位之间', trigger: 'blur' },
  ],
  roleKey: [
    { required: true, message: '请输入权限字符', trigger: 'blur' },
    { min: 2, max: 50, message: '权限字符长度需在 2 到 50 位之间', trigger: 'blur' },
  ],
  roleLevel: [{ required: true, message: '请输入角色级别', trigger: 'change', type: 'number' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  permId: [{ required: true, message: '请至少选择一个菜单权限', trigger: 'change', type: 'array' }],
}

export const buildPermissionTree = (menus: SysMenuRecord[]): Array<Record<string, unknown>> =>
  menus.map((menu) => ({
    label: menu.menuName,
    value: Number(menu.menuId),
    children: buildPermissionTree(menu.sonMenus ?? []),
  }))

/**
 * 方法效果：
 * 根据当前勾选的菜单主键集合，自动补齐所有祖先节点主键。
 * 参数：
 * - `nodes`：权限树节点集合，节点格式约定为 `{ value, children }`。
 * - `selectedIds`：当前实际勾选的菜单主键数组。
 * 返回值：
 * - 包含原勾选节点及其全部祖先节点的主键数组。
 */
export const collectPermissionIdsWithAncestors = (
  nodes: Array<Record<string, unknown>>,
  selectedIds: number[],
): number[] => {
  const parentMap = new Map<number, number | null>()
  const orderedNodeIds: number[] = []

  const walkNodes = (currentNodes: Array<Record<string, unknown>>, parentId: number | null = null) => {
    currentNodes.forEach((node) => {
      const currentId = Number(node.value ?? 0)

      if (currentId > 0) {
        parentMap.set(currentId, parentId)
        orderedNodeIds.push(currentId)
      }

      const children = Array.isArray(node.children)
        ? (node.children as Array<Record<string, unknown>>)
        : []

      if (children.length > 0) {
        walkNodes(children, currentId > 0 ? currentId : parentId)
      }
    })
  }

  walkNodes(nodes)

  const selectedIdSet = new Set<number>()

  selectedIds
    .map((item) => Number(item))
    .filter((item) => item > 0)
    .forEach((currentId) => {
      let cursor: number | null | undefined = currentId

      while (cursor && cursor > 0) {
        if (selectedIdSet.has(cursor)) {
          break
        }

        selectedIdSet.add(cursor)
        cursor = parentMap.get(cursor) ?? null
      }
    })

  return orderedNodeIds.filter((item) => selectedIdSet.has(item))
}
