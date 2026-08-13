<!--
  文件作用：
  报表「重新生成」弹窗：选择 主体 + 周期类型（MONTH/WEEK）+ 具体周期，确定后回填三键定位，
  供 report/index.vue 调 regenerateAuditReportApi 生成（重算）该期报表。
  关键约定：
  - 主体选择：ElSelect remote 按主体名搜（getAuditSubjectPageApi({ name })），回写 subjectId（对齐用户 req1「名称搜索选择后确认ID」）。
  - 周期类型：ElRadioGroup MONTH/WEEK；切换时重算候选周期列表。
  - 具体周期下拉：
    - MONTH：向前 12 个已结束月（不含当月）；label "2026-07（07-01 ~ 07-31）"。
    - WEEK：向前 48 个已结束 ISO 周（不含当周）；label "2026-W32（08-03 ~ 08-09）"。
  - periodKey 格式严格复刻后端：MONTH "yyyy-MM"、WEEK "yyyy-Www"（两位补零的 ISO 周号）。
  - ISO 周算法无 date-fns 依赖，手撸对齐后端 java IsoFields（jan4 anchor + week-based-year）。
  - 当期不在候选内，无需禁用；后端 regenerate 复核 period_end<now，误传会被拒并弹 message。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElMessage, ElOption, ElRadio, ElRadioGroup, ElSelect } from 'element-plus'
import { getAuditSubjectPageApi } from '@/api/knowhub/audit'
import type { ReportRegeneratePayload } from '@/types/api/knowhub/audit'

defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: ReportRegeneratePayload]
}>()

interface SubjectOption {
  value: number
  label: string
}

const subjectId = ref<number | undefined>(undefined)
const subjectOptions = ref<SubjectOption[]>([])
const subjectLoading = ref(false)
const periodType = ref<'MONTH' | 'WEEK' | ''>('')
const periodKey = ref<string>('')
const submitting = ref(false)

/**
 * 方法效果：
 * pad2：补零到两位（月/周号展示用）。
 */
const pad2 = (n: number): string => (n < 10 ? `0${n}` : String(n))

/**
 * 方法效果：
 * 计算 y 年 m 月（m: 0-11）的首日与末日（Date 复制返回，含/含）。
 */
const monthRange = (y: number, m: number): { start: Date; end: Date } => {
  const start = new Date(y, m, 1)
  const end = new Date(y, m + 1, 0) // 月末日（day=0 → 上月最后一天）
  return { start, end }
}

/**
 * 方法效果：
 * ISO 周算法：给定一个 Date，返回其 ISO week-based-year 与 ISO 周号（1-53）。
 * 算法对齐后端 java.time IsoFields（week Monday~Sunday，第 1 周含该年第一个 Thursday，即锚定 1/4）。
 * 用 jan4 anchor 法：取该年 1/4 的 Monday（ISO 周一）为第 1 周起点，计算 |now - anchorMonday|/7 判定是否落到上一年收尾周。
 */
const isoWeek = (date: Date): { weekYear: number; week: number } => {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const thursday = new Date(d)
  thursday.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7)) // 当周的 Thursday（ISO 周语义锚点所在年）
  const weekYear = thursday.getFullYear()
  const jan4 = new Date(weekYear, 0, 4)
  const jan4Monday = new Date(jan4)
  jan4Monday.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7)) // week-based-year 的第 1 周 Monday
  const diffDays = Math.round((thursday.getTime() - jan4Monday.getTime()) / 86400000)
  const week = Math.floor(diffDays / 7) + 1
  return { weekYear, week }
}

/**
 * 方法效果：
 * 根据 weekYear + ISO week 号反算该周的 Monday 与 Sunday（ISO 周起止）。
 * 锚 jan4 → 取其所在 ISO 周 Monday → 偏移 (week-1)*7 天得目标周 Monday，Sunday = Monday + 6。
 */
const isoWeekRange = (weekYear: number, week: number): { start: Date; end: Date } => {
  const jan4 = new Date(weekYear, 0, 4)
  const jan4Monday = new Date(jan4)
  jan4Monday.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7))
  const start = new Date(jan4Monday)
  start.setDate(jan4Monday.getDate() + (week - 1) * 7)
  const end = new Date(start)
  end.setDate(start.getDate() + 6)
  return { start, end }
}

/** fmt：yyyy-MM-dd */
const fmtDate = (d: Date): string =>
  `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`

interface PeriodCandidate {
  periodKey: string
  label: string
}

