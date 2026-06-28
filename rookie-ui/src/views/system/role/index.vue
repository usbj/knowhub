<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeSysRoleStatusApi,
  createSysRoleApi,
  deleteSysRolesApi,
  getSysRoleDetailApi,
  getSysRolePageApi,
  setDefaultSysRoleApi,
  updateSysRoleApi,
} from '@/api/system/role'
import { getSysMenuListApi } from '@/api/system/menu'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysRoleListQuery, SysRolePageResult, SysRoleRecord } from '@/types/api/system/role'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  buildPermissionTree,
  collectPermissionIdsWithAncestors,
  createDefaultRoleForm,
  createDefaultRoleQuery,
  createRoleQuerySchema,
  createRoleSchema,
  roleFormRules,
  type RoleQueryFormState,
} from './config'
import RolePermissionTreeField from './components/RolePermissionTreeField.vue'

type RoleDialogMode = 'create' | 'edit'

const queryForm = reactive<RoleQueryFormState>(createDefaultRoleQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<RoleDialogMode>('create')
const roleFormModel = ref<SysRoleRecord>(createDefaultRoleForm())
const permissionTree = ref<Array<Record<string, unknown>>>([])
const pageState = ref<SysRolePageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<RoleQueryFormState>>(() => createRoleQuerySchema())
const roleSchema = computed<SharedFieldSchemaMap<SysRoleRecord>>(() => createRoleSchema())
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增角色' : '编辑角色'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建角色' : '保存修改'))
const currentRows = computed(() => pageState.value.records)
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: currentRows.value,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.role.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.roleId))
    },
  },
  {
    key: 'default',
    label: '设为默认',
    permKey: SYSTEM_PERMISSION_KEYS.role.setDefault,
    buttonType: 'success',
    visible: (row) => Number(row.isDefault) !== 1,
    onClick: async (row) => {
      await handleSetDefaultRole(Number(row.roleId))
    },
  },
  {
    key: 'enable',
    label: '启用',
    permKey: SYSTEM_PERMISSION_KEYS.role.status,
    buttonType: 'success',
    visible: (row) => Number(row.status) !== 1,
    onClick: async (row) => {
      await handleChangeStatus(Number(row.roleId), 1)
    },
  },
  {
    key: 'disable',
    label: '停用',
    permKey: SYSTEM_PERMISSION_KEYS.role.status,
    buttonType: 'warning',
    visible: (row) => Number(row.status) === 1,
    onClick: async (row) => {
      await handleChangeStatus(Number(row.roleId), 0)
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.role.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteRole(Number(row.roleId))
    },
  },
])

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.roleName = String(nextValue.roleName ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildRoleListParams = (): SysRoleListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    roleName: queryForm.roleName.trim() || undefined,
    status: queryForm.status,
    beginTime,
    endTime,
  }
}

const fetchRolePage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysRolePageApi(buildRoleListParams())
  } finally {
    listLoading.value = false
  }
}

const fetchPermissionOptions = async () => {
  const result = await getSysMenuListApi()
  permissionTree.value = buildPermissionTree(result.data)
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchRolePage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultRoleQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchRolePage()
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  roleFormModel.value = createDefaultRoleForm()
  dialogVisible.value = true
}

const openEditDialog = async (roleId: number) => {
  const result = await getSysRoleDetailApi(roleId)

  dialogMode.value = 'edit'
  roleFormModel.value = {
    ...createDefaultRoleForm(),
    ...result.data,
    permId: result.data.permId ?? [],
    rolePerm: result.data.rolePerm ?? [],
  }
  dialogVisible.value = true
}

const handleRoleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  roleFormModel.value = {
    ...roleFormModel.value,
    ...nextValue,
    roleLevel: Number(nextValue.roleLevel ?? roleFormModel.value.roleLevel),
    permId: Array.isArray(nextValue.permId) ? (nextValue.permId as number[]) : roleFormModel.value.permId,
  }
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchRolePage()
}

const handleSubmitRoleForm = async () => {
  submitLoading.value = true

  try {
    const normalizedPermissionIds = collectPermissionIdsWithAncestors(
      permissionTree.value,
      roleFormModel.value.permId.map((item) => Number(item)),
    )

    const payload: SysRoleRecord = {
      ...roleFormModel.value,
      roleName: roleFormModel.value.roleName.trim(),
      roleKey: roleFormModel.value.roleKey.trim(),
      permId: normalizedPermissionIds,
    }

    if (dialogMode.value === 'create') {
      await createSysRoleApi(payload)
      ElMessage.success('角色创建成功')
    } else {
      await updateSysRoleApi(payload)
      ElMessage.success('角色更新成功')
    }

    dialogVisible.value = false
    await fetchRolePage()
  } finally {
    submitLoading.value = false
  }
}

const handleChangeStatus = async (roleId: number, status: number) => {
  await changeSysRoleStatusApi(roleId, status)
  ElMessage.success(status === 1 ? '角色已启用' : '角色已停用')
  await fetchRolePage()
}

const handleSetDefaultRole = async (roleId: number) => {
  await setDefaultSysRoleApi(roleId)
  ElMessage.success('默认角色设置成功')
  await fetchRolePage()
}

const handleDeleteRole = async (roleId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除角色', {
    type: 'warning',
  })

  await deleteSysRolesApi([roleId])
  ElMessage.success('角色删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchRolePage()
}

onMounted(async () => {
  await Promise.all([fetchPermissionOptions(), fetchRolePage()])
})
</script>

<template>
  <section class="system-role-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增角色"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.role.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="角色列表">
      <SharedTablePanel
        :rows="currentRows as Record<string, unknown>[]"
        :schema="roleSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="true"
        :pagination="tablePagination"
        :table-max-height="560"
        row-key="roleId"
        :form-visible="dialogVisible"
        :form-model-value="roleFormModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="2"
        :form-rules="roleFormRules"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleRoleFormModelUpdate"
        @form-submit="handleSubmitRoleForm"
        @form-cancel="dialogVisible = false"
      >
        <template #field-permId="{ modelValue, updateFieldValue }">
          <RolePermissionTreeField
            :model-value="Array.isArray(modelValue) ? (modelValue as number[]) : []"
            :options="permissionTree"
            @update:model-value="updateFieldValue"
          />
        </template>
      </SharedTablePanel>
    </BaseCard>
  </section>
</template>

<style scoped>
.system-role-view {
  display: grid;
  gap: 18px;
}
</style>
