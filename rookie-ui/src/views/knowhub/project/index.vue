<!--
  文件作用：
  承接项目管理页面（后台菜单名"项目管理"），负责项目列表查询、分页展示、新增、编辑、
  发布、撤回、审核、删除、详情查看。成员管理与文件树在「编辑弹窗」内操作（参考用户要求：
  新增/编辑弹窗内就要能加成员和文件，详情只读、审核只给审核结果）。
  关键状态：
  - `queryForm`：当前查询条件。
  - `pageState`：分页状态。
  - `editVisible`/`editMode`/`editProjectId`：编辑弹窗显隐、模式、项目主键（创建成功后回填解锁成员/文件页）。
  - `detailVisible`/`detailRecord`：详情弹窗（只读，不改数据）。
  - `reviewVisible`/`reviewProjectId`/`reviewProject`/`reviewForm`：审核弹窗（只给通过/驳回+意见）。
  关键依赖：
  - 列表用 SharedTablePanel 仅展示表格（不放内置弹窗表单），编辑用独立 ProjectEditDialog；
  - 等级权限(view/download/edit:lN)由后端 ProjectPermissionResolver 取最高等级判定，
    前端列表可见性由后端 SQL 过滤、详情按钮显隐由后端回填的 canView/canDownload/canEdit 控制。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteProjectsApi,
  getProjectDetailApi,
  getProjectPageApi,
  publishProjectApi,
  reviewProjectApi,
  revokeProjectApi,
} from '@/api/knowhub/project'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import ProjectEditDialog from './components/ProjectEditDialog.vue'
import ProjectDetailDialog from './components/ProjectDetailDialog.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  ProjectListQuery,
  ProjectPageResult,
  ProjectRecord,
  ProjectReviewPayload,
} from '@/types/api/knowhub/project'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultProjectQuery,
  createProjectQuerySchema,
  createProjectSchema,
  type ProjectQueryFormState,
} from './config'

type ProjectDialogMode = 'create' | 'edit'

const queryForm = reactive<ProjectQueryFormState>(createDefaultProjectQuery())
const listLoading = ref(false)
const pageState = ref<ProjectPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

// 编辑弹窗（新增+编辑共用，含项目信息/成员/文件三标签页）
const editVisible = ref(false)
const editMode = ref<ProjectDialogMode>('create')
const editProjectId = ref<number | undefined>(undefined)

// 详情弹窗（只读）
const detailVisible = ref(false)
const detailRecord = ref<ProjectRecord | null>(null)

// 审核弹窗（只给通过/驳回+意见，不展示其它数据）
const reviewVisible = ref(false)
const reviewProjectId = ref<number | null>(null)
const reviewProject = ref<ProjectRecord | null>(null)
const reviewLoading = ref(false)
const reviewForm = reactive<{ pass: boolean; advice: string }>({ pass: true, advice: '' })

