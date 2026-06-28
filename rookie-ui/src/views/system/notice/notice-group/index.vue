/**
 * 文件作用：
 * 承接通知分组管理页面，
 * 负责分组列表查询、分页展示、新增、编辑、删除，以及分组成员管理弹窗。
 * 关键状态：
 * - `currentMembers`：当前管理成员的分组已有成员列表，供成员管理弹窗初始化已选集合与差集计算。
 * - `pageState` / `dialogVisible` / `memberManageGroupId`：列表分页、编辑弹窗、成员管理弹窗状态。
 * 关键依赖：
 * - 复用公共表格、公共表单、筛选面板，对齐字典管理页结构；
 * - 成员管理弹窗单独封装在 components/GroupMemberTransfer.vue（搜索 + 分页 + 标签区形态）。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSysNoticeGroupApi,
  deleteSysNoticeGroupsApi,
  getSysNoticeGroupDetailApi,
  getSysNoticeGroupPageApi,
  updateSysNoticeGroupApi,
} from '@/api/system/notice'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  SysNoticeGroupListQuery,
  SysNoticeGroupMemberRecord,
  SysNoticeGroupPageResult,
  SysNoticeGroupRecord,
} from '@/types/api/system/notice'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultNoticeGroupForm,
  createDefaultNoticeGroupQuery,
  createNoticeGroupQuerySchema,
  createNoticeGroupSchema,
  noticeGroupFormRules,
  type NoticeGroupQueryFormState,
} from './config'
import GroupMemberTransfer from './components/GroupMemberTransfer.vue'

type NoticeGroupDialogMode = 'create' | 'edit'

const queryForm = reactive<NoticeGroupQueryFormState>(createDefaultNoticeGroupQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<NoticeGroupDialogMode>('create')
const formModel = ref<SysNoticeGroupRecord>(createDefaultNoticeGroupForm())
const pageState = ref<SysNoticeGroupPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

// 成员管理弹窗状态：当前操作的分组主键与已有成员列表
const memberTransferRef = ref<InstanceType<typeof GroupMemberTransfer>>()
const currentGroupId = ref(0)
const currentMembers = ref<SysNoticeGroupMemberRecord[]>([])

const querySchema = computed<SharedFieldSchemaMap<NoticeGroupQueryFormState>>(() =>
  createNoticeGroupQuerySchema(),
)
const tableSchema = computed<SharedFieldSchemaMap<SysNoticeGroupRecord>>(() =>
  createNoticeGroupSchema(),
)
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增分组' : '编辑分组'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建分组' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.noticeGroup.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.groupId))
    },
  },
  {
    key: 'member',
    label: '管理成员',
    permKey: SYSTEM_PERMISSION_KEYS.noticeGroup.member,
    buttonType: 'primary',
    onClick: async (row) => {
      await openMemberManage(Number(row.groupId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.noticeGroup.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteGroup(Number(row.groupId))
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
  queryForm.groupName = String(nextValue.groupName ?? '')
  queryForm.groupCode = String(nextValue.groupCode ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
}

const buildListParams = (): SysNoticeGroupListQuery => ({
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  groupName: queryForm.groupName.trim() || undefined,
  groupCode: queryForm.groupCode.trim() || undefined,
  status: queryForm.status,
})

/**
 * 方法效果：
 * 拉取分组分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysNoticeGroupPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行分组查询，并从第一页重新拉取列表。
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
 * 重置分组查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultNoticeGroupQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增分组弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultNoticeGroupForm()
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑分组弹窗，并先拉取详情用于完整回显。
 * 参数：
 * - `groupId`：待编辑分组主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (groupId: number) => {
  const result = await getSysNoticeGroupDetailApi(groupId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultNoticeGroupForm(),
    ...result.data,
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开成员管理弹窗，先拉取分组详情拿到当前成员列表再交给弹窗初始化已选集合。
 * 用户候选数据由弹窗内部按搜索条件分页拉取，不再预拉全量用户。
 * 参数：
 * - `groupId`：待管理成员的分组主键。
 * 返回值：
 * - 无返回值；副作用是更新当前成员列表并打开成员管理弹窗。
 */
const openMemberManage = async (groupId: number) => {
  const result = await getSysNoticeGroupDetailApi(groupId)

  currentGroupId.value = groupId
  currentMembers.value = Array.isArray(result.data.members) ? result.data.members : []

  memberTransferRef.value?.open()
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
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新分组列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑分组表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: SysNoticeGroupRecord = {
      ...formModel.value,
      groupName: formModel.value.groupName.trim(),
      groupCode: formModel.value.groupCode.trim(),
      groupDesc: formModel.value.groupDesc?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createSysNoticeGroupApi(payload)
      ElMessage.success('分组创建成功')
    } else {
      await updateSysNoticeGroupApi(payload)
      ElMessage.success('分组更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 成员管理弹窗保存成功后刷新列表，让成员数量列及时更新。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新分组列表。
 */
const handleMemberSaved = async () => {
  await fetchPage()
}

/**
 * 方法效果：
 * 删除指定分组，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `groupId`：待删除分组主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteGroup = async (groupId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除分组', {
    type: 'warning',
  })

  await deleteSysNoticeGroupsApi([groupId])
  ElMessage.success('分组删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  // 列表初次加载只拉分组，用户候选数据延迟到打开成员管理弹窗时按需分页拉取
  await fetchPage()
})
</script>

<template>
  <section class="system-notice-group-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="72px"
        create-button-text="新增分组"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.noticeGroup.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="通知分组列表">
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
        :form-columns="2"
        :form-rules="noticeGroupFormRules"
        row-key="groupId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      />
    </BaseCard>

    <!-- 分组成员管理弹窗（搜索 + 分页 + 标签区） -->
    <GroupMemberTransfer
      ref="memberTransferRef"
      :group-id="currentGroupId"
      :members="currentMembers"
      @success="handleMemberSaved"
    />
  </section>
</template>

<style scoped>
.system-notice-group-view {
  display: grid;
  gap: 18px;
}
</style>
