<script setup lang="ts">
/**
 * 文件作用：
 * 提供一套基于字段元数据驱动的公共表格组件，
 * 并可选地在组件内部挂载同一份字段配置驱动的编辑表单。
 */
import { computed } from 'vue'
import { ElButton, ElEmpty, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import type { FormRules } from 'element-plus'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import { getValueByPath } from '@/utils/object'
import SharedFormPanel from './SharedFormPanel.vue'

const props = withDefaults(
  defineProps<{
    rows: Record<string, unknown>[]
    schema: SharedFieldSchemaMap<Record<string, unknown>>
    actions?: SharedActionConfig<Record<string, unknown>>[]
    title?: string
    description?: string
    loading?: boolean
    stripe?: boolean
    rowKey?: string
    showIndex?: boolean
    formVisible?: boolean
    formModelValue?: Record<string, unknown>
    formTitle?: string
    formDescription?: string
    formActions?: SharedActionConfig<Record<string, unknown>>[]
    formColumns?: number
    formSubmitText?: string
    pagination?: Partial<NormalizedPageResult<Record<string, unknown>>>
    formRules?: FormRules
    tableMaxHeight?: number | string
    formLoading?: boolean
  }>(),
  {
    actions: () => [],
    title: '',
    description: '',
    loading: false,
    stripe: true,
    rowKey: 'id',
    showIndex: false,
    formVisible: false,
    formModelValue: () => ({}),
    formTitle: '',
    formDescription: '',
    formActions: () => [],
    formColumns: 2,
    formSubmitText: '提交',
    formRules: () => ({}),
    pagination: () => ({
      total: 0,
      pageNum: 1,
      pageSize: 10,
      pages: 0,
      records: [],
    }),
    tableMaxHeight: 560,
    formLoading: false,
  },
)

const emit = defineEmits<{
  rowAction: [payload: { actionKey: string; row: Record<string, unknown> }]
  'update:formVisible': [value: boolean]
  'update:formModelValue': [value: Record<string, unknown>]
  formSubmit: [value: Record<string, unknown>]
  formCancel: []
  paginationChange: [payload: { pageNum: number; pageSize: number }]
}>()

/**
 * 方法效果：
 * 按表格展示规则筛出需要渲染的列，并按列顺序排序。
 * 参数：
 * - 无，直接读取当前组件 props。
 * 返回值：
 * - 可直接用于表格列循环渲染的字段列表。
 */
const tableFields = computed(() =>
  Object.entries(props.schema)
    .filter(([, config]) => config.tableVisible !== false)
    .sort(([, previousConfig], [, nextConfig]) => (previousConfig.tableOrder ?? 0) - (nextConfig.tableOrder ?? 0)),
)

/**
 * 方法效果：
 * 基于字段路径读取当前行数据的原始值。
 * 参数：
 * - `row`：当前表格行。
 * - `fieldKey`：字段路径键，例如 `dept.name`。
 * 返回值：
 * - 当前列对应的原始值。
 */
const readCellValue = (row: Record<string, unknown>, fieldKey: string) => getValueByPath(row, fieldKey)

/**
 * 方法效果：
 * 统一处理当前列显示文本，优先走字段自定义格式化逻辑。
 * 参数：
 * - `row`：当前表格行。
 * - `fieldKey`：字段路径键。
 * 返回值：
 * - 最终展示到单元格中的文本。
 */
const formatCellValue = (row: Record<string, unknown>, fieldKey: string) => {
  const fieldConfig = props.schema[fieldKey]
  const rawValue = readCellValue(row, fieldKey)

  if (fieldConfig?.formatter) {
    return fieldConfig.formatter(rawValue, row)
  }

  if (rawValue === null || rawValue === undefined || rawValue === '') {
    return '--'
  }

  return String(rawValue)
}

/**
 * 方法效果：
 * 统一判断当前行操作按钮是否应该显示。
 * 参数：
 * - `action`：单个行操作按钮配置。
 * - `row`：当前表格行。
 * 返回值：
 * - `true` 表示渲染该操作按钮。
 */
const isActionVisible = (action: SharedActionConfig<Record<string, unknown>>, row: Record<string, unknown>) => {
  if (typeof action.visible === 'function') {
    return action.visible(row)
  }

  return action.visible !== false
}

/**
 * 方法效果：
 * 统一判断当前行操作按钮是否应该禁用。
 * 参数：
 * - `action`：单个行操作按钮配置。
 * - `row`：当前表格行。
 * 返回值：
 * - `true` 表示按钮禁用。
 */
const isActionDisabled = (action: SharedActionConfig<Record<string, unknown>>, row: Record<string, unknown>) => {
  if (typeof action.disabled === 'function') {
    return action.disabled(row)
  }

  return Boolean(action.disabled)
}

/**
 * 方法效果：
 * 处理表格行操作按钮点击，并把当前操作和行数据一并抛给外层页面。
 * 参数：
 * - `action`：单个行操作按钮配置。
 * - `row`：当前表格行。
 * 返回值：
 * - 无返回值；副作用是执行外部回调并派发 `rowAction` 事件。
 */
const handleRowAction = async (
  action: SharedActionConfig<Record<string, unknown>>,
  row: Record<string, unknown>,
) => {
  if (action.onClick) {
    await action.onClick(row)
  }

  emit('rowAction', {
    actionKey: action.key,
    row,
  })
}

/**
 * 方法效果：
 * 处理分页页码切换，并把最新分页参数回抛给外层页面重新请求数据。
 * 参数：
 * - `pageNum`：当前页码。
 * 返回值：
 * - 无返回值；副作用是触发 `paginationChange` 事件。
 */
const handleCurrentChange = (pageNum: number) => {
  emit('paginationChange', {
    pageNum,
    pageSize: props.pagination.pageSize ?? 10,
  })
}

/**
 * 方法效果：
 * 处理分页每页条数切换，并回到第一页重新请求数据。
 * 参数：
 * - `pageSize`：新的每页条数。
 * 返回值：
 * - 无返回值；副作用是触发 `paginationChange` 事件。
 */
const handlePageSizeChange = (pageSize: number) => {
  emit('paginationChange', {
    pageNum: 1,
    pageSize,
  })
}
</script>

<template>
  <!-- 公共表格区域 -->
  <section class="shared-table-panel">
    <header v-if="title || description" class="shared-table-panel__head">
      <strong v-if="title" class="shared-table-panel__title">{{ title }}</strong>
      <p v-if="description" class="shared-table-panel__description">{{ description }}</p>
    </header>

    <ElTable
      v-if="rows.length > 0"
      class="shared-table-panel__table"
      :data="rows"
      :stripe="stripe"
      :row-key="rowKey"
      :max-height="tableMaxHeight"
      v-loading="loading"
    >
      <ElTableColumn v-if="showIndex" type="index" width="64" label="#" />

      <ElTableColumn
        v-for="[fieldKey, fieldConfig] in tableFields"
        :key="fieldKey"
        :label="fieldConfig.label"
        :width="fieldConfig.tableWidth"
        :min-width="fieldConfig.tableMinWidth || 140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ formatCellValue(row, fieldKey) }}
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="actions.length > 0"
        label="操作"
        :min-width="180"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="shared-table-panel__actions">
            <ElButton
              v-for="action in actions.filter((item) => isActionVisible(item, row))"
              :key="action.key"
              :type="action.buttonType || 'primary'"
              :plain="action.plain !== false"
              :text="action.text !== false"
              :disabled="isActionDisabled(action, row)"
              @click="handleRowAction(action, row)"
            >
              {{ action.label }}
            </ElButton>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>

    <div v-else class="shared-table-panel__empty">
      <ElEmpty description="暂无数据" />
    </div>

    <div v-if="(pagination.total ?? 0) > 0" class="shared-table-panel__pagination">
      <ElPagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
        :current-page="pagination.pageNum"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handleCurrentChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <SharedFormPanel
      v-if="formVisible"
      mode="dialog"
      :visible="formVisible"
      :title="formTitle"
      :description="formDescription"
      :schema="schema"
      :model-value="formModelValue"
      :actions="formActions"
      :columns="formColumns"
      :submit-text="formSubmitText"
      :rules="formRules"
      :loading="formLoading"
      @update:visible="emit('update:formVisible', $event)"
      @update:model-value="emit('update:formModelValue', $event)"
      @submit="emit('formSubmit', $event)"
      @cancel="emit('formCancel')"
    />
  </section>
</template>

<style scoped>
.shared-table-panel {
  display: grid;
  gap: 18px;
}

.shared-table-panel__head {
  display: grid;
  gap: 8px;
}

.shared-table-panel__title {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-lg);
}

.shared-table-panel__description {
  margin: 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.shared-table-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.shared-table-panel__empty {
  padding: 18px 0;
  border: 1px dashed var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.shared-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
