<!--
  文件作用：
  承接资金流水管理页（后台菜单"资金流水"），负责三流合一（预算/收账/花销）列表查询、分页展示、
  新增、编辑、删除、提交审批、通过、驳回、撤回与只读详情（含审核历史时间线）。
  关键状态：
  - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
  - `pageState` / `dialogVisible` / `detailVisible` / `reviewVisible`：分页、编辑弹窗、详情弹窗、审核弹窗状态。
  关键依赖：
  - 复用公共表格、公共表单、筛选面板，对齐博客/资源管理页结构。
  - flowType/category/status/reviewStatus 走字典（dictKey 自动渲染 DictTag/下拉）。
  - 审核历史时间线复用博客页同款渲染（DictTag review_action + 操作人昵称 + 时间 + 意见）。
  - 行操作按钮按 status 显隐：DRAFT→提交/编辑/删除；PENDING→通过/驳回；APPROVED→撤回；REJECTED/REVOKED→删除。
  - 撤回/提交/审核/重算 均弹确认或经审核弹窗，advice 必填校验在审核弹窗内完成。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElCollapse, ElCollapseItem, ElDialog, ElEmpty, ElLink, ElMessage, ElMessageBox } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import {
  approveAuditFlowApi,
  createAuditFlowApi,
  deleteAuditFlowApi,
  getAuditFlowDetailApi,
  getAuditFlowPageApi,
  getAuditFlowReviewLogApi,
  rejectAuditFlowApi,
  revokeAuditFlowApi,
  submitAuditFlowApi,
  updateAuditFlowApi,
} from '@/api/knowhub/audit'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import FlowReviewDialog from './FlowReviewDialog.vue'
import VoucherUploader from './components/VoucherUploader.vue'
import SubjectPicker from '../components/SubjectPicker.vue'
import UserPicker from '../components/UserPicker.vue'
import { downloadVoucher } from '../shared/downloadVoucher'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { formatDateTime } from '@/utils/format'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  FlowReviewLogRecord,
  FundFlowListQuery,
  FundFlowPageResult,
  FundFlowRecord,
  FundFlowReviewPayload,
} from '@/types/api/knowhub/audit'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  buildFlowFormRules,
  createDefaultFlowForm,
  createDefaultFlowQuery,
  createFlowQuerySchema,
  createFlowSchema,
  formatAmount,
  type FundFlowQueryFormState,
} from './config'

type FlowDialogMode = 'create' | 'edit'

