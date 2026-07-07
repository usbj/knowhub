<!--
  文件作用：
  承接资源分类管理页面（独立菜单"资源分类"），用 ElTable 树表格展示分类树，
  支持新增顶级分类/新增子分类/编辑/删除，并约定 -1=其他（前端硬编码虚拟节点，不入树）。
  关键状态：
  - `treeData`：分类树数据（由 getResourceCategoryTreeApi 拉取，后端按 parent_id 自关联组树）。
  - `dialogVisible` / `dialogMode` / `formModel`：分类弹窗表单状态。
  - `treeLoading` / `submitLoading`：树加载与表单提交中态。
  - `expandAll` / `tableRef`：批量展开/折叠控制（对齐菜单管理页风格）。
  关键交互：
  - 树表格列：分类名（带层级缩进）/ 状态 / 排序 / 操作（新增子分类 / 编辑 / 删除）。
  - 顶部工具栏：展开全部/折叠全部 + 新增顶级分类。
  - 删除分类：有子分类拒绝（后端校验），无子分类则后端把挂载资源置 -1 后软删。
  - 状态切换走编辑接口（后端无独立状态切换接口，复用 PUT /resource-category）。
  设计约定：
  - 复用 BaseCard 包裹对齐其他管理页；表格/弹窗用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
  - 权限用 usePermission().hasPermission（对齐菜单页），非手写 SYSTEM_PERMISSION_KEYS 长度判。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createResourceCategoryApi,
  deleteResourceCategoryApi,
  getResourceCategoryTreeApi,
  updateResourceCategoryApi,
} from '@/api/knowhub/resource-category'
import BaseCard from '@/components/BaseCard.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { usePermission } from '@/composables/usePermission'
import type { ResourceCategoryRecord, ResourceCategoryTreeNode } from '@/types/api/knowhub/resource'

type CategoryDialogMode = 'create' | 'create-child' | 'edit'

/** 分类状态静态选项，0 禁用 / 1 启用，与后端 resource_category.status 列口径一致 */
const categoryStatusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const treeData = ref<ResourceCategoryTreeNode[]>([])
const treeLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<CategoryDialogMode>('create')
const formModel = ref<ResourceCategoryRecord>({
  categoryName: '',
  parentId: 0,
  sort: 0,
  status: 1,
})
/** 当前操作的父节点（新增子分类时用） */
const currentParent = ref<ResourceCategoryTreeNode | null>(null)
const formRef = ref<FormInstance>()
const tableRef = ref<InstanceType<typeof ElTable>>()
const expandAll = ref(false)
const { hasPermission } = usePermission()

const dialogTitle = computed(() => {
  if (dialogMode.value === 'create') return '新增顶级分类'
  if (dialogMode.value === 'create-child') return `新增子分类（父级：${currentParent.value?.categoryName ?? ''}）`
  return '编辑分类'
})
const dialogSubmitText = computed(() => (dialogMode.value === 'edit' ? '保存修改' : '创建分类'))

const canCreate = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.resourceCategory.create))
const canEdit = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.resourceCategory.edit))
const canDelete = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.resourceCategory.delete))

