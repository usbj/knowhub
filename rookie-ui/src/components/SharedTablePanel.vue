/**
 * 文件作用：
 * 提供列表页共用的基础表格组件，
 * 统一处理列渲染、权限操作按钮、分页和内置编辑弹窗。
 * 关键参数：
 * - `rows` / `schema`：表格数据与字段配置。
 * - `actions`：行级操作按钮配置，支持权限、显隐和禁用控制。
 * - `pagination` / `tableMaxHeight`：控制分页与表格区域高度。
 * - `formVisible` / `formModelValue` / `formRules`：驱动内置弹窗表单。
 * 插槽与事件：
 * - 透传 `SharedFormPanel` 的字段插槽，方便页面注入特殊表单字段。
 * - `paginationChange` / `rowAction` / `selectionChange` / `formSubmit` 等事件向页面回抛交互结果。
 */
<script setup lang="ts">
import { computed } from 'vue'
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import type { FormRules } from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { usePermission } from '@/composables/usePermission'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  SharedActionConfig,
  SharedFieldSchemaItem,
  SharedFieldSchemaMap,
  SharedFieldTagRenderOptions,
  SharedFieldTagType,
} from '@/types/components/data-display'
import { formatDisplayValue } from '@/utils/format'
import { getValueByPath } from '@/utils/object'
import SharedFormPanel from './SharedFormPanel.vue'

const props = withDefaults(
  defineProps<{
    rows: Record<string, unknown>[]
    schema: SharedFieldSchemaMap<any>
    actions?: SharedActionConfig<Record<string, unknown>>[]
    loading?: boolean
    stripe?: boolean
    rowKey?: string
    showSelection?: boolean
    formVisible?: boolean
    formModelValue?: Record<string, unknown>
    formTitle?: string
    formColumns?: number
    formSubmitText?: string
    pagination?: Partial<NormalizedPageResult<Record<string, unknown>>>
    formRules?: FormRules
    tableMaxHeight?: number | string
    formLoading?: boolean
    maxInlineActions?: number
  }>(),
  {
    actions: () => [],
    loading: false,
    stripe: true,
    rowKey: 'id',
    showSelection: false,
    formVisible: false,
    formModelValue: () => ({}),
    formTitle: '',
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
    maxInlineActions: 3,
  },
)

const emit = defineEmits<{
  rowAction: [payload: { actionKey: string; row: Record<string, unknown> }]
  'update:formVisible': [value: boolean]
  'update:formModelValue': [value: Record<string, unknown>]
  formSubmit: [value: Record<string, unknown>]
  formCancel: []
  paginationChange: [payload: { pageNum: number; pageSize: number }]
  selectionChange: [rows: Record<string, unknown>[]]
}>()

const { hasPermission } = usePermission()
const { resolveDictLabel } = useDict()

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

  if (fieldConfig?.dictKey) {
    const label = resolveDictLabel(fieldConfig.dictKey, rawValue)
    return Array.isArray(label) ? label.join(' / ') : label || '--'
  }

  if (rawValue === null || rawValue === undefined || rawValue === '') {
    return '--'
  }

  return formatDisplayValue(rawValue)
}

/**
 * 方法效果：
 * 当字段声明为 `tag` 渲染类型时，把当前行数据转换成标签展示所需的信息，
 * 包括标签文字、标签类型（颜色）、标签风格和自定义类名。
 * 数据流转：
 * - 标签文字优先从 `tagRender.labelField` 跨字段读取，否则取本字段值；
 *   命中字段 `options` 时用选项 `label` 作为文字，否则用原始值的字符串形式。
 * - 标签颜色优先从 `tagRender.typeField` 跨字段读取，否则取本字段值；
 *   读到的值再经 `tagTypeMap` 映射，未提供映射时直接当作标签类型使用；
 *   来源为空时回落到 `info`，用于无标签类型的下拉选项型数据。
 * - 标签风格优先从 `tagRender.effectField` 跨字段读取，否则取 `effect`，
 *   再否则若本字段值本身是合法风格则复用，最终回落 `plain`。
 * - 自定义类名从 `tagRender.classField` 跨字段读取并裁剪空白。
 * - 是否渲染标签以"文字来源值"是否非空为准：标签展示列本身不对应数据属性，
 *   本字段恒为空，必须按 labelField 指向的字段判断是否展示。
 * 参数：
 * - `row`：当前表格行。
 * - `fieldKey`：字段路径键。
 * 返回值：
 * - 标签展示信息；无法渲染有效标签（文字为空）时返回 `null`，交由模板渲染占位文本。
 */
