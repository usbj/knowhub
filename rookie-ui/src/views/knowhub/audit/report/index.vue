<!--
  文件作用：
  承接周期报表管理页（后台菜单"周期报表"），负责月度/周记报表的列表查询、分页展示、
  只读详情（含花销分类汇总 expenseByCategory JSON 解析展示）与手动重算（仅已结束期可重算）。
  关键状态：
  - `queryForm`：当前查询条件；`pageState`：分页结果；`detailVisible` / `detailRecord`：详情弹窗。
  关键依赖：
  - 复用公共表格、筛选面板，对齐博客/资源/流水/借出管理页结构。
  - periodType 走字典（dictKey 自动渲染 DictTag/下拉），generateTime 走 daterange。
  - 报表由定时任务生成，无 add/edit，故无新增/编辑弹窗；仅详情弹窗 + 行内重算动作。
  - 重算按钮当期（periodEnded===false）disabled + ElTooltip 提示"当前期未结束"，仅已结束期可点。
  - expenseByCategory 是 JSON 字符串（如 {"耗材":100.00}），详情弹窗里 JSON.parse 为分类列表展示。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTooltip } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import {
  getAuditReportDetailApi,
  getAuditReportPageApi,
  regenerateAuditReportApi,
} from '@/api/knowhub/audit'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import ReportRegenerateDialog from './components/ReportRegenerateDialog.vue'
import PeriodFlowListDialog from './components/PeriodFlowListDialog.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { formatDate, formatDateTime } from '@/utils/format'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  ReportListQuery,
  ReportPageResult,
  ReportRecord,
  ReportRegeneratePayload,
} from '@/types/api/knowhub/audit'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultReportQuery,
  createReportQuerySchema,
  createReportSchema,
  formatReportMoney,
  type ReportQueryFormState,
} from './config'

const queryForm = reactive<ReportQueryFormState>(createDefaultReportQuery())
const listLoading = ref(false)
const regenerateLoading = ref(false)
const detailVisible = ref(false)
const detailRecord = ref<ReportRecord | null>(null)
/** 重新生成弹窗状态 */
const regenerateVisible = ref(false)
/**
 * 「本期流水明细」弹窗状态 + 透传摘要。
 * 两种入口复用同一弹窗：① 重新生成后自动弹（展示新生成期数据）
 *                      ② 报表详情弹窗点「查看流水明细」手动弹（已有报表随时可看明细）。
 */
const flowListVisible = ref(false)
const flowListReport = ref<ReportRecord | null>(null)
const flowListPeriodLabel = ref('')
const pageState = ref<ReportPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<ReportQueryFormState>>(() => createReportQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<ReportRecord>>(() => createReportSchema())
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'regenerate',
    label: '重算',
    permKey: SYSTEM_PERMISSION_KEYS.audit.report.regenerate,
    buttonType: 'primary',
    // 当期（periodEnded===false）禁用重算按钮（视觉灰显）；详情弹窗内再用 ElTooltip 提示原因
    disabled: (row) => row.periodEnded === false,
    onClick: async (row) => {
      await handleRegenerate(row as unknown as ReportRecord)
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.reportId))
    },
  },
])

/**
 * 详情弹窗花销分类汇总：JSON.parse(expenseByCategory) → [category, amount] 列表
 * 失败/空回退空数组，弹窗展示空态。
 */
const expenseCategoryList = computed<Array<{ category: string; amount: number }>>(() => {
  const raw = detailRecord.value?.expenseByCategory
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    return Object.entries(parsed).map(([category, amount]) => ({
      category,
      amount: Number(amount) || 0,
    }))
  } catch {
    return []
  }
})

/** 详情弹窗重算按钮是否禁用（当期 periodEnded===false 时禁用） */
const detailRegenerateDisabled = computed(() => detailRecord.value?.periodEnded === false)

const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.subjectName = String(nextValue.subjectName ?? '')
  queryForm.periodType = nextValue.periodType ? String(nextValue.periodType) : undefined
  queryForm.periodKey = String(nextValue.periodKey ?? '')
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): ReportListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    subjectName: queryForm.subjectName.trim() || undefined,
    periodType: queryForm.periodType,
    periodKey: queryForm.periodKey.trim() || undefined,
    beginTime,
    endTime,
  }
}

const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getAuditReportPageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultReportQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

const openDetailDialog = async (reportId: number) => {
  const result = await getAuditReportDetailApi(reportId)

  detailRecord.value = result.data
  detailVisible.value = true
}

const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 按三键（subjectId/periodType/periodKey）重算报表，成功后用列表拉新报表记录，
 * 取其 periodStart/periodEnd + 摘要字段打开「本期流水明细」弹窗展示具体流水。
 * 参数：
 * - `payload`：重算三键入参。
 */
