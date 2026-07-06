/**
 * 文件作用：
 * 承接博客模块下的标签管理页面，
 * 负责受控标签列表查询、新增、编辑、删除。
 * 关键状态：
 * - `tagList`：当前标签全量列表（标签不分页，后端返回 List）。
 * - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
 * - `dialogVisible`：编辑弹窗显隐状态。
 * 关键依赖：
 * - 复用公共表格、公共表单、筛选面板，对齐字典管理页结构。
 * - 标签列表不分页：不传 pagination 给 SharedTablePanel，组件默认 total=0 不渲染分页条。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTagApi,
  deleteTagsApi,
  getTagDetailApi,
  getTagListApi,
  updateTagApi,
} from '@/api/knowhub/tag'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { TagRecord } from '@/types/api/knowhub/tag'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultTagForm,
  createDefaultTagQuery,
  createTagQuerySchema,
  createTagSchema,
  tagFormRules,
  type TagQueryFormState,
} from './config'

type TagDialogMode = 'create' | 'edit'

/** 标签全量列表（后端不分页，直接持有全量数组供表格渲染） */
const tagList = ref<TagRecord[]>([])
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<TagDialogMode>('create')
const formModel = ref<TagRecord>(createDefaultTagForm())

const queryForm = reactive<TagQueryFormState>(createDefaultTagQuery())

const querySchema = computed<SharedFieldSchemaMap<TagQueryFormState>>(() => createTagQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<TagRecord>>(() => createTagSchema())
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增标签' : '编辑标签'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建标签' : '保存修改'))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.tag.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.tagId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.tag.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteTag(Number(row.tagId))
    },
  },
])

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.tagName = String(nextValue.tagName ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
}

/**
 * 方法效果：
 * 拉取标签全量列表（后端不分页），并更新当前表格数据。
 * 参数：
 * - 无，直接使用当前页的查询条件。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据。
 */
const fetchTagList = async () => {
  listLoading.value = true

  try {
    const result = await getTagListApi({
      tagName: queryForm.tagName.trim() || undefined,
      status: queryForm.status,
    })
    tagList.value = result.data ?? []
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行标签查询，重新拉取全量列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearch = async () => {
  await fetchTagList()
}

/**
 * 方法效果：
 * 重置标签查询条件，并重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultTagQuery())
  await fetchTagList()
}

/**
 * 方法效果：
 * 打开新增标签弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultTagForm()
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑标签弹窗，并先拉取详情用于完整回显。
 * 参数：
 * - `tagId`：待编辑标签主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (tagId: number) => {
  const result = await getTagDetailApi(tagId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultTagForm(),
    ...result.data,
    status: Number(result.data.status ?? 1),
    sort: Number(result.data.sort ?? 0),
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    status: Number(nextValue.status ?? formModel.value.status),
    sort: Number(nextValue.sort ?? formModel.value.sort ?? 0),
  }
}

/**
 * 方法效果：
 * 提交新增或编辑标签表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: TagRecord = {
      ...formModel.value,
      tagName: formModel.value.tagName.trim(),
      description: formModel.value.description?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createTagApi(payload)
      ElMessage.success('标签创建成功')
    } else {
      await updateTagApi(payload)
      ElMessage.success('标签更新成功')
    }

    dialogVisible.value = false
    await fetchTagList()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除指定标签，软删并级联清理 blog_tag 关联。
 * 参数：
 * - `tagId`：待删除标签主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteTag = async (tagId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除标签', {
    type: 'warning',
  })

  await deleteTagsApi([tagId])
  ElMessage.success('标签删除成功')
  await fetchTagList()
}

onMounted(async () => {
  await fetchTagList()
})
</script>

<template>
  <section class="blog-tag-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增标签"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.tag.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="标签列表">
      <!-- 公共表格区域：标签不分页，不传 pagination，组件默认 total=0 不渲染分页条 -->
      <SharedTablePanel
        :rows="tagList as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="false"
        :table-max-height="560"
        :form-visible="dialogVisible"
        :form-model-value="formModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="2"
        :form-rules="tagFormRules"
        row-key="tagId"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.blog-tag-view {
  display: grid;
  gap: 18px;
}
</style>