const resolveTagDisplay = (
  row: Record<string, unknown>,
  fieldKey: string,
): { label: string; type: SharedFieldTagType; effect: 'plain' | 'light' | 'dark'; className: string } | null => {
  const fieldConfig = props.schema[fieldKey] as SharedFieldSchemaItem<Record<string, unknown>> | undefined
  const tagConfig: SharedFieldTagRenderOptions = fieldConfig?.tagRender ?? {}
  const rawValue = readCellValue(row, fieldKey)

  // 文字来源：优先跨字段读取，否则用本字段值
  const labelSourceValue = tagConfig.labelField ? readCellValue(row, tagConfig.labelField) : rawValue

  // 是否渲染以文字来源是否非空为准，避免标签展示列因本字段恒空而被误判为无内容
  if (labelSourceValue === null || labelSourceValue === undefined || labelSourceValue === '') {
    return null
  }

  // 命中字段 options 时用选项 label 作为可见文字，否则用文字来源的字符串形式
  const matchedOption = fieldConfig?.options?.find(
    (item) => String(item.value) === String(labelSourceValue),
  )
  const tagText = matchedOption?.label ?? String(labelSourceValue)

  // 标签颜色来源：优先跨字段读取，否则回落到本字段值；再经 tagTypeMap 映射，空值回落 info
  const colorSourceValue = tagConfig.typeField
    ? readCellValue(row, tagConfig.typeField)
    : rawValue
  const colorKey = String(colorSourceValue ?? '')
  const tagType: SharedFieldTagType = colorKey
    ? ((tagConfig.tagTypeMap?.[colorKey] ?? colorKey) as SharedFieldTagType)
    : 'info'

  // 标签风格来源：优先跨字段读取，否则取显式 effect，再否则本字段值若合法则复用，最终回落 plain
  const validEffects = ['plain', 'light', 'dark'] as const
  const effectSourceValue = tagConfig.effectField ? readCellValue(row, tagConfig.effectField) : undefined
  const resolvedEffect: 'plain' | 'light' | 'dark' =
    (effectSourceValue as (typeof validEffects)[number] | undefined) ??
    tagConfig.effect ??
    (validEffects.includes(rawValue as (typeof validEffects)[number])
      ? (rawValue as (typeof validEffects)[number])
      : 'plain')

  // 自定义类名跨字段读取并裁剪，避免空白类名污染标签
  const className = tagConfig.classField
    ? String(readCellValue(row, tagConfig.classField) ?? '').trim()
    : ''

  return {
    label: tagText,
    type: tagType,
    effect: resolvedEffect,
    className,
  }
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
  if (!hasPermission(action.permKey)) {
    return false
  }

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
 * 读取当前行所有满足显示条件的操作按钮。
 * 参数：
 * - `row`：当前表格行。
 * 返回值：
 * - 当前行可展示的操作按钮数组。
 */
const getVisibleActions = (row: Record<string, unknown>) => props.actions.filter((action) => isActionVisible(action, row))

/**
 * 方法效果：
 * 限制操作列直接展示的按钮数量，避免一行按钮过多导致排版混乱。
 * 参数：
 * - `row`：当前表格行。
 * 返回值：
 * - 当前行直接展示在表格中的操作按钮数组。
 */
const getInlineActions = (row: Record<string, unknown>) => {
  const visibleActions = getVisibleActions(row)

  if (visibleActions.length <= props.maxInlineActions) {
    return visibleActions
  }

  return visibleActions.slice(0, Math.max(props.maxInlineActions - 1, 1))
}

/**
 * 方法效果：
 * 计算当前行需要放入“更多”下拉中的操作按钮。
 * 参数：
 * - `row`：当前表格行。
 * 返回值：
 * - 需要折叠展示的操作按钮数组。
 */
const getOverflowActions = (row: Record<string, unknown>) => {
  const visibleActions = getVisibleActions(row)

  if (visibleActions.length <= props.maxInlineActions) {
    return []
  }

  return visibleActions.slice(Math.max(props.maxInlineActions - 1, 1))
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

/**
 * 方法效果：
 * 把表格多选结果同步给外层页面。
 * 参数：
 * - `rows`：当前选中的数据行数组。
 * 返回值：
 * - 无返回值；副作用是触发 `selectionChange` 事件。
 */
const handleSelectionChange = (rows: Record<string, unknown>[]) => {
  emit('selectionChange', rows)
}
</script>

<template>
  <!-- 公共表格区域 -->
  <section class="shared-table-panel">
    <ElTable
      v-if="rows.length > 0"
      v-loading="loading"
      class="shared-table-panel__table"
      :data="rows"
      :stripe="stripe"
      :row-key="rowKey"
      :max-height="tableMaxHeight"
      @selection-change="handleSelectionChange"
    >
      <ElTableColumn v-if="showSelection" type="selection" width="54" reserve-selection />

      <ElTableColumn
        v-for="[fieldKey, fieldConfig] in tableFields"
        :key="fieldKey"
        :label="fieldConfig.label"
        :width="fieldConfig.tableWidth"
        :min-width="fieldConfig.tableMinWidth || 140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <ElTag
            v-if="fieldConfig.renderType === 'tag' && resolveTagDisplay(row, fieldKey)"
            size="small"
            :class="resolveTagDisplay(row, fieldKey)?.className"
            :effect="resolveTagDisplay(row, fieldKey)?.effect ?? 'plain'"
            :type="resolveTagDisplay(row, fieldKey)?.type ?? 'info'"
          >
            {{ resolveTagDisplay(row, fieldKey)?.label }}
          </ElTag>
          <span
            v-else-if="fieldConfig.renderType === 'tag'"
            class="shared-table-panel__tag-placeholder"
          >
            {{ fieldConfig.tagRender?.placeholder ?? '--' }}
          </span>
          <DictTag
            v-else-if="fieldConfig.dictKey && !fieldConfig.formatter"
            :dict-key="fieldConfig.dictKey"
            :value="readCellValue(row, fieldKey)"
          />
          <template v-else>
            {{ formatCellValue(row, fieldKey) }}
          </template>
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="actions.length > 0"
        label="操作"
        :min-width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="shared-table-panel__actions">
            <ElButton
              v-for="action in getInlineActions(row)"
              :key="action.key"
              :type="action.buttonType || 'primary'"
              :plain="action.plain !== false"
              :text="action.text !== false"
              :disabled="isActionDisabled(action, row)"
              @click="handleRowAction(action, row)"
            >
              {{ action.label }}
            </ElButton>

            <ElDropdown
              v-if="getOverflowActions(row).length > 0"
              trigger="click"
              placement="bottom-end"
            >
              <ElButton text>更多</ElButton>

              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem
                    v-for="action in getOverflowActions(row)"
                    :key="action.key"
                    :disabled="isActionDisabled(action, row)"
                    @click="handleRowAction(action, row)"
                  >
                    {{ action.label }}
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>
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
      :dialog-title="formTitle"
      :schema="schema"
      :model-value="formModelValue"
      :columns="formColumns"
      :submit-text="formSubmitText"
      :rules="formRules"
      :loading="formLoading"
      @update:visible="emit('update:formVisible', $event)"
      @update:model-value="emit('update:formModelValue', $event)"
      @submit="emit('formSubmit', $event)"
      @cancel="emit('formCancel')"
    >
      <template
        v-for="(_, slotName) in $slots"
        :key="slotName"
        #[slotName]="slotProps"
      >
        <slot :name="slotName" v-bind="slotProps" />
      </template>
    </SharedFormPanel>
  </section>
</template>

<style scoped>
.shared-table-panel {
  display: grid;
  gap: 18px;
}

.shared-table-panel__actions {
  display: flex;
  align-items: center;
  gap: 0 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.shared-table-panel__actions :deep(.el-button + .el-button) {
  margin-left: 0;
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

.shared-table-panel__tag-placeholder {
  color: var(--rookie-text-tertiary);
}
</style>