const querySchema = computed<SharedFieldSchemaMap<ProjectQueryFormState>>(() => createProjectQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<ProjectRecord>>(() => createProjectSchema())
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
    permKey: SYSTEM_PERMISSION_KEYS.project.create,
    buttonType: 'primary',
    // 仅 DRAFT/REJECTED/REVOKED/ARCHIVED 可编辑；PUBLISHED 须先撤回、PENDING_REVIEW 审核中不能改
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      openEditDialog(Number(row.projectId))
    },
  },
  {
    key: 'publish',
    label: '发布',
    permKey: SYSTEM_PERMISSION_KEYS.project.publish,
    buttonType: 'success',
    visible: (row) => !['PUBLISHED', 'PENDING_REVIEW'].includes(String(row.status)),
    onClick: async (row) => {
      await handlePublishProject(Number(row.projectId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.project.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PUBLISHED',
    onClick: async (row) => {
      await handleRevokeProject(Number(row.projectId))
    },
  },
  {
    key: 'review',
    label: '审核',
    permKey: SYSTEM_PERMISSION_KEYS.project.review,
    buttonType: 'primary',
    visible: (row) => String(row.status) === 'PENDING_REVIEW',
    onClick: async (row) => {
      await openReviewDialog(Number(row.projectId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.projectId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.project.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteProject(Number(row.projectId))
    },
  },
])

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.title = String(nextValue.title ?? '')
  queryForm.type = nextValue.type ? String(nextValue.type) : undefined
  queryForm.level =
    nextValue.level != null && nextValue.level !== '' ? Number(nextValue.level) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.createBy = String(nextValue.createBy ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): ProjectListQuery => {
  const [beginTime, endTime] = queryForm.dateRange
  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    type: queryForm.type,
    level: queryForm.level,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    createBy: queryForm.createBy.trim() || undefined,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  listLoading.value = true
  try {
    pageState.value = await getProjectPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultProjectQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/** 打开新增项目弹窗（只显示项目信息页，提交创建成功后解锁成员/文件页） */
const openCreateDialog = () => {
  editMode.value = 'create'
  editProjectId.value = undefined
  editVisible.value = true
}

/** 打开编辑项目弹窗（三标签页全部可操作） */
const openEditDialog = (projectId: number) => {
  editMode.value = 'edit'
  editProjectId.value = projectId
  editVisible.value = true
}

/** 编辑弹窗创建成功后回填 projectId，解锁成员/文件页并刷新列表 */
const handleEditSaved = (projectId: number) => {
  editProjectId.value = projectId
  editMode.value = 'edit'
  void fetchPage()
}

const openDetailDialog = async (projectId: number) => {
  const result = await getProjectDetailApi(projectId)
  detailRecord.value = result.data
  detailVisible.value = true
}

const openReviewDialog = async (projectId: number) => {
  reviewLoading.value = true
  try {
    const result = await getProjectDetailApi(projectId)
    reviewProject.value = result.data
  } finally {
    reviewLoading.value = false
  }
  reviewProjectId.value = projectId
  reviewForm.pass = true
  reviewForm.advice = ''
  reviewVisible.value = true
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

const handlePublishProject = async (projectId: number) => {
  await ElMessageBox.confirm('确认发布该项目吗？发布后对可见。', '发布项目', { type: 'warning' })
  await publishProjectApi(projectId)
  ElMessage.success('项目发布成功')
  await fetchPage()
}

const handleRevokeProject = async (projectId: number) => {
  await ElMessageBox.confirm('确认撤回该项目吗？撤回后不再可见。', '撤回项目', { type: 'warning' })
  await revokeProjectApi(projectId)
  ElMessage.success('项目撤回成功')
  await fetchPage()
}

const handleReviewSubmit = async () => {
  if (!reviewProjectId.value) return
  if (!reviewForm.pass && !reviewForm.advice.trim()) {
    ElMessage.warning('驳回需填写审核意见')
    return
  }
  const payload: ProjectReviewPayload = {
    projectId: reviewProjectId.value,
    pass: reviewForm.pass,
    advice: reviewForm.advice.trim() || undefined,
  }
  await reviewProjectApi(payload)
  ElMessage.success(reviewForm.pass ? '审核通过，项目已发布' : '已驳回项目')
  reviewVisible.value = false
  await fetchPage()
}

const handleDeleteProject = async (projectId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除项目', { type: 'warning' })
  await deleteProjectsApi([projectId])
  ElMessage.success('项目删除成功')
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
  <section class="project-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增项目"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.project.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="项目列表">
      <!-- 列表仅展示表格，编辑用独立 ProjectEditDialog（含成员/文件标签页） -->
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="false"
        row-key="projectId"
        @pagination-change="handlePaginationChange"
      />
    </BaseCard>

    <!-- 新增/编辑项目弹窗（项目信息 / 团队成员 / 项目文件 三标签页） -->
    <ProjectEditDialog
      :visible="editVisible"
      :mode="editMode"
      :project-id="editProjectId"
      @update:visible="editVisible = $event"
      @saved="handleEditSaved"
    />

    <!-- 项目详情弹窗（只读，不改数据；下载属查看行为仍可用） -->
    <ProjectDetailDialog
      :visible="detailVisible"
      :project="detailRecord"
      @update:visible="detailVisible = $event"
    />

    <!-- 项目审核弹窗（仅审核结果+意见，不展示其它数据，不承担改数据职责） -->
    <el-dialog v-model="reviewVisible" title="审核项目" width="520px" destroy-on-close>
      <div v-if="reviewProject" class="project-review__meta">
        <p><strong>项目名称：</strong>{{ reviewProject.title }}</p>
        <p v-if="reviewProject.summary"><strong>简介：</strong>{{ reviewProject.summary }}</p>
      </div>
      <el-form label-width="80px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.pass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见" :required="!reviewForm.pass">
          <el-input
            v-model="reviewForm.advice"
            type="textarea"
            :rows="3"
            :placeholder="reviewForm.pass ? '通过意见（可选）' : '驳回意见（必填）'"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="handleReviewSubmit">提交审核</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.project-view {
  display: grid;
  gap: 18px;
}

.project-review__meta {
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.project-review__meta p {
  margin: 4px 0;
}
</style>