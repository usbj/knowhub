<!--
  文件作用：
  承接审计花销主体管理页（后台菜单"花销主体"），负责主体列表查询、分页展示、新增、编辑、删除。
  关键状态：
  - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
  - `pageState` / `dialogVisible` / `dialogMode`：分页、编辑弹窗状态。
  关键依赖：
  - 复用公共表格、公共表单、筛选面板，对齐博客/资源管理页结构。
  - scope 走字典 audit_subject_scope（dictKey 自动渲染 DictTag/下拉）；
    status 为 ACTIVE/CLOSED 二值，前端硬编码 select 选项；
    projectId 仅在 scope=PROJECT 时由 computed schema 切 formVisible=true。
  - balance/monthExpense 为后端聚合回填字段，列表直接展示，不入表单。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAuditSubjectApi,
  deleteAuditSubjectApi,
  getAuditSubjectDetailApi,
  getAuditSubjectPageApi,
  updateAuditSubjectApi,
} from '@/api/knowhub/audit'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import UserPicker from '../components/UserPicker.vue'
import { useRouter } from 'vue-router'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SubjectListQuery, SubjectPageResult, SubjectRecord } from '@/types/api/knowhub/audit'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import type { NavigationMenuItem } from '@/types/components/navigation'
import {
  createDefaultSubjectForm,
  createDefaultSubjectQuery,
  createSubjectQuerySchema,
  createSubjectSchema,
  subjectFormRules,
  type SubjectQueryFormState,
} from './config'

type SubjectDialogMode = 'create' | 'edit'

const queryForm = reactive<SubjectQueryFormState>(createDefaultSubjectQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<SubjectDialogMode>('create')
const formModel = ref<SubjectRecord>(createDefaultSubjectForm())
const router = useRouter()
const layoutNavigationStore = useLayoutNavigationStore()
const pageState = ref<SubjectPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<SubjectQueryFormState>>(() => createSubjectQuerySchema())
/**
 * 表格列与表单字段 schema：按当前表单 scope 动态切换 projectId 的 formVisible。
 * - scope=PROJECT：projectId 可见（number 输入）。
 * - scope=LAB：projectId 隐藏。
 */
const tableSchema = computed<SharedFieldSchemaMap<SubjectRecord>>(() => {
  const base = createSubjectSchema()
  if (base.projectId) {
    base.projectId.formVisible = formModel.value.scope === 'PROJECT'
  }
  return base
})
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增花销主体' : '编辑花销主体'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建主体' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'flowDetail',
    label: '流水明细',
    buttonType: 'primary',
    onClick: async (row) => {
      await jumpToFlowPage(row as unknown as SubjectRecord)
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.audit.subject.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.subjectId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.audit.subject.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteSubject(Number(row.subjectId))
    },
  },
])

/**
 * 方法效果：
 * 跳转到「资金流水」页，并把主体名带过去自动回填筛选区（按主体名模糊查该主体全部流水）。
 * 与系统字典点击「数据项」跳字典数据管理页同款 router.push({ path, query }) 范式：
 * 目标页在 onMounted 读取 route.query 自动应用筛选条件。
 * 关键点：跳转路径不硬编码拼接，而是把导航 store 的标准化菜单树（normalizeMenuTree 递归
 * 拼接祖先 route 段所得完整路径，与侧边栏 RouterLink :to 用的同源）递归拍平后用 permKey 查出
 * 「资金流水」菜单的 route 再跳转，保证命中的就是侧边栏点开同菜单时实际注册的路由，
 * 杜绝路径段人工拼接与实际菜单树层级不一致（如 knowhub 挂在 system 下多一层）导致跳转后
 * Vue Router 报 "No match found for location" 整页布局空白的问题。
 * 参数：
 * - `subject`：行选中主体记录（取 subjectId/subjectName 透传）。
 */
const flattenMenuTree = (menus: NavigationMenuItem[]): NavigationMenuItem[] =>
  menus.flatMap((menu) => [menu, ...flattenMenuTree(menu.children)])

const jumpToFlowPage = async (subject: SubjectRecord) => {
  const flowMenu = flattenMenuTree(layoutNavigationStore.menuTree).find(
    (menu) => menu.permKey === 'knowhub:audit:flow' && menu.menuType === 2,
  )

  await router.push({
    path: flowMenu?.route ?? '/system/blog/audit/audit-flow',
    query: {
      subjectId: subject.subjectId != null ? String(subject.subjectId) : undefined,
      subjectName: subject.name ?? undefined,
    },
  })
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
  queryForm.name = String(nextValue.name ?? '')
  queryForm.scope = nextValue.scope ? String(nextValue.scope) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.handlerName = String(nextValue.handlerName ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): SubjectListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    name: queryForm.name.trim() || undefined,
    scope: queryForm.scope,
    status: queryForm.status,
    handlerName: queryForm.handlerName.trim() || undefined,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取主体分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getAuditSubjectPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行主体查询，并从第一页重新拉取列表。
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
 * 重置主体查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultSubjectQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增主体弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultSubjectForm()
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑主体弹窗，并先拉取详情用于回显。
 * 参数：
 * - `subjectId`：待编辑主体主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (subjectId: number) => {
  const result = await getAuditSubjectDetailApi(subjectId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultSubjectForm(),
    ...result.data,
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
    scope: nextValue.scope ? String(nextValue.scope) : formModel.value.scope,
    status: nextValue.status ? String(nextValue.status) : formModel.value.status,
    handlerId:
      nextValue.handlerId != null && nextValue.handlerId !== ''
        ? Number(nextValue.handlerId)
        : formModel.value.handlerId,
    projectId:
      nextValue.projectId != null && nextValue.projectId !== ''
        ? Number(nextValue.projectId)
        : formModel.value.projectId,
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新主体列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑主体表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: SubjectRecord = {
      ...formModel.value,
      name: formModel.value.name.trim(),
      note: formModel.value.note?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createAuditSubjectApi(payload)
      ElMessage.success('主体创建成功')
    } else {
      await updateAuditSubjectApi(payload)
      ElMessage.success('主体更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除指定主体，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `subjectId`：待删除主体主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteSubject = async (subjectId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，关联流水可能受影响，确认继续吗？', '删除花销主体', {
    type: 'warning',
  })

  await deleteAuditSubjectApi(subjectId)
  ElMessage.success('主体删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <section class="audit-subject-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增主体"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.audit.subject.add"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="花销主体列表">
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
        :form-rules="subjectFormRules"
        row-key="subjectId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      >
        <!-- 负责人选人：插槽接管为 remote 搜索昵称的用户下拉，回写 userId 到 formModel.handlerId -->
        <template #field-handlerId="{ modelValue, updateFieldValue }">
          <UserPicker
            :model-value="modelValue as number | undefined"
            @update:model-value="(value: number | undefined) => updateFieldValue(value)"
          />
        </template>
      </SharedTablePanel>
    </BaseCard>
  </section>
</template>

<style scoped>
.audit-subject-view {
  display: grid;
  gap: 18px;
}
</style>