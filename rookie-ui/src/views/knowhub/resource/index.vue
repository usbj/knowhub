<!--
  文件作用：
  承接资源管理页面（后台菜单名"资源管理"），负责资源列表查询、分页展示、新增、编辑、
  发布、撤回、审核、删除、详情查看，以及互动（点赞/收藏/评分）与下载入口。
  关键状态：
  - `categoryTree`：资源分类树（含"其他"虚拟节点 -1），供筛选区与表单 ElTreeSelect 复用。
  - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
  - `pageState` / `dialogVisible` / `detailVisible` / `reviewVisible`：分页、编辑弹窗、详情弹窗、审核弹窗状态。
  关键依赖：
  - 复用公共表格、公共表单、筛选面板，对齐博客管理页结构。
  - resourceCategoryId 字段用 #field-resourceCategoryId 插槽接管为 ElTreeSelect（分类树 + "其他"）。
  - fileObjectId / linkUrl 字段用 #field-* 插槽接管，按 resourceType 切换显示（FILE 上传 / LINK 输 URL）。
  - 互动按钮（点赞/收藏/评分）在详情弹窗内，列表不展示；下载在详情弹窗内。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTreeSelect } from 'element-plus'
import {
  createResourceApi,
  deleteResourcesApi,
  getResourceDetailApi,
  getResourcePageApi,
  publishResourceApi,
  reviewResourceApi,
  revokeResourceApi,
  updateResourceApi,
} from '@/api/knowhub/resource'
import { getResourceCategoryTreeApi } from '@/api/knowhub/resource-category'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import ResourceDetailDialog from './components/ResourceDetailDialog.vue'
import ResourceReviewDialog from './components/ResourceReviewDialog.vue'
import ResourceFileUploader from './components/ResourceFileUploader.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { useUserStore } from '@/stores/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  ResourceListQuery,
  ResourcePageResult,
  ResourceRecord,
  ResourceReviewPayload,
  ResourceCategoryTreeNode,
} from '@/types/api/knowhub/resource'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultResourceForm,
  createDefaultResourceQuery,
  createResourceQuerySchema,
  createResourceSchema,
  buildResourceFormRules,
  CATEGORY_OTHER_VALUE,
  type ResourceQueryFormState,
} from './config'

type ResourceDialogMode = 'create' | 'edit'

// 审核员回避前端对齐：自己不能审自己提交的资源（后端 reviewResource 强判 authorId==userId 拒，
// 前端按钮显隐先挡避免点了报错；admin 亦回避——admin 自审自同样隐藏审核按钮）
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.userId ?? -1)

const queryForm = reactive<ResourceQueryFormState>(createDefaultResourceQuery())
const categoryTree = ref<ResourceCategoryTreeNode[]>([])
/** 表单内上传文件的原始名（编辑回显 + 上传回传） */
const formOriginalName = ref('')
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<ResourceDialogMode>('create')
const formModel = ref<ResourceRecord>(createDefaultResourceForm())
const detailVisible = ref(false)
const detailRecord = ref<ResourceRecord | null>(null)
const reviewVisible = ref(false)
const reviewResourceId = ref<number | null>(null)
/** 待审核资源详情（审核弹窗展示标题/文件或链接/说明用） */
const reviewResource = ref<ResourceRecord | null>(null)
const reviewLoading = ref(false)
const pageState = ref<ResourcePageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<ResourceQueryFormState>>(() => createResourceQuerySchema())
/**
 * 表格列与表单字段 schema：按当前表单 resourceType 动态切换 fileObjectId / linkUrl 的 formVisible。
 * - FILE 类型：fileObjectId 可见（custom 插槽上传组件），linkUrl 隐藏
 * - LINK 类型：linkUrl 可见（text 输入），fileObjectId 隐藏
 * 这样在弹窗内切换类型时，载体字段跟着切换显示，无需 v-if 重建表单。
 * 注意：不用 ...base 展开再覆盖（会把 label 等必填字段类型变 optional 致 ts 报错），
 * 改为直接修改 base 副本对应项的 formVisible，保持 SharedFieldSchemaItem 完整类型。
 */
