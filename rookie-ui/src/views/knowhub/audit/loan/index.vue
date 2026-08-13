<!--
  文件作用：
  承接物品借出管理页（后台菜单"物品借出"），负责借出列表查询、分页展示、新增、编辑、删除、
  审核通过/驳回、归还（损耗扣费走阈值审批）与只读详情（含审核历史时间线）。
  关键状态：
  - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
  - `pageState` / `dialogVisible` / `detailVisible` / `reviewVisible` / `returnVisible`：分页与各弹窗状态。
  关键依赖：
  - 复用公共表格、公共表单、筛选面板，对齐博客/资源管理页结构。
  - itemType/status 走字典（dictKey 自动渲染 DictTag/下拉）；assetNo 按 itemType=ASSET 切 formVisible。
  - pendingReturn switch 过滤待归还（BORROWED/OVERDUE）。
  - 审核历史时间线复用博客页同款渲染。
  - 行操作按钮按 status 显隐：REQUEST→通过/驳回/编辑/删除；BORROWED/OVERDUE→归还；REJECTED→删除。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDialog,
  ElEmpty,
  ElLink,
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import {
  approveAuditLoanApi,
  createAuditLoanApi,
  deleteAuditLoanApi,
  getAuditLoanDetailApi,
  getAuditLoanPageApi,
  getAuditLoanReviewLogApi,
  rejectAuditLoanApi,
  returnAuditLoanApi,
  updateAuditLoanApi,
} from '@/api/knowhub/audit'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import LoanReviewDialog from './LoanReviewDialog.vue'
import LoanReturnDialog from './LoanReturnDialog.vue'
import SubjectPicker from '../components/SubjectPicker.vue'
import VoucherUploader from '../flow/components/VoucherUploader.vue'
import { downloadVoucher } from '../shared/downloadVoucher'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { formatDateTime } from '@/utils/format'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  LoanListQuery,
  LoanPageResult,
  LoanRecord,
  LoanReviewLogRecord,
  LoanReviewPayload,
} from '@/types/api/knowhub/audit'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  buildLoanFormRules,
  createDefaultLoanForm,
  createDefaultLoanQuery,
  createLoanQuerySchema,
  createLoanSchema,
  type LoanQueryFormState,
} from './config'

type LoanDialogMode = 'create' | 'edit'

const queryForm = reactive<LoanQueryFormState>(createDefaultLoanQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<LoanDialogMode>('create')
const formModel = ref<LoanRecord>(createDefaultLoanForm())
const detailVisible = ref(false)
const detailRecord = ref<LoanRecord | null>(null)
const reviewLogs = ref<LoanReviewLogRecord[]>([])
const reviewLogLoading = ref(false)
const reviewVisible = ref(false)
const reviewLoanId = ref<number | null>(null)
const reviewLoan = ref<LoanRecord | null>(null)
const reviewDecide = ref<'approve' | 'reject'>('approve')
const returnVisible = ref(false)
const returnLoan = ref<LoanRecord | null>(null)
const pageState = ref<LoanPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<LoanQueryFormState>>(() => createLoanQuerySchema())
/**
 * 表格列与表单字段 schema：按当前表单 itemType 动态切换 assetNo 的 formVisible。
 * - itemType=ASSET：assetNo 可见（资产需记编号）。
 * - itemType=CONSUMABLE：assetNo 隐藏（耗材无资产编号）。
 */
const tableSchema = computed<SharedFieldSchemaMap<LoanRecord>>(() => {
  const base = createLoanSchema()
  if (base.assetNo) {
    base.assetNo.formVisible = formModel.value.itemType === 'ASSET'
  }
  return base
})
const loanFormRules = computed(() => buildLoanFormRules())
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增物品借出' : '编辑物品借出'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建借出' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'approve',
    label: '通过',
    permKey: SYSTEM_PERMISSION_KEYS.audit.loan.approve,
    buttonType: 'primary',
    visible: (row) => String(row.status) === 'REQUEST',
    onClick: async (row) => {
      await openReviewDialog(Number(row.loanId), 'approve')
    },
  },
  {
    key: 'reject',
    label: '驳回',
    permKey: SYSTEM_PERMISSION_KEYS.audit.loan.reject,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'REQUEST',
    onClick: async (row) => {
      await openReviewDialog(Number(row.loanId), 'reject')
    },
  },
  {
    key: 'return',
    label: '归还',
    permKey: SYSTEM_PERMISSION_KEYS.audit.loan.return,
    buttonType: 'success',
    visible: (row) => ['BORROWED', 'OVERDUE'].includes(String(row.status)),
    onClick: async (row) => {
      await openReturnDialog(Number(row.loanId))
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.audit.loan.edit,
    buttonType: 'primary',
    visible: (row) => String(row.status) === 'REQUEST',
    onClick: async (row) => {
      await openEditDialog(Number(row.loanId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.loanId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.audit.loan.delete,
    buttonType: 'danger',
    visible: (row) => ['REQUEST', 'REJECTED'].includes(String(row.status)),
    onClick: async (row) => {
      await handleDeleteLoan(Number(row.loanId))
    },
  },
])

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.subjectName = String(nextValue.subjectName ?? '')
  queryForm.itemType = nextValue.itemType ? String(nextValue.itemType) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.borrowerName = String(nextValue.borrowerName ?? '')
  queryForm.itemName = String(nextValue.itemName ?? '')
  queryForm.pendingReturn = Boolean(nextValue.pendingReturn)
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): LoanListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    subjectName: queryForm.subjectName.trim() || undefined,
    itemType: queryForm.itemType,
    status: queryForm.status,
    borrowerName: queryForm.borrowerName.trim() || undefined,
    itemName: queryForm.itemName.trim() || undefined,
    pendingReturn: queryForm.pendingReturn || undefined,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getAuditLoanPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultLoanQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultLoanForm()
  dialogVisible.value = true
}

