<!--
  文件作用：
  审计模块通用的"选主体"下拉组件，供流水/借出弹窗表单的 subjectId 字段
  接管 #field-subjectId 插槽使用（弹窗内不手填 subjectId 数字，改为按主体名搜索选择确认 ID）。
  关键约定：
  - 复用主体分页接口 getAuditSubjectPageApi（GET /audit/subject/list），按 name 模糊搜索，pageSize=20。
  - 单选：value 为 subjectId（number | undefined），回写 update:model-value 发 subjectId。
  - 回显：编辑弹窗打开时传入已存 subjectId 且不在当前选项时，按 subjectId 拉详情补一条占位选项显示主体名。
  - 不改 rookie-ui 原有组件，只在 knowhub 命名空间内新增，沿用 UserPicker 的 #field-* 插槽接管范式。
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElOption, ElSelect } from 'element-plus'
import { getAuditSubjectDetailApi, getAuditSubjectPageApi } from '@/api/knowhub/audit'
import type { SubjectRecord } from '@/types/api/knowhub/audit'

const props = defineProps<{
  /** 当前选中的 subjectId（number | undefined） */
  modelValue: number | undefined
  /** 占位文案，默认"搜索主体名选择" */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

interface SubjectOption {
  value: number
  label: string
}

const innerValue = ref<number | undefined>(props.modelValue)
const options = ref<SubjectOption[]>([])
const loading = ref(false)

/**
 * 把后端 SubjectRecord 映射为下拉选项。
 * label 用主体名，无名称时退化为 #subjectId，保证可读。
 */
const toOption = (subject: SubjectRecord): SubjectOption => ({
  value: Number(subject.subjectId),
  label: subject.name ? subject.name : `主体#${subject.subjectId}`,
})

/**
 * 方法效果：
 * 按主体名关键词模糊搜索，关键词为空时不拉全量（避免无筛选刷屏），仅清空选项。
 */
const handleSearch = async (keyword: string) => {
  const trimmed = keyword.trim()
  if (!trimmed) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const page = await getAuditSubjectPageApi({ name: trimmed, pageNum: 1, pageSize: 20 })
    options.value = (page.records ?? []).map(toOption)
  } finally {
    loading.value = false
  }
}

/**
 * 方法效果：
 * 拉单个主体详情并补一条选项，用于编辑弹窗打开时按已存 subjectId 回显主体名。
 * 拉取失败则兜底显示 subjectId，避免选主体框空白不可读。
 */
const ensureOptionForValue = async (subjectId: number) => {
  if (options.value.some((item) => item.value === subjectId)) {
    return
  }
  try {
    const result = await getAuditSubjectDetailApi(subjectId)
    if (result.data) {
      options.value = [toOption(result.data), ...options.value]
    }
  } catch {
    options.value = [{ value: subjectId, label: `主体#${subjectId}` }]
  }
}

watch(
  () => props.modelValue,
  (next) => {
    innerValue.value = next
    if (next != null) {
      void ensureOptionForValue(next)
    }
  },
  { immediate: true },
)

const handleChange = (value: number | undefined) => {
  innerValue.value = value
  emit('update:modelValue', value)
}
</script>

<template>
  <ElSelect
    :model-value="innerValue"
    filterable
    remote
    clearable
    :loading="loading"
    :placeholder="placeholder ?? '搜索主体名选择'"
    :disabled="disabled"
    :remote-method="handleSearch"
    style="width: 100%"
    @update:model-value="handleChange"
  >
    <ElOption
      v-for="item in options"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </ElSelect>
</template>