const tableSchema = computed<SharedFieldSchemaMap<ResourceRecord>>(() => {
  const base = createResourceSchema()
  const isFile = formModel.value.resourceType === 'FILE'
  if (base.fileObjectId) {
    base.fileObjectId.formVisible = isFile
  }
  if (base.linkUrl) {
    base.linkUrl.formVisible = !isFile
  }
  return base
})
/**
 * 弹窗表单校验规则：按当前 resourceType 动态构建。
 * - 详细说明始终必填（必须描述资源内容）；
 * - FILE 类型 fileObjectId 必传（已上传文件）、LINK 类型 linkUrl 必填且合法 URL。
 * 切换类型时规则跟着重建，校验红字与当前载体字段一致。
 */
const resourceFormRules = computed(() => buildResourceFormRules(formModel.value.resourceType))
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增资源' : '编辑资源'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建资源' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

/** ElTreeSelect 节点类型（value 作 node-key，label 显示，children 递归） */
interface TreeSelectNode {
  value: number
  label: string
  children?: TreeSelectNode[]
}

/**
 * 方法效果：
 * 把分类树扁平化为 ElTreeSelect 可用的 props.data 格式（节点带 value/label/children）。
 * 并在根级追加"其他"虚拟节点（value=-1），与后端 -1=其他 约定对齐。
 * 参数：
 * - `tree`：后端返回的分类树节点列表。
 * 返回值：
 * - ElTreeSelect 的 data 格式数组。
 */