/**
 * 方法效果：
 * 生成「具体周期」下拉候选（不含当期）：
 * - MONTH：今天所在月往前推 12 个月（不含当月），label 附该月首末日。
 * - WEEK：今天所在 ISO 周往前推 48 周（不含当周），label 附该周首末日（ISO Monday~Sunday）。
 * 参数：
 * - type：'MONTH' | 'WEEK'。
 * 返回值：
 * - 候选列表（最近一期在前）。
 */
const buildCandidates = (type: 'MONTH' | 'WEEK'): PeriodCandidate[] => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const list: PeriodCandidate[] = []
  if (type === 'MONTH') {
    for (let i = 1; i <= 12; i++) {
      const ref = new Date(today.getFullYear(), today.getMonth() - i, 1)
      const y = ref.getFullYear()
      const m = ref.getMonth()
      const { start, end } = monthRange(y, m)
      const key = `${y}-${pad2(m + 1)}`
      list.push({
        periodKey: key,
        label: `${key}（${fmtDate(start)} ~ ${fmtDate(end)}）`,
      })
    }
  } else {
    for (let i = 1; i <= 48; i++) {
      const ref = new Date(today)
      ref.setDate(today.getDate() - i * 7)
      const { weekYear, week } = isoWeek(ref)
      const { start, end } = isoWeekRange(weekYear, week)
      const key = `${weekYear}-W${pad2(week)}`
      list.push({
        periodKey: key,
        label: `${key}（${fmtDate(start)} ~ ${fmtDate(end)}）`,
      })
    }
  }
  return list
}

const periodCandidates = computed<PeriodCandidate[]>(() => {
  if (periodType.value === 'MONTH' || periodType.value === 'WEEK') {
    return buildCandidates(periodType.value)
  }
  return []
})

/**
 * 方法效果：
 * 按主体名关键词 remote 搜索主体（getAuditSubjectPageApi({ name })），回填 subjectId 选项。
 */
const handleSubjectSearch = async (keyword: string) => {
  const trimmed = keyword.trim()
  if (!trimmed) {
    subjectOptions.value = []
    return
  }
  subjectLoading.value = true
  try {
    const page = await getAuditSubjectPageApi({ name: trimmed, pageNum: 1, pageSize: 20 })
    subjectOptions.value = (page.records ?? []).map((s) => ({
      value: Number(s.subjectId),
      label: String(s.name ?? s.subjectId),
    }))
  } finally {
    subjectLoading.value = false
  }
}

const handlePeriodTypeChange = () => {
  periodKey.value = ''
}

/**
 * 方法效果：
 * 提交重算：校验三键齐备后 emit submit(payload)。
 */
const handleSubmit = async () => {
  if (subjectId.value == null) {
    ElMessage.warning('请选择要生成报表的主体')
    return
  }
  if (!periodType.value) {
    ElMessage.warning('请选择周期类型')
    return
  }
  if (!periodKey.value) {
    ElMessage.warning('请选择具体周期')
    return
  }
  submitting.value = true
  try {
    emit('submit', {
      subjectId: subjectId.value,
      periodType: periodType.value,
      periodKey: periodKey.value,
    })
  } finally {
    submitting.value = false
  }
}

const handleCancel = () => {
  emit('update:visible', false)
}

// 弹窗关闭后清空内部态（避免重复打开残留）
const resetState = () => {
  subjectId.value = undefined
  subjectOptions.value = []
  periodType.value = ''
  periodKey.value = ''
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="重新生成周期报表"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @closed="resetState"
  >
    <ElForm label-width="92px" class="report-regenerate__form">
      <ElFormItem label="主体" required>
        <ElSelect
          v-model="subjectId"
          filterable
          remote
          clearable
          :loading="subjectLoading"
          placeholder="搜索主体名选择"
          :remote-method="handleSubjectSearch"
          style="width: 100%"
        >
          <ElOption
            v-for="item in subjectOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="周期类型" required>
        <ElRadioGroup v-model="periodType" @change="handlePeriodTypeChange">
          <ElRadio value="MONTH">月度</ElRadio>
          <ElRadio value="WEEK">周记</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem v-if="periodType" label="具体周期" required>
        <ElSelect
          v-model="periodKey"
          filterable
          clearable
          placeholder="选择要生成的周期"
          style="width: 100%"
        >
          <ElOption
            v-for="item in periodCandidates"
            :key="item.periodKey"
            :label="item.label"
            :value="item.periodKey"
          />
        </ElSelect>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="handleSubmit">
        生成报表
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.report-regenerate__form {
  display: grid;
  gap: 14px;
}
</style>