const doRegenerateAndShowFlows = async (payload: ReportRegeneratePayload) => {
  regenerateLoading.value = true
  try {
    await regenerateAuditReportApi(payload)
    ElMessage.success('报表生成成功')
    await fetchPage()
    // 用三键拉新报表记录取 periodStart/periodEnd + 摘要（regenerate 返 Boolean，需另查记录）
    const page = await getAuditReportPageApi({
      subjectName: undefined,
      periodType: payload.periodType,
      periodKey: payload.periodKey,
      pageNum: 1,
      pageSize: 50,
    })
    const matched = (page.records ?? []).find(
      (r) => Number(r.subjectId) === Number(payload.subjectId),
    )
    if (matched) {
      flowListReport.value = matched
      flowListPeriodLabel.value = `${payload.periodType === 'MONTH' ? '月度' : '周记'} ${payload.periodKey}`
      flowListVisible.value = true
    }
    // 若详情弹窗打开且为同记录，同步刷新详情
    if (
      detailVisible.value &&
      detailRecord.value &&
      detailRecord.value.subjectId === payload.subjectId &&
      detailRecord.value.periodType === payload.periodType &&
      detailRecord.value.periodKey === payload.periodKey
    ) {
      const result = await getAuditReportDetailApi(detailRecord.value.reportId as number)
      detailRecord.value = result.data
    }
  } finally {
    regenerateLoading.value = false
  }
}

/** 行内「重算」按钮：当期禁用守卫 + 确认框 + 重算 + 流水明细弹窗 */
const handleRegenerate = async (record: ReportRecord) => {
  await ElMessageBox.confirm(
    `确认重新统计 ${record.periodKey} 该期的报表数据吗？重算将覆盖当前已生成报表。`,
    '重算周期报表',
    { type: 'warning' },
  )
  await doRegenerateAndShowFlows({
    subjectId: record.subjectId,
    periodType: record.periodType,
    periodKey: record.periodKey,
  })
}

/**
 * 方法效果：
 * 顶部「重新生成」按钮打开选主体+周期类型的弹窗，提交后走重算+流水明细流程。
 */
const handleRegenerateSubmit = async (payload: ReportRegeneratePayload) => {
  regenerateVisible.value = false
  await doRegenerateAndShowFlows(payload)
}

/** 详情弹窗内的重算按钮点击（带 disabled 守卫 + tooltip 提示） */
const handleDetailRegenerate = async () => {
  if (!detailRecord.value || detailRegenerateDisabled.value) {
    return
  }
  await handleRegenerate(detailRecord.value)
}

/**
 * 方法效果：
 * 报表详情弹窗内「查看流水明细」按钮：透传 detailRecord 与周期文本打开 PeriodFlowListDialog，
 * 让已有报表随时可看明细（不强制走重新生成），复用与重算后同款弹窗。
 */
const handleDetailShowFlows = () => {
  if (!detailRecord.value) {
    return
  }
  flowListReport.value = detailRecord.value
  flowListPeriodLabel.value = `${detailRecord.value.periodType === 'MONTH' ? '月度' : '周记'} ${detailRecord.value.periodKey}`
  flowListVisible.value = true
}

const formatTime = (value: unknown) => formatDateTime(value) || '--'
const formatDay = (value: unknown) => formatDate(value) || '--'

onMounted(async () => {
  await fetchPage()
})
</script>