const treeSelectData = computed<TreeSelectNode[]>(() => {
  const transform = (nodes: ResourceCategoryTreeNode[]): TreeSelectNode[] =>
    nodes.map((node) => ({
      value: node.categoryId ?? 0,
      label: node.categoryName,
      children: node.children && node.children.length ? transform(node.children) : undefined,
    }))
  return [
    ...transform(categoryTree.value),
    { value: CATEGORY_OTHER_VALUE, label: '其他' },
  ]
})

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.resource.edit,
    buttonType: 'primary',
    // 仅 DRAFT/REJECTED/REVOKED 可编辑；PUBLISHED 须先撤回、PENDING_REVIEW 审核中不能改
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      await openEditDialog(Number(row.resourceId))
    },
  },
  {
    key: 'publish',
    label: '发布',
    permKey: SYSTEM_PERMISSION_KEYS.resource.publish,
    buttonType: 'success',
    // 仅非发布且非待审状态可发布；PUBLISHED 无需重复发布、PENDING_REVIEW 已在审不可重复提交
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      await handlePublishResource(Number(row.resourceId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.resource.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PUBLISHED',
    onClick: async (row) => {
      await handleRevokeResource(Number(row.resourceId))
    },
  },
  {
    key: 'review',
    label: '审核',
    permKey: SYSTEM_PERMISSION_KEYS.resource.review,
    buttonType: 'primary',
    // 仅 PENDING_REVIEW 可审；且不能审自己提交的（authorId==当前用户则隐藏，admin 亦回避）
    visible: (row) => String(row.status) === 'PENDING_REVIEW' && Number(row.authorId) !== currentUserId.value,
    onClick: async (row) => {
      await openReviewDialog(Number(row.resourceId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.resourceId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.resource.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteResource(Number(row.resourceId))
    },
  },
])

/**
 * 方法效果：
 * 拉取资源分类树，供筛选区与弹窗内 resourceCategoryId 的 ElTreeSelect 复用。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新分类树数据源。
 */
const fetchCategoryTree = async () => {
  const result = await getResourceCategoryTreeApi()
  categoryTree.value = result.data ?? []
}

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.title = String(nextValue.title ?? '')
  queryForm.resourceType = nextValue.resourceType ? String(nextValue.resourceType) : undefined
  queryForm.resourceCategoryId =
    nextValue.resourceCategoryId != null && nextValue.resourceCategoryId !== ''
      ? Number(nextValue.resourceCategoryId)
      : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.createBy = String(nextValue.createBy ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): ResourceListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    resourceType: queryForm.resourceType,
    resourceCategoryId: queryForm.resourceCategoryId,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    createBy: queryForm.createBy.trim() || undefined,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取资源分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getResourcePageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行资源查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

/**
 * 方法效果：
 * 重置资源查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultResourceQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增资源弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultResourceForm()
  formOriginalName.value = ''
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑资源弹窗，并先拉取详情用于完整回显（含文件名/分类等）。
 * 参数：
 * - `resourceId`：待编辑资源主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (resourceId: number) => {
  const result = await getResourceDetailApi(resourceId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultResourceForm(),
    ...result.data,
  }
  formOriginalName.value = result.data.originalName || ''
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开只读详情弹窗，拉取资源详情后展示说明、文件/链接、互动计数、审核历史。
 * 参数：
 * - `resourceId`：待查看资源主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (resourceId: number) => {
  const result = await getResourceDetailApi(resourceId)

  detailRecord.value = result.data
  detailVisible.value = true
}

/**
 * 方法效果：
 * 打开审核弹窗，先拉取资源详情（标题/文件或链接/说明）供审核员参考决策。
 * 参数：
 * - `resourceId`：待审核资源主键。
 * 返回值：
 * - 无返回值；副作用是更新审核资源详情并展示弹窗。
 */
const openReviewDialog = async (resourceId: number) => {
  reviewLoading.value = true
  try {
    const result = await getResourceDetailApi(resourceId)
    reviewResource.value = result.data
  } finally {
    reviewLoading.value = false
  }
  reviewResourceId.value = resourceId
  reviewVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 注意：fileObjectId/linkUrl 由 #field-* 插槽自管，此处保留旧值避免被表单通用回传覆盖。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    // fileObjectId / linkUrl 由插槽自管 updateFieldValue，表单通用回传可能丢值，保留旧值兜底
    fileObjectId:
      nextValue.fileObjectId != null
        ? Number(nextValue.fileObjectId)
        : formModel.value.fileObjectId,
    linkUrl:
      nextValue.linkUrl != null
        ? String(nextValue.linkUrl)
        : formModel.value.linkUrl,
    resourceCategoryId:
      nextValue.resourceCategoryId != null
        ? Number(nextValue.resourceCategoryId)
        : formModel.value.resourceCategoryId,
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新资源列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑资源表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: ResourceRecord = {
      ...formModel.value,
      title: formModel.value.title.trim(),
      summary: formModel.value.summary?.trim() || '',
      description: formModel.value.description?.trim() || '',
      // resourceCategoryId 缺省置 -1（其他）
      resourceCategoryId: formModel.value.resourceCategoryId ?? CATEGORY_OTHER_VALUE,
    }

    if (dialogMode.value === 'create') {
      await createResourceApi(payload)
      ElMessage.success('资源创建成功')
    } else {
      await updateResourceApi(payload)
      ElMessage.success('资源更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 发布指定资源。受全局审核开关控制：开关关→直接发布；开关开→进入待审核。
 * 参数：
 * - `resourceId`：待发布资源主键。
 * 返回值：
 * - 无返回值；副作用是调用发布接口并刷新列表。
 */
const handlePublishResource = async (resourceId: number) => {
  await ElMessageBox.confirm('确认发布该资源吗？发布后对读者可见。', '发布资源', {
    type: 'warning',
  })

  await publishResourceApi(resourceId)
  ElMessage.success('资源发布成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 撤回已发布的资源，使其回到已撤回状态。
 * 参数：
 * - `resourceId`：待撤回资源主键。
 * 返回值：
 * - 无返回值；副作用是调用撤回接口并刷新列表。
 */
const handleRevokeResource = async (resourceId: number) => {
  await ElMessageBox.confirm('确认撤回该资源吗？撤回后不再对读者可见。', '撤回资源', {
    type: 'warning',
  })

  await revokeResourceApi(resourceId)
  ElMessage.success('资源撤回成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 接收审核弹窗的提交结果，调审核接口（通过/驳回）并刷新列表。
 * 参数：
 * - `payload`：审核入参（resourceId / pass / advice）。
 * 返回值：
 * - 无返回值；副作用是调用审核接口、关闭弹窗并刷新列表。
 */
const handleReviewSubmit = async (payload: ResourceReviewPayload) => {
  await reviewResourceApi(payload)
  ElMessage.success(payload.pass ? '审核通过，资源已发布' : '已驳回资源')
  reviewVisible.value = false
  await fetchPage()
}

/**
 * 方法效果：
 * 删除指定资源，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `resourceId`：待删除资源主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteResource = async (resourceId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除资源', {
    type: 'warning',
  })

  await deleteResourcesApi([resourceId])
  ElMessage.success('资源删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  await Promise.all([fetchCategoryTree(), fetchPage()])
})
</script>

<template>
  <section class="resource-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增资源"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.resource.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      >
        <!-- 筛选区分类树选择：custom 字段插槽接管为 ElTreeSelect -->
        <template #field-resourceCategoryId="{ modelValue, updateFieldValue }">
          <ElTreeSelect
            :model-value="modelValue != null && modelValue !== '' ? Number(modelValue) : undefined"
            :data="treeSelectData"
            node-key="value"
            :props="{ label: 'label', children: 'children' }"
            check-strictly
            clearable
            filterable
            placeholder="请选择分类"
            style="width: 100%"
            @update:model-value="updateFieldValue"
          />
        </template>
      </SearchFilterPanel>
    </BaseCard>

    <BaseCard title="资源列表">
      <!-- 公共表格区域 -->
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="dialogVisible"
        :form-model-value="formModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="1"
        :form-rules="resourceFormRules"
        row-key="resourceId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      >
        <!-- 表单内分类树选择：custom 字段插槽接管为 ElTreeSelect -->
        <template #field-resourceCategoryId="{ modelValue, updateFieldValue }">
          <ElTreeSelect
            :model-value="modelValue != null && modelValue !== '' ? Number(modelValue) : CATEGORY_OTHER_VALUE"
            :data="treeSelectData"
            node-key="value"
            :props="{ label: 'label', children: 'children' }"
            check-strictly
            clearable
            filterable
            placeholder="请选择分类"
            style="width: 100%"
            @update:model-value="updateFieldValue"
          />
        </template>

        <!-- 表单内文件上传（FILE 类型由 schema formVisible 控制显示）：custom 字段插槽接管为 ResourceFileUploader -->
        <template #field-fileObjectId="{ modelValue, updateFieldValue }">
          <ResourceFileUploader
            :model-value="modelValue != null ? Number(modelValue) : undefined"
            :original-name="formOriginalName"
            @update:model-value="updateFieldValue"
            @update:original-name="formOriginalName = $event"
          />
        </template>
      </SharedTablePanel>
    </BaseCard>

    <!-- 资源详情只读弹窗 -->
    <ResourceDetailDialog
      :visible="detailVisible"
      :resource="detailRecord"
      @update:visible="detailVisible = $event"
    />

    <!-- 资源审核弹窗（展示标题/文件或链接/说明供审核员参考） -->
    <ResourceReviewDialog
      :visible="reviewVisible"
      :resource-id="reviewResourceId"
      :resource="reviewResource"
      :loading="reviewLoading"
      @update:visible="reviewVisible = $event"
      @submit="handleReviewSubmit"
    />
  </section>
</template>

<style scoped>
.resource-view {
  display: grid;
  gap: 18px;
}
</style>