const queryForm = reactive<FundFlowQueryFormState>(createDefaultFlowQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<FlowDialogMode>('create')
const formModel = ref<FundFlowRecord>(createDefaultFlowForm())
const detailVisible = ref(false)
const detailRecord = ref<FundFlowRecord | null>(null)
/** 审核历史流水（详情弹窗展示，按 flowId 拉取） */
const reviewLogs = ref<FlowReviewLogRecord[]>([])
const reviewLogLoading = ref(false)
/** 审核弹窗：决定 approve / reject */
const reviewVisible = ref(false)
const reviewFlowId = ref<number | null>(null)
const reviewFlow = ref<FundFlowRecord | null>(null)
const reviewDecide = ref<'approve' | 'reject'>('approve')
const pageState = ref<FundFlowPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<FundFlowQueryFormState>>(() => createFlowQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<FundFlowRecord>>(() => createFlowSchema())
const flowFormRules = computed(() => buildFlowFormRules())
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增资金流水' : '编辑资金流水'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建流水' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'submit',
    label: '提交审批',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.review,
    buttonType: 'success',
    visible: (row) => String(row.status) === 'DRAFT',
    onClick: async (row) => {
      await handleSubmitFlow(Number(row.flowId))
    },
  },
  {
    key: 'approve',
    label: '通过',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.approve,
    buttonType: 'primary',
    visible: (row) => String(row.status) === 'PENDING',
    onClick: async (row) => {
      await openReviewDialog(Number(row.flowId), 'approve')
    },
  },
  {
    key: 'reject',
    label: '驳回',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.reject,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PENDING',
    onClick: async (row) => {
      await openReviewDialog(Number(row.flowId), 'reject')
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'APPROVED',
    onClick: async (row) => {
      await handleRevokeFlow(Number(row.flowId))
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.edit,
    buttonType: 'primary',
    // 草稿可编辑；PENDING/APPROVED 不能改（待审/已通过）；REVOKED/REJECTED 不可改须重建
    visible: (row) => String(row.status) === 'DRAFT',
    onClick: async (row) => {
      await openEditDialog(Number(row.flowId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.flowId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.audit.flow.delete,
    buttonType: 'danger',
    // 草稿/已驳回/已撤回可删；PENDING 待审不可删；APPROVED 已通过不可删（须先撤回）
    visible: (row) => ['DRAFT', 'REJECTED', 'REVOKED'].includes(String(row.status)),
    onClick: async (row) => {
      await handleDeleteFlow(Number(row.flowId))
    },
  },
])

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.subjectName = String(nextValue.subjectName ?? '')
  queryForm.flowType = nextValue.flowType ? String(nextValue.flowType) : undefined
  queryForm.category = nextValue.category ? String(nextValue.category) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.reviewStatus = nextValue.reviewStatus ? String(nextValue.reviewStatus) : undefined
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): FundFlowListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    subjectName: queryForm.subjectName.trim() || undefined,
    flowType: queryForm.flowType,
    category: queryForm.category,
    status: queryForm.status,
    reviewStatus: queryForm.reviewStatus,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getAuditFlowPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultFlowQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultFlowForm()
  dialogVisible.value = true
}

const openEditDialog = async (flowId: number) => {
  const result = await getAuditFlowDetailApi(flowId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultFlowForm(),
    ...result.data,
  }
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开只读详情弹窗，拉取流水详情与审核历史后展示。
 * 参数：
 * - `flowId`：待查看流水主键。
 */
const openDetailDialog = async (flowId: number) => {
  const result = await getAuditFlowDetailApi(flowId)

  detailRecord.value = result.data
  detailVisible.value = true

  reviewLogLoading.value = true
  try {
    const logsResult = await getAuditFlowReviewLogApi(flowId)
    reviewLogs.value = logsResult.data ?? []
  } catch {
    reviewLogs.value = []
  } finally {
    reviewLogLoading.value = false
  }
}

/**
 * 方法效果：
 * 打开审核弹窗，先拉取流水详情供审核员参考。
 * 参数：
 * - `flowId`：待审核流水主键。
 * - `decide`：approve 通过 / reject 驳回，决定弹窗默认决定与标题。
 */
const openReviewDialog = async (flowId: number, decide: 'approve' | 'reject') => {
  reviewDecide.value = decide
  reviewFlow.value = await getAuditFlowDetailApi(flowId).then((r) => r.data)
  reviewFlowId.value = flowId
  reviewVisible.value = true
}

const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    flowType: nextValue.flowType ? String(nextValue.flowType) : formModel.value.flowType,
    category: nextValue.category != null && nextValue.category !== ''
      ? String(nextValue.category)
      : formModel.value.category,
    subjectId:
      nextValue.subjectId != null && nextValue.subjectId !== ''
        ? Number(nextValue.subjectId)
        : formModel.value.subjectId,
    amount:
      nextValue.amount != null && nextValue.amount !== ''
        ? Number(nextValue.amount)
        : formModel.value.amount,
    handlerId:
      nextValue.handlerId != null && nextValue.handlerId !== ''
        ? Number(nextValue.handlerId)
        : formModel.value.handlerId,
    voucherObjectId:
      nextValue.voucherObjectId != null && nextValue.voucherObjectId !== ''
        ? Number(nextValue.voucherObjectId)
        : formModel.value.voucherObjectId,
  }
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: FundFlowRecord = {
      ...formModel.value,
      note: formModel.value.note?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createAuditFlowApi(payload)
      ElMessage.success('流水创建成功')
    } else {
      await updateAuditFlowApi(payload)
      ElMessage.success('流水更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

const handleSubmitFlow = async (flowId: number) => {
  await ElMessageBox.confirm('确认提交该流水审批吗？', '提交审批', { type: 'warning' })

  await submitAuditFlowApi(flowId)
  ElMessage.success('已提交审批')
  await fetchPage()
}

const handleRevokeFlow = async (flowId: number) => {
  await ElMessageBox.confirm('确认撤回该已通过流水吗？撤回后不再计入主体结余。', '撤回流水', {
    type: 'warning',
  })

  await revokeAuditFlowApi(flowId)
  ElMessage.success('已撤回流水')
  await fetchPage()
}

/**
 * 方法效果：
 * 接收审核弹窗的提交结果，调通过/驳回接口并刷新列表。
 * 参数：
 * - `payload`：审核入参（blogId 承 flowId / pass / advice）。
 */
const handleReviewSubmit = async (payload: FundFlowReviewPayload) => {
  if (payload.pass) {
    await approveAuditFlowApi(payload)
    ElMessage.success('审核通过，流水已生效')
  } else {
    await rejectAuditFlowApi(payload)
    ElMessage.success('已驳回流水')
  }
  reviewVisible.value = false
  await fetchPage()
}

const handleDeleteFlow = async (flowId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除资金流水', { type: 'warning' })

  await deleteAuditFlowApi(flowId)
  ElMessage.success('流水删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

const formatTime = (value: unknown) => formatDateTime(value) || '--'
/** 详情弹窗内金额格式化（复用 config formatAmount，保持千分位+两位小数一致） */
const formatAmountInline = (value: unknown) => formatAmount(value)

/**
 * 方法效果：
 * 读取路由 query 中透传过来的筛选条件（由"花销主体"页"流水明细"跳转携带），
 * 回填到当前查询表单，使首屏列表即按跳转来源主体过滤。
 * 与系统字典点击「数据项」跳字典数据管理页同款范式（消费方在 onMounted 读 route.query）。
 */
const route = useRoute()
const applyRouteQuery = () => {
  const subjectName = route.query.subjectName
  if (subjectName != null && String(subjectName) !== '') {
    queryForm.subjectName = String(subjectName)
  }
}

onMounted(async () => {
  applyRouteQuery()
  await fetchPage()
})
</script>

<template>
  <section class="audit-flow-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增流水"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.audit.flow.add"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="资金流水列表">
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
        :form-rules="flowFormRules"
        row-key="flowId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      >
        <!-- 主体选人：插槽接管为 remote 搜索主体名的下拉，回写 subjectId 到 formModel.subjectId -->
        <template #field-subjectId="{ modelValue, updateFieldValue }">
          <SubjectPicker
            :model-value="modelValue as number | undefined"
            @update:model-value="(value: number | undefined) => updateFieldValue(value)"
          />
        </template>
        <!-- 经办人选人：插槽接管为 remote 搜索昵称的用户下拉，回写 userId 到 formModel.handlerId -->
        <template #field-handlerId="{ modelValue, updateFieldValue }">
          <UserPicker
            :model-value="modelValue as number | undefined"
            @update:model-value="(value: number | undefined) => updateFieldValue(value)"
          />
        </template>
        <!-- 票据凭证：插槽接管为弹窗内直传图片/PDF 的 VoucherUploader，回写 objectId 到 formModel.voucherObjectId -->
        <template #field-voucherObjectId="{ modelValue, updateFieldValue }">
          <VoucherUploader
            :model-value="modelValue as number | undefined"
            @update:model-value="(value: number | undefined) => updateFieldValue(value)"
          />
        </template>
      </SharedTablePanel>
    </BaseCard>

    <ElDialog
      :model-value="detailVisible"
      title="资金流水详情"
      width="780px"
      destroy-on-close
      @update:model-value="detailVisible = $event"
    >
      <article v-if="detailRecord" class="audit-flow-detail">
        <h2 class="audit-flow-detail__title">流水 #{{ detailRecord.flowId }}</h2>

        <div class="audit-flow-detail__meta">
          <DictTag dict-key="audit_flow_type" :value="detailRecord.flowType" />
          <DictTag dict-key="audit_flow_status" :value="detailRecord.status" />
          <DictTag v-if="detailRecord.reviewStatus" dict-key="review_status" :value="detailRecord.reviewStatus" />
          <DictTag v-if="detailRecord.category" dict-key="audit_expense_category" :value="detailRecord.category" />
        </div>

        <div class="audit-flow-detail__info">
          <span><em>金额</em>{{ formatAmountInline(detailRecord.amount) }} 元</span>
          <span><em>主体</em>{{ detailRecord.subjectName || detailRecord.subjectId }}</span>
          <span><em>经办人</em>{{ detailRecord.handlerNickname || detailRecord.handlerId || '--' }}</span>
          <span><em>发生日期</em>{{ formatTime(detailRecord.occurDate) }}</span>
          <span v-if="detailRecord.voucherObjectId" class="audit-flow-detail__info-full audit-flow-detail__voucher">
            <em>票据附件</em>
            <ElLink type="primary" :underline="false" @click="downloadVoucher(detailRecord.voucherObjectId)">
              点击下载 (附件 #{{ detailRecord.voucherObjectId }})
            </ElLink>
          </span>
          <span><em>创建时间</em>{{ formatTime(detailRecord.createTime) }}</span>
          <span v-if="detailRecord.note" class="audit-flow-detail__info-full"><em>备注</em>{{ detailRecord.note }}</span>
        </div>

        <ElCollapse v-if="reviewLogs.length > 0" class="audit-flow-detail__review-log">
          <ElCollapseItem title="审核历史" name="review-log">
            <ul class="audit-flow-detail__timeline">
              <li v-for="log in reviewLogs" :key="log.reviewLogId" class="audit-flow-detail__timeline-item">
                <div class="audit-flow-detail__timeline-head">
                  <DictTag dict-key="review_action" :value="log.action" />
                  <span class="audit-flow-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                  <span class="audit-flow-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
                </div>
                <p v-if="log.advice" class="audit-flow-detail__timeline-advice">{{ log.advice }}</p>
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
        <ElEmpty
          v-else-if="!reviewLogLoading && detailVisible"
          class="audit-flow-detail__review-empty"
          description="暂无审核历史"
          :image-size="48"
        />
      </article>

      <template #footer>
        <div class="audit-flow-detail__footer">
          <ElButton @click="detailVisible = false">关闭</ElButton>
        </div>
      </template>
    </ElDialog>

    <FlowReviewDialog
      :visible="reviewVisible"
      :flow-id="reviewFlowId"
      :flow="reviewFlow"
      :decide="reviewDecide"
      @update:visible="reviewVisible = $event"
      @submit="handleReviewSubmit"
    />
  </section>
</template>

<script lang="ts">
export default { name: 'AuditFlowView' }
</script>

<style scoped>
.audit-flow-view {
  display: grid;
  gap: 18px;
}

.audit-flow-detail {
  display: grid;
  gap: 16px;
}

.audit-flow-detail__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--rookie-text);
}

.audit-flow-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.audit-flow-detail__info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 24px;
  padding: 12px 16px;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.audit-flow-detail__info-full {
  grid-column: 1 / -1;
}

.audit-flow-detail__voucher {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-flow-detail__info em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.audit-flow-detail__review-log {
  border-top: 1px solid var(--rookie-border);
  padding-top: 8px;
}

.audit-flow-detail__timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.audit-flow-detail__timeline-item {
  padding: 8px 12px;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
}

.audit-flow-detail__timeline-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: var(--rookie-font-size-sm);
}

.audit-flow-detail__timeline-operator {
  color: var(--rookie-text);
  font-weight: 500;
}

.audit-flow-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.audit-flow-detail__timeline-advice {
  margin: 6px 0 0;
  padding: 6px 10px;
  border-left: 2px solid var(--rookie-border);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-flow-detail__review-empty {
  padding: 12px 0;
}

.audit-flow-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>