<template>
  <section class="audit-report-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="84px"
        create-button-text="重新生成"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.audit.report.regenerate"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="regenerateVisible = true"
      />
      <p class="audit-report-view__hint">报表由定时任务自动生成，可点「重新生成」按主体 + 周期类型 + 具体周期手动生成/重算已结束期。</p>
    </BaseCard>

    <BaseCard title="周期报表列表">
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="false"
        row-key="reportId"
        @pagination-change="handlePaginationChange"
      />
    </BaseCard>

    <ElDialog
      :model-value="detailVisible"
      title="周期报表详情"
      width="820px"
      destroy-on-close
      @update:model-value="detailVisible = $event"
    >
      <article v-if="detailRecord" class="audit-report-detail">
        <header class="audit-report-detail__header">
          <h2 class="audit-report-detail__title">
            <DictTag dict-key="audit_period_type" :value="detailRecord.periodType" />
            {{ detailRecord.periodKey }}
          </h2>
          <p class="audit-report-detail__subject">
            主体：{{ detailRecord.subjectName || detailRecord.subjectId }}
            <span v-if="detailRecord.handlerNickname">（负责人：{{ detailRecord.handlerNickname }}）</span>
          </p>
          <p class="audit-report-detail__period">
            周期：{{ formatDay(detailRecord.periodStart) }} ~ {{ formatDay(detailRecord.periodEnd) }}
            <span
              class="audit-report-detail__period-state"
              :class="detailRecord.periodEnded ? 'is-ended' : 'is-current'"
            >
              {{ detailRecord.periodEnded ? '已结束' : '当前期未结束' }}
            </span>
          </p>
        </header>

        <div class="audit-report-detail__metrics">
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">本期预算</span>
            <span class="audit-report-detail__metric-value">{{ formatReportMoney(detailRecord.budgetAmount) }}</span>
          </div>
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">本期实到</span>
            <span class="audit-report-detail__metric-value">{{ formatReportMoney(detailRecord.incomeAmount) }}</span>
          </div>
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">本期已花</span>
            <span class="audit-report-detail__metric-value">{{ formatReportMoney(detailRecord.expenseAmount) }}</span>
          </div>
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">期末结余</span>
            <span class="audit-report-detail__metric-value">{{ formatReportMoney(detailRecord.balanceEnd) }}</span>
          </div>
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">本期借出</span>
            <span class="audit-report-detail__metric-value">{{ detailRecord.loanOutCount ?? 0 }} 笔</span>
          </div>
          <div class="audit-report-detail__metric">
            <span class="audit-report-detail__metric-label">未归还</span>
            <span class="audit-report-detail__metric-value">{{ detailRecord.loanUnreturned ?? 0 }} 笔</span>
          </div>
        </div>

        <section class="audit-report-detail__category">
          <h3 class="audit-report-detail__category-title">花销分类汇总</h3>
          <ElTable
            v-if="expenseCategoryList.length > 0"
            :data="expenseCategoryList"
            border
            size="small"
            class="audit-report-detail__category-table"
          >
            <ElTableColumn prop="category" label="分类" min-width="160" />
            <ElTableColumn label="金额（元）" min-width="140" align="right">
              <template #default="{ row }">
                {{ formatReportMoney(row.amount) }}
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty
            v-else
            description="本期无已通过花销"
            :image-size="48"
            class="audit-report-detail__category-empty"
          />
        </section>

        <p class="audit-report-detail__generate-time">
          生成/重算时间：{{ formatTime(detailRecord.generateTime) }}
        </p>
      </article>

      <template #footer>
        <div class="audit-report-detail__footer">
          <ElButton type="primary" plain @click="handleDetailShowFlows">
            查看流水明细
          </ElButton>
          <ElTooltip
            :disabled="!detailRegenerateDisabled"
            content="当前期未结束，不可重算"
            placement="top"
          >
            <span>
              <ElButton
                type="primary"
                :loading="regenerateLoading"
                :disabled="detailRegenerateDisabled"
                @click="handleDetailRegenerate"
              >
                重算报表
              </ElButton>
            </span>
          </ElTooltip>
          <ElButton @click="detailVisible = false">关闭</ElButton>
        </div>
      </template>
    </ElDialog>

    <ReportRegenerateDialog
      :visible="regenerateVisible"
      @update:visible="regenerateVisible = $event"
      @submit="handleRegenerateSubmit"
    />

    <PeriodFlowListDialog
      :visible="flowListVisible"
      :subject-id="flowListReport?.subjectId"
      :subject-name="flowListReport?.subjectName"
      :period-start="flowListReport?.periodStart"
      :period-end="flowListReport?.periodEnd"
      :period-label="flowListPeriodLabel"
      :budget-amount="flowListReport?.budgetAmount"
      :income-amount="flowListReport?.incomeAmount"
      :expense-amount="flowListReport?.expenseAmount"
      :balance-end="flowListReport?.balanceEnd"
      :loan-out-count="flowListReport?.loanOutCount"
      :loan-unreturned="flowListReport?.loanUnreturned"
      @update:visible="flowListVisible = $event"
    />
  </section>
</template>

<script lang="ts">
export default { name: 'AuditReportView' }
</script>

<style scoped>
.audit-report-view {
  display: grid;
  gap: 18px;
}

.audit-report-view__hint {
  margin: 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.audit-report-detail {
  display: grid;
  gap: 18px;
}

.audit-report-detail__header {
  display: grid;
  gap: 6px;
}

.audit-report-detail__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--rookie-text);
}

.audit-report-detail__subject,
.audit-report-detail__period {
  margin: 0;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.audit-report-detail__period-state {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: var(--rookie-radius-sm);
  font-size: var(--rookie-font-size-sm);
}

.audit-report-detail__period-state.is-ended {
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-tertiary);
  border: 1px solid var(--rookie-border);
}

.audit-report-detail__period-state.is-current {
  background: var(--rookie-warning-bg, #fdf6ec);
  color: var(--rookie-warning-text, #e6a23c);
  border: 1px solid var(--rookie-warning-text, #e6a23c);
}

.audit-report-detail__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.audit-report-detail__metric {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
}

.audit-report-detail__metric-label {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-tertiary);
}

.audit-report-detail__metric-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--rookie-text);
}

.audit-report-detail__category {
  display: grid;
  gap: 8px;
}

.audit-report-detail__category-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--rookie-text);
}

.audit-report-detail__category-empty {
  padding: 12px 0;
}

.audit-report-detail__generate-time {
  margin: 0;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-tertiary);
}

.audit-report-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>