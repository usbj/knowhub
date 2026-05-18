<script setup lang="ts">
/**
 * 文件作用：
 * 为列表页提供更紧凑的筛选条件组件，
 * 在字段配置能力上与公共表单保持一致，但默认更适合查询场景。
 */
import { ElButton } from 'element-plus'
import { computed } from 'vue'
import SharedFormPanel from '@/components/SharedFormPanel.vue'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'

const props = withDefaults(
  defineProps<{
    schema: SharedFieldSchemaMap
    modelValue: Record<string, unknown>
    columns?: number
    labelWidth?: string
    loading?: boolean
  }>(),
  {
    columns: 5,
    labelWidth: '72px',
    loading: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  search: [value: Record<string, unknown>]
  reset: []
  create: []
}>()

/**
 * 方法效果：
 * 透传公共表单回传的新模型，确保筛选条件输入值可以同步到页面状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的最新筛选条件对象。
 * 返回值：
 * - 无返回值；副作用是向父层继续抛出 `update:modelValue`。
 */
const handleModelValueUpdate = (nextValue: Record<string, unknown>) => {
  emit('update:modelValue', nextValue)
}

/**
 * 方法效果：
 * 处理查询动作，统一把当前筛选条件对象回抛给外层页面。
 * 参数：
 * - `formValue`：当前筛选条件对象。
 * 返回值：
 * - 无返回值；副作用是触发 `search` 事件。
 */
const handleSearch = (formValue: Record<string, unknown>) => {
  emit('search', formValue)
}

/**
 * 方法效果：
 * 供查询按钮直接读取当前最新筛选条件，避免依赖公共表单内部的提交按钮。
 * 参数：
 * - 无。
 * 返回值：
 * - 当前筛选条件对象。
 */
const currentModelValue = computed(() => props.modelValue)

/**
 * 方法效果：
 * 让第二行按钮和第一行输入框起始位置保持一致，避免落在标签左边显得别扭。
 * 参数：
 * - 无。
 * 返回值：
 * - 可直接绑定到按钮容器的内联样式对象。
 */
const actionRowStyle = computed(() => ({
  paddingLeft: `calc(${props.labelWidth} + 12px)`,
}))
</script>

<template>
  <!-- 列表筛选条件区域 -->
  <section class="search-filter-panel">
    <SharedFormPanel
      :schema="schema"
      :model-value="modelValue"
      :columns="columns"
      :label-width="labelWidth"
      :loading="loading"
      :show-submit-button="false"
      compact
      @update:model-value="handleModelValueUpdate"
    >
    </SharedFormPanel>

    <div class="search-filter-panel__actions" :style="actionRowStyle">
      <ElButton type="primary" :loading="loading" @click="handleSearch(currentModelValue)">
        查询
      </ElButton>
      <ElButton @click="emit('reset')">重置</ElButton>
      <ElButton type="primary" plain @click="emit('create')">新增用户</ElButton>
    </div>
  </section>
</template>

<style scoped>
.search-filter-panel {
  display: grid;
  gap: 8px;
}

.search-filter-panel__actions {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.search-filter-panel :deep(.shared-form-panel) {
  min-width: 0;
}
</style>
