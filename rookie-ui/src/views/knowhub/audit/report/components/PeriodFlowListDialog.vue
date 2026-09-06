<!--
  文件作用：
  报表生成/重算完毕后弹出的「本期流水明细」弹窗：按 subjectId + 周期起止拉该主体本期
  APPROVED 流水（getAuditFlowPageApi），顶部展示 6 项摘要数字卡 + 下方只读流水表。
  关键约定：
  - 摘要卡数据由父层从报表记录透传（budget/income/expense/balanceEnd/loanOutCount/loanUnreturned），
    避免 Dialog 内重复算报表汇总（后端 regenerateReport 已算并落库）。
  - 流水列表拉取参数：subjectId 精确、status=APPROVED、beginTime=periodStart、endTime=periodEnd、pageSize=100。
    FundFlowListQuery 已支持 subjectId 精确 + status + occur_date 范围。
  - 流水表只读（不用 SharedTablePanel，避免带编辑表单过重），展示 flowType/amount/occurDate/category/handlerNickname/note。
  - 金额 formatReportMoney 与列表页一致（两位小数）。
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { getAuditFlowPageApi } from '@/api/knowhub/audit'
import { formatDate } from '@/utils/format'
import type { FundFlowRecord } from '@/types/api/knowhub/audit'
import { formatReportMoney } from '../config'

const props = defineProps<{
  visible: boolean
  subjectId: number | undefined
  subjectName: string | undefined
  periodStart: string | undefined
  periodEnd: string | undefined
  periodLabel?: string
  budgetAmount?: number
  incomeAmount?: number
  expenseAmount?: number
  balanceEnd?: number
  loanOutCount?: number
  loanUnreturned?: number
}>()

defineEmits<{ 'update:visible': [value: boolean] }>()

const flows = ref<FundFlowRecord[]>([])
const loading = ref(false)

/**
 * 方法效果：
 * 按 subjectId + 周期起止拉该主体本期 APPROVED 流水（pageSize=100 足覆盖单期体量）。
 * 触发时机：visible 由 false→true 且 subjectId/periodStart/periodEnd 俱备时。
 */
const fetchFlows = async () => {
  if (props.subjectId == null || !props.periodStart || !props.periodEnd) {
    flows.value = []
    return
  }
  loading.value = true
  try {
    const page = await getAuditFlowPageApi({
      subjectId: props.subjectId,
      status: 'APPROVED',
      beginTime: formatDate(props.periodStart) || undefined,
      endTime: formatDate(props.periodEnd) || undefined,
      pageNum: 1,
      pageSize: 100,
    })
    flows.value = page.records ?? []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (next) => {
    if (next) {
      void fetchFlows()
    }
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="`${subjectName ?? ''} ${periodLabel ?? ''} 流水明细`"
    width="860px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <article class="period-flow-list">
      <!-- 顶部 6 项摘要数字卡 -->
      <div class="period-flow-list__metrics">
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">本期预算</span>
          <span class="period-flow-list__metric-value">{{ formatReportMoney(budgetAmount) }}</span>
        </div>
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">本期实到</span>
          <span class="period-flow-list__metric-value">{{ formatReportMoney(incomeAmount) }}</span>
        </div>
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">本期已花</span>
          <span class="period-flow-list__metric-value">{{ formatReportMoney(expenseAmount) }}</span>
        </div>
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">期末结余</span>
          <span class="period-flow-list__metric-value">{{ formatReportMoney(balanceEnd) }}</span>
        </div>
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">本期借出</span>
          <span class="period-flow-list__metric-value">{{ loanOutCount ?? 0 }} 笔</span>
        </div>
        <div class="period-flow-list__metric">
          <span class="period-flow-list__metric-label">未归还</span>
          <span class="period-flow-list__metric-value">{{ loanUnreturned ?? 0 }} 笔</span>
        </div>
      </div>

      <p class="period-flow-list__period">
        周期：{{ formatDate(periodStart) || '--' }} ~ {{ formatDate(periodEnd) || '--' }}
      </p>

      <!-- 只读流水明细表 -->
      <ElTable
        v-loading="loading"
        :data="flows"
        border
        size="small"
        max-height="420"
        class="period-flow-list__table"
      >
        <ElTableColumn label="流水类型" width="100">
          <template #default="{ row }">
            <DictTag dict-key="audit_flow_type" :value="row.flowType" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="分类" width="110">
          <template #default="{ row }">
            <DictTag v-if="row.category" dict-key="audit_expense_category" :value="row.category" />
            <span v-else>--</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="金额（元）" min-width="120" align="right">
          <template #default="{ row }">
            {{ formatReportMoney(row.amount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="发生日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.occurDate) || '--' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="经办人" min-width="120">
          <template #default="{ row }">
            {{ row.handlerNickname || row.handlerId || '--' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.note || '--' }}
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="本期无已通过流水" :image-size="48" />
        </template>
      </ElTable>
    </article>
  </ElDialog>
</template>

<style scoped>
.period-flow-list {
  display: grid;
  gap: 14px;
}

.period-flow-list__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.period-flow-list__metric {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
}

.period-flow-list__metric-label {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-tertiary);
}

.period-flow-list__metric-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--rookie-text);
}

.period-flow-list__period {
  margin: 0;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.period-flow-list__table {
  width: 100%;
}
</style>