const openEditDialog = async (loanId: number) => {
  const result = await getAuditLoanDetailApi(loanId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultLoanForm(),
    ...result.data,
  }
  dialogVisible.value = true
}

const openDetailDialog = async (loanId: number) => {
  const result = await getAuditLoanDetailApi(loanId)

  detailRecord.value = result.data
  detailVisible.value = true

  reviewLogLoading.value = true
  try {
    const logsResult = await getAuditLoanReviewLogApi(loanId)
    reviewLogs.value = logsResult.data ?? []
  } catch {
    reviewLogs.value = []
  } finally {
    reviewLogLoading.value = false
  }
}

const openReviewDialog = async (loanId: number, decide: 'approve' | 'reject') => {
  reviewDecide.value = decide
  reviewLoan.value = await getAuditLoanDetailApi(loanId).then((r) => r.data)
  reviewLoanId.value = loanId
  reviewVisible.value = true
}

const openReturnDialog = async (loanId: number) => {
  returnLoan.value = await getAuditLoanDetailApi(loanId).then((r) => r.data)
  returnVisible.value = true
}

const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    itemType: nextValue.itemType ? String(nextValue.itemType) : formModel.value.itemType,
    borrowerName: String(nextValue.borrowerName ?? formModel.value.borrowerName ?? ''),
    borrowerPhone: String(nextValue.borrowerPhone ?? formModel.value.borrowerPhone ?? ''),
    borrowerOrg:
      nextValue.borrowerOrg != null ? String(nextValue.borrowerOrg) : formModel.value.borrowerOrg,
    borrowerRemark:
      nextValue.borrowerRemark != null
        ? String(nextValue.borrowerRemark)
        : formModel.value.borrowerRemark,
    subjectId:
      nextValue.subjectId != null && nextValue.subjectId !== ''
        ? Number(nextValue.subjectId)
        : formModel.value.subjectId,
    quantity:
      nextValue.quantity != null && nextValue.quantity !== ''
        ? Number(nextValue.quantity)
        : formModel.value.quantity,
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
    const payload: LoanRecord = {
      ...formModel.value,
      itemName: formModel.value.itemName.trim(),
      borrowerName: formModel.value.borrowerName.trim(),
      borrowerPhone: formModel.value.borrowerPhone.trim(),
      borrowerOrg: formModel.value.borrowerOrg?.trim() || undefined,
      borrowerRemark: formModel.value.borrowerRemark?.trim() || undefined,
      assetNo: formModel.value.assetNo?.trim() || '',
      note: formModel.value.note?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createAuditLoanApi(payload)
      ElMessage.success('借出创建成功')
    } else {
      await updateAuditLoanApi(payload)
      ElMessage.success('借出更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

const handleReviewSubmit = async (payload: LoanReviewPayload) => {
  if (payload.pass) {
    await approveAuditLoanApi(payload)
    ElMessage.success('审核通过，物品已借出')
  } else {
    await rejectAuditLoanApi(payload)
    ElMessage.success('已驳回借出申请')
  }
  reviewVisible.value = false
  await fetchPage()
}

const handleReturnSubmit = async (payload: LoanRecord) => {
  await returnAuditLoanApi(payload)
  ElMessage.success('物品已归还')
  returnVisible.value = false
  await fetchPage()
}

const handleDeleteLoan = async (loanId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除物品借出', { type: 'warning' })

  await deleteAuditLoanApi(loanId)
  ElMessage.success('借出记录删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

const formatTime = (value: unknown) => formatDateTime(value) || '--'

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <section class="audit-loan-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="新增借出"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.audit.loan.add"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="物品借出列表">
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
        :form-rules="loanFormRules"
        row-key="loanId"
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
        <!-- 附件：插槽接管为弹窗内直传图片/PDF 的 VoucherUploader，回写 objectId 到 formModel.voucherObjectId（复用流水票据组件） -->
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
      title="物品借出详情"
      width="780px"
      destroy-on-close
      @update:model-value="detailVisible = $event"
    >
      <article v-if="detailRecord" class="audit-loan-detail">
        <h2 class="audit-loan-detail__title">{{ detailRecord.itemName }}</h2>

        <div class="audit-loan-detail__meta">
          <DictTag dict-key="audit_loan_item_type" :value="detailRecord.itemType" />
          <DictTag dict-key="audit_loan_status" :value="detailRecord.status" />
        </div>

        <div class="audit-loan-detail__info">
          <span><em>主体</em>{{ detailRecord.subjectName || detailRecord.subjectId }}</span>
          <span><em>借用人</em>{{ detailRecord.borrowerName || '--' }}</span>
          <span><em>联系电话</em>{{ detailRecord.borrowerPhone || '--' }}</span>
          <span v-if="detailRecord.borrowerOrg"><em>所属单位</em>{{ detailRecord.borrowerOrg }}</span>
          <span><em>数量</em>{{ detailRecord.quantity ?? 1 }}</span>
          <span v-if="detailRecord.assetNo"><em>资产号</em>{{ detailRecord.assetNo }}</span>
          <span><em>借出时间</em>{{ formatTime(detailRecord.borrowDate) }}</span>
          <span><em>预计归还</em>{{ formatTime(detailRecord.expectedReturnDate) }}</span>
          <span><em>实际归还</em>{{ formatTime(detailRecord.actualReturnDate) }}</span>
          <span><em>附件ID</em>{{ detailRecord.voucherObjectId ?? '--' }}</span>
          <span><em>创建时间</em>{{ formatTime(detailRecord.createTime) }}</span>
          <span v-if="detailRecord.borrowerRemark" class="audit-loan-detail__info-full"><em>借用人备注</em>{{ detailRecord.borrowerRemark }}</span>
          <span v-if="detailRecord.note" class="audit-loan-detail__info-full"><em>备注</em>{{ detailRecord.note }}</span>
        </div>

        <div v-if="detailRecord.voucherObjectId" class="audit-loan-detail__voucher">
          <em>附件</em>
          <ElLink type="primary" :underline="false" @click="downloadVoucher(detailRecord.voucherObjectId)">
            点击下载 (附件 #{{ detailRecord.voucherObjectId }})
          </ElLink>
        </div>

        <ElCollapse v-if="reviewLogs.length > 0" class="audit-loan-detail__review-log">
          <ElCollapseItem title="审核历史" name="review-log">
            <ul class="audit-loan-detail__timeline">
              <li v-for="log in reviewLogs" :key="log.reviewLogId" class="audit-loan-detail__timeline-item">
                <div class="audit-loan-detail__timeline-head">
                  <DictTag dict-key="review_action" :value="log.action" />
                  <span class="audit-loan-detail__timeline-operator">{{ log.operatorNickname || log.operator }}</span>
                  <span class="audit-loan-detail__timeline-time">{{ formatTime(log.createTime) }}</span>
                </div>
                <p v-if="log.advice" class="audit-loan-detail__timeline-advice">{{ log.advice }}</p>
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
        <ElEmpty
          v-else-if="!reviewLogLoading && detailVisible"
          class="audit-loan-detail__review-empty"
          description="暂无审核历史"
          :image-size="48"
        />
      </article>

      <template #footer>
        <div class="audit-loan-detail__footer">
          <ElButton @click="detailVisible = false">关闭</ElButton>
        </div>
      </template>
    </ElDialog>

    <LoanReviewDialog
      :visible="reviewVisible"
      :loan-id="reviewLoanId"
      :loan="reviewLoan"
      :decide="reviewDecide"
      @update:visible="reviewVisible = $event"
      @submit="handleReviewSubmit"
    />

    <LoanReturnDialog
      :visible="returnVisible"
      :loan="returnLoan"
      @update:visible="returnVisible = $event"
      @submit="handleReturnSubmit"
    />
  </section>
</template>

<script lang="ts">
export default { name: 'AuditLoanView' }
</script>

<style scoped>
.audit-loan-view {
  display: grid;
  gap: 18px;
}

.audit-loan-detail {
  display: grid;
  gap: 16px;
}

.audit-loan-detail__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--rookie-text);
}

.audit-loan-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.audit-loan-detail__info {
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

.audit-loan-detail__info-full {
  grid-column: 1 / -1;
}

.audit-loan-detail__voucher {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.audit-loan-detail__voucher em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
}

.audit-loan-detail__info em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.audit-loan-detail__review-log {
  border-top: 1px solid var(--rookie-border);
  padding-top: 8px;
}

.audit-loan-detail__timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.audit-loan-detail__timeline-item {
  padding: 8px 12px;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
}

.audit-loan-detail__timeline-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: var(--rookie-font-size-sm);
}

.audit-loan-detail__timeline-operator {
  color: var(--rookie-text);
  font-weight: 500;
}

.audit-loan-detail__timeline-time {
  color: var(--rookie-text-tertiary);
}

.audit-loan-detail__timeline-advice {
  margin: 6px 0 0;
  padding: 6px 10px;
  border-left: 2px solid var(--rookie-border);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-loan-detail__review-empty {
  padding: 12px 0;
}

.audit-loan-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>