const formRules: FormRules = {
  categoryName: [
    { required: true, message: '请输入分类名', trigger: 'blur' },
    { min: 1, max: 64, message: '分类名长度需在 1 到 64 位之间', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

/**
 * 方法效果：
 * 拉取资源分类树并更新 treeData。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新树数据。
 */
const fetchTree = async () => {
  treeLoading.value = true
  try {
    const result = await getResourceCategoryTreeApi()
    treeData.value = result.data ?? []
  } finally {
    treeLoading.value = false
  }
}

/**
 * 方法效果：
 * 打开新增顶级分类弹窗。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重置表单并展示弹窗。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = { categoryName: '', parentId: 0, sort: 0, status: 1 }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开新增子分类弹窗（在指定父节点下新增）。
 * 参数：
 * - `parent`：父节点数据。
 * 返回值：
 * - 无返回值；副作用是重置表单并展示弹窗。
 */
const openCreateChildDialog = (parent: ResourceCategoryTreeNode) => {
  dialogMode.value = 'create-child'
  currentParent.value = parent
  formModel.value = { categoryName: '', parentId: parent.categoryId, sort: 0, status: 1 }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑分类弹窗，回显当前节点数据。
 * 参数：
 * - `node`：待编辑节点数据。
 * 返回值：
 * - 无返回值；副作用是填充表单并展示弹窗。
 */
const openEditDialog = (node: ResourceCategoryTreeNode) => {
  dialogMode.value = 'edit'
  formModel.value = {
    categoryId: node.categoryId,
    categoryName: node.categoryName,
    parentId: node.parentId,
    sort: node.sort,
    status: node.status,
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 提交分类表单（新增顶级/新增子分类/编辑），成功后关闭弹窗并刷新树。
 * 参数：
 * - 无，直接读取当前表单模型与弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调保存接口、关弹窗、刷新树。
 */
const handleSubmitForm = async () => {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitLoading.value = true
  try {
    const payload: ResourceCategoryRecord = {
      ...formModel.value,
      categoryName: formModel.value.categoryName.trim(),
      status: Number(formModel.value.status ?? 1),
      sort: Number(formModel.value.sort ?? 0),
    }
    if (dialogMode.value === 'edit') {
      await updateResourceCategoryApi(payload)
      ElMessage.success('分类更新成功')
    } else {
      await createResourceCategoryApi(payload)
      ElMessage.success('分类创建成功')
    }
    dialogVisible.value = false
    await fetchTree()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除分类。有子分类后端会拒绝；无子分类后端把挂载资源置 -1 后软删。
 * 参数：
 * - `node`：待删除节点数据。
 * 返回值：
 * - 无返回值；副作用是调删除接口并刷新树。
 */
const handleDelete = async (node: ResourceCategoryTreeNode) => {
  await ElMessageBox.confirm(
    `确认删除分类「${node.categoryName}」吗？若该分类下有资源，将自动归入"其他"。`,
    '删除分类',
    { type: 'warning' },
  )
  try {
    await deleteResourceCategoryApi(node.categoryId!)
    ElMessage.success('分类删除成功')
    await fetchTree()
  } catch {
    // http.ts 已统一弹错（如"有子分类请先处理"）
  }
}

/**
 * 方法效果：
 * 统一切换分类树的展开和折叠状态（对齐菜单管理页）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是批量切换树表格的展开状态。
 */
const toggleExpandAll = () => {
  expandAll.value = !expandAll.value
  const walkNodes = (nodes: ResourceCategoryTreeNode[]) => {
    nodes.forEach((node) => {
      tableRef.value?.toggleRowExpansion(node, expandAll.value)
      if (node.children?.length) {
        walkNodes(node.children)
      }
    })
  }
  walkNodes(treeData.value)
}

/**
 * 方法效果：
 * 取分类状态标签的 Element Plus 类型（启用=primary，禁用=info），供表格状态列渲染。
 * 参数：
 * - `status`：状态值（0/1）。
 * 返回值：
 * - ElTag 的 type 值。
 */
const getStatusTagType = (status: number | undefined) => (Number(status) === 1 ? 'primary' : 'info')

/**
 * 方法效果：
 * 生成当前行可展示的操作按钮，避免模板里过长的条件判断。
 * 参数：
 * - `row`：当前分类行数据。
 * 返回值：
 * - 当前行可执行的操作按钮数组（新增子分类 / 编辑 / 删除，按权限过滤）。
 */
const getRowActions = (row: ResourceCategoryTreeNode) => {
  const actions: Array<{ key: string; label: string; type?: 'primary' | 'danger'; onClick: () => void }> = []
  if (canCreate.value) {
    actions.push({
      key: 'create-child',
      label: '新增',
      type: 'primary',
      onClick: () => openCreateChildDialog(row),
    })
  }
  if (canEdit.value) {
    actions.push({
      key: 'edit',
      label: '编辑',
      type: 'primary',
      onClick: () => openEditDialog(row),
    })
  }
  if (canDelete.value) {
    actions.push({
      key: 'delete',
      label: '删除',
      type: 'danger',
      onClick: () => handleDelete(row),
    })
  }
  return actions
}

onMounted(async () => {
  await fetchTree()
})
</script>

<template>
  <section class="resource-category-view">
    <BaseCard title="资源分类管理">
      <div class="resource-category-view__toolbar">
        <ElButton plain @click="toggleExpandAll">{{ expandAll ? '折叠全部' : '展开全部' }}</ElButton>
        <ElButton
          v-if="canCreate"
          type="primary"
          @click="openCreateDialog"
        >
          新增顶级分类
        </ElButton>
      </div>

      <ElTable
        ref="tableRef"
        v-loading="treeLoading"
        class="resource-category-view__table"
        :data="treeData"
        row-key="categoryId"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <ElTableColumn prop="categoryName" label="分类名称" min-width="260" show-overflow-tooltip />
        <ElTableColumn prop="sort" label="排序" width="100" align="center" />
        <ElTableColumn prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <ElTag size="small" effect="plain" :type="getStatusTagType(row.status)">
              {{ Number(row.status) === 1 ? '启用' : '禁用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <div class="resource-category-view__actions">
              <ElButton
                v-for="action in getRowActions(row)"
                :key="action.key"
                text
                :type="action.type || 'primary'"
                @click="action.onClick()"
              >
                {{ action.label }}
              </ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </BaseCard>

    <!-- 分类新增/编辑弹窗 -->
    <ElDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <ElForm ref="formRef" :model="formModel" :rules="formRules" label-width="80px">
        <ElFormItem label="分类名" prop="categoryName">
          <ElInput v-model="formModel.categoryName" placeholder="请输入分类名" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="排序" prop="sort">
          <ElInput v-model.number="formModel.sort" type="number" placeholder="同级内asc排序，默认0" />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="formModel.status" placeholder="请选择状态">
            <ElOption
              v-for="item in categoryStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <div class="resource-category-view__dialog-footer">
          <ElButton @click="dialogVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="submitLoading" @click="handleSubmitForm">
            {{ dialogSubmitText }}
          </ElButton>
        </div>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.resource-category-view {
  display: grid;
  gap: 18px;
}

.resource-category-view__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.resource-category-view__table :deep(.el-table__cell) {
  vertical-align: middle;
}

.resource-category-view__actions {
  display: flex;
  align-items: center;
  gap: 0 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.resource-category-view__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.resource-category-view__dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.resource-category-view__dialog-footer :deep(.el-select),
.resource-category-view__dialog-footer :deep(.el-select__wrapper),
.resource-category-view__dialog-footer :deep(.el-input),
.resource-category-view__dialog-footer :deep(.el-input__wrapper) {
  width: 100%;
}
</style>
