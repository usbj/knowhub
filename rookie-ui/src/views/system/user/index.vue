<script setup lang="ts">
/**
 * 文件作用：
 * 承接系统模块下的用户管理页面，
 * 负责用户列表查询、分页展示、新增、编辑、状态切换与删除操作。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormRules } from 'element-plus'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import {
  changeSysUserStatusApi,
  createSysUserApi,
  deleteSysUsersApi,
  getSysUserDetailApi,
  getSysUserPageApi,
  updateSysUserApi,
} from '@/api/system/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  SysUserFormData,
  SysUserListQuery,
  SysUserPageResult,
} from '@/types/api/system/user'
import type {
  SharedActionConfig,
  SharedFieldSchemaMap,
} from '@/types/components/data-display'

interface UserQueryFormState {
  username: string
  nickName: string
  phoneNumber: string
  status: number | undefined
  dateRange: string[]
}

type UserDialogMode = 'create' | 'edit'

const mockRoleOptions = [
  { label: '系统管理员', value: 1 },
  { label: '普通角色', value: 2 },
  { label: '访客角色', value: 3 },
]

/**
 * 方法效果：
 * 创建空白的用户查询表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 用户查询表单默认值对象。
 */
const createDefaultQueryForm = (): UserQueryFormState => ({
  username: '',
  nickName: '',
  phoneNumber: '',
  status: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 创建空白的用户编辑表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 用户新增或编辑时使用的默认表单对象。
 */
const createDefaultUserForm = (): SysUserFormData => ({
  username: '',
  password: '',
  nickName: '',
  phoneNumber: '',
  sex: '1',
  status: 1,
  roleId: [],
  userRole: [],
})

const queryForm = reactive<UserQueryFormState>(createDefaultQueryForm())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<UserDialogMode>('create')
const userFormModel = ref<SysUserFormData>(createDefaultUserForm())
const pageState = ref<SysUserPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<UserQueryFormState>>(() => ({
  username: {
    label: '账号',
    inputType: 'text',
    placeholder: '请输入账号',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 5,
    props: {
      style: {
        width: '100%',
      },
    },
  },
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
    props: {
      style: {
        width: '100%',
      },
    },
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 5,
    props: {
      style: {
        width: '100%',
      },
    },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    props: {
      style: {
        width: '100%',
      },
    },
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
    formOrder: 5,
    span: 5,
    props: {
      unlinkPanels: true,
      style: {
        width: '100%',
      },
    },
  },
}))

const userSchema = computed<SharedFieldSchemaMap<SysUserFormData>>(() => ({
  username: {
    label: '账号',
    inputType: 'text',
    placeholder: '请输入账号',
    tableVisible: true,
    formVisible: dialogMode.value === 'create',
    tableOrder: 2,
    formOrder: 1,
    span: 12,
    tableMinWidth: 140,
  },
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
    tableMinWidth: 140,
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
    tableMinWidth: 160,
  },
  sex: {
    label: '性别',
    inputType: 'select',
    placeholder: '请选择性别',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 12,
    options: [
      { label: '男', value: '1' },
      { label: '女', value: '0' },
    ],
    formatter: (value) => (String(value) === '0' ? '女' : '男'),
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 5,
    span: 12,
    options: [
      { label: '正常', value: 1 },
      { label: '停用', value: 0 },
    ],
    formatter: (value) => (Number(value) === 1 ? '正常' : '停用'),
  },
  roleId: {
    label: '角色',
    inputType: 'select',
    placeholder: '请选择角色',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 12,
    options: mockRoleOptions,
    props: {
      multiple: true,
      style: {
        width: '100%',
      },
    },
  },
  password: {
    label: '密码',
    inputType: 'password',
    placeholder: '不填写则默认 123456',
    tableVisible: false,
    formVisible: dialogMode.value === 'create',
    formOrder: 7,
    span: 12,
    props: {
      autocomplete: 'new-password',
    },
  },
  userId: {
    label: '用户编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableMinWidth: 180,
  },
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.userId))
    },
  },
  {
    key: 'enable',
    label: '启用',
    buttonType: 'success',
    visible: (row) => Number(row.status) !== 1,
    onClick: async (row) => {
      await handleChangeStatus(Number(row.userId), 1)
    },
  },
  {
    key: 'disable',
    label: '停用',
    buttonType: 'warning',
    visible: (row) => Number(row.status) === 1,
    onClick: async (row) => {
      await handleChangeStatus(Number(row.userId), 0)
    },
  },
  {
    key: 'delete',
    label: '删除',
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteUser(Number(row.userId))
    },
  },
])

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增用户' : '编辑用户'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建用户' : '保存修改'))
const currentRows = computed(() => pageState.value.records)
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: currentRows.value,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const userFormRules = computed<FormRules>(() => ({
  username:
    dialogMode.value === 'create'
      ? [
          { required: true, message: '请输入账号', trigger: 'blur' },
          { min: 2, max: 20, message: '账号长度需在 2 到 20 位之间', trigger: 'blur' },
        ]
      : [],
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度需在 2 到 20 位之间', trigger: 'blur' },
  ],
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  roleId: [{ required: true, message: '请至少选择一个角色', trigger: 'change', type: 'array' }],
  password:
    dialogMode.value === 'create'
      ? [
          {
            trigger: 'blur',
            validator: (_rule, value, callback) => {
              if (!value) {
                callback()
                return
              }

              if (String(value).length < 6 || String(value).length > 20) {
                callback(new Error('密码长度需在 6 到 20 位之间'))
                return
              }

              callback()
            },
          },
        ]
      : [],
}))

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.username = String(nextValue.username ?? '')
  queryForm.nickName = String(nextValue.nickName ?? '')
  queryForm.phoneNumber = String(nextValue.phoneNumber ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

/**
 * 方法效果：
 * 根据当前查询表单和分页状态组装后端用户列表查询参数。
 * 参数：
 * - 无，直接读取当前页的查询表单与分页状态。
 * 返回值：
 * - 与后端 `UserQuarry + PageUtil` 对齐的查询参数对象。
 */
const buildUserListParams = (): SysUserListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    username: queryForm.username.trim() || undefined,
    nickName: queryForm.nickName.trim() || undefined,
    phoneNumber: queryForm.phoneNumber.trim() || undefined,
    status: queryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取用户分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchUserPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysUserPageApi(buildUserListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 处理查询表单提交，并从第一页重新拉取用户列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新用户列表。
 */
const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchUserPage()
}

/**
 * 方法效果：
 * 重置查询表单，并恢复到分页初始状态重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultQueryForm())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchUserPage()
}

/**
 * 方法效果：
 * 打开新增用户弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  userFormModel.value = createDefaultUserForm()
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑用户弹窗，并先拉取用户详情用于完整回显。
 * 参数：
 * - `userId`：待编辑的用户主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (userId: number) => {
  const result = await getSysUserDetailApi(userId)

  /**
   * 编辑接口提交时后端会先清空旧角色再重新写入，
   * 所以这里必须把详情里的 `roleId` 一并保留回表单模型中，避免保存后丢角色。
   */
  dialogMode.value = 'edit'
  userFormModel.value = {
    ...createDefaultUserForm(),
    ...result.data,
    roleId: result.data.roleId ?? [],
    userRole: result.data.userRole ?? [],
    password: '',
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗的表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleUserFormModelUpdate = (nextValue: Record<string, unknown>) => {
  userFormModel.value = {
    ...userFormModel.value,
    ...nextValue,
    roleId: Array.isArray(nextValue.roleId) ? (nextValue.roleId as number[]) : userFormModel.value.roleId,
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码与每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新用户列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchUserPage()
}

/**
 * 方法效果：
 * 提交新增或编辑用户表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitUserForm = async () => {
  if (!userFormModel.value.username.trim()) {
    ElMessage.warning('请输入账号')
    return
  }

  if (!userFormModel.value.nickName.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }

  submitLoading.value = true

  try {
    const payload: SysUserFormData = {
      ...userFormModel.value,
      username: userFormModel.value.username.trim(),
      nickName: userFormModel.value.nickName.trim(),
      phoneNumber: userFormModel.value.phoneNumber.trim(),
      password: userFormModel.value.password?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createSysUserApi(payload)
      ElMessage.success('用户创建成功')
    } else {
      await updateSysUserApi(payload)
      ElMessage.success('用户更新成功')
    }

    dialogVisible.value = false
    await fetchUserPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 更新指定用户的启用状态，并在成功后刷新当前页列表。
 * 参数：
 * - `userId`：用户主键。
 * - `status`：目标状态。
 * 返回值：
 * - 无返回值；副作用是调用状态切换接口并刷新列表。
 */
const handleChangeStatus = async (userId: number, status: number) => {
  await changeSysUserStatusApi(userId, status)
  ElMessage.success(status === 1 ? '用户已启用' : '用户已停用')
  await fetchUserPage()
}

/**
 * 方法效果：
 * 删除指定用户，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `userId`：待删除的用户主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteUser = async (userId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除用户', {
    type: 'warning',
  })

  await deleteSysUsersApi([userId])
  ElMessage.success('用户删除成功')

  /**
   * 如果当前页只剩最后一条数据，删除后就主动回退一页，
   * 这样能避免接口返回空页时界面停留在“已无数据但页码仍偏后”的状态。
   */
  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchUserPage()
}

onMounted(async () => {
  await fetchUserPage()
})
</script>

<template>
  <!-- 用户管理页面区域 -->
  <section class="system-user-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="5"
        label-width="64px"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="用户列表">
      <SharedTablePanel
        :rows="currentRows as Record<string, unknown>[]"
        :schema="userSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-index="true"
        :pagination="tablePagination"
        :table-max-height="560"
        :form-visible="dialogVisible"
        :form-model-value="userFormModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="2"
        :form-rules="userFormRules"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleUserFormModelUpdate"
        @form-submit="handleSubmitUserForm"
        @form-cancel="dialogVisible = false"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.system-user-view {
  display: grid;
  gap: 18px;
}
</style>
