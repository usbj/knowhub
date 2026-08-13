<!--
  文件作用：
  审计模块通用的"选人"下拉组件，供主体负责人/流水经办人/借出借用人字段在弹窗表单里
  接管 #field-handlerId / #field-borrowerId 插槽使用。
  关键约定：
  - 复用系统用户分页接口 getSysUserPageApi（GET /sys/user/list），按昵称模糊搜索，pageSize=20。
  - 单选：value 为 userId（number | undefined），回写 update:model-value 发 userId。
  - 回显：传入已存在的 userId 且不在当前选项时，按 userId 拉详情补一条占位选项显示昵称。
  - 不改 rookie-ui 原有组件，只在 knowhub 命名空间内新增，沿用 blog 页 #field-* 插槽接管范式。
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElOption, ElSelect } from 'element-plus'
import { getSysUserDetailApi, getSysUserPageApi } from '@/api/system/user'
import type { SysUserFormData } from '@/types/api/system/user'

const props = defineProps<{
  /** 当前选中的 userId（number | undefined） */
  modelValue: number | undefined
  /** 占位文案，默认"搜索昵称选择用户" */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

interface UserOption {
  value: number
  label: string
  /** 用户名（辅助展示，避免和昵称混淆） */
  username: string
}

const innerValue = ref<number | undefined>(props.modelValue)
const options = ref<UserOption[]>([])
const loading = ref(false)

/**
 * 把后端 SysUserFormData 映射为下拉选项。
 * label 用"昵称（用户名）"，无昵称时退化为用户名，便于同名区分。
 */
const toOption = (user: SysUserFormData): UserOption => ({
  value: Number(user.userId),
  label: user.nickName ? `${user.nickName}（${user.username}）` : user.username,
  username: user.username,
})

/**
 * 方法效果：
 * 按昵称关键词模糊搜索用户，关键词为空时不拉全量（避免无筛选刷屏），仅清空选项。
 */
const handleSearch = async (keyword: string) => {
  const trimmed = keyword.trim()
  if (!trimmed) {
    options.value = []
    return
  }
  loading.value = true
  try {
    const page = await getSysUserPageApi({ nickName: trimmed, pageNum: 1, pageSize: 20 })
    options.value = page.records.map(toOption)
  } finally {
    loading.value = false
  }
}

/**
 * 方法效果：
 * 拉单个用户详情并补一条选项，用于编辑弹窗打开时按已存 userId 回显昵称。
 * 拉取失败则兜底显示 userId，避免选人框空白不可读。
 */
const ensureOptionForValue = async (userId: number) => {
  if (options.value.some((item) => item.value === userId)) {
    return
  }
  try {
    const result = await getSysUserDetailApi(userId)
    if (result.data) {
      options.value = [toOption(result.data), ...options.value]
    }
  } catch {
    options.value = [{ value: userId, label: `用户${userId}`, username: String(userId) }]
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
    :placeholder="placeholder ?? '搜索昵称选择用户'"
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