<script setup lang="ts">
/**
 * 文件作用：
 * 提供一套基于字段元数据驱动的公共表单组件，
 * 支持面板模式和弹窗模式两种展示方式。
 */
import { computed, ref, toRaw } from 'vue'
import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type {
  SharedActionConfig,
  SharedFieldSchemaItem,
  SharedFieldSchemaMap,
} from '@/types/components/data-display'
import { getValueByPath, setValueByPath } from '@/utils/object'

const props = withDefaults(
  defineProps<{
    schema: SharedFieldSchemaMap
    modelValue: Record<string, unknown>
    actions?: SharedActionConfig<Record<string, unknown>>[]
    title?: string
    description?: string
    mode?: 'panel' | 'dialog'
    visible?: boolean
    columns?: number
    labelWidth?: string
    loading?: boolean
    submitText?: string
    showSubmitButton?: boolean
    size?: 'large' | 'default' | 'small'
    compact?: boolean
    rules?: FormRules
  }>(),
  {
    actions: () => [],
    title: '',
    description: '',
    mode: 'panel',
    visible: false,
    columns: 2,
    labelWidth: '96px',
    loading: false,
    submitText: '提交',
    showSubmitButton: true,
    size: 'default',
    compact: false,
    rules: () => ({}),
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  'update:visible': [value: boolean]
  submit: [value: Record<string, unknown>]
  cancel: []
  invalid: []
}>()

const formRef = ref<FormInstance>()

/**
 * 方法效果：
 * 按表单展示规则筛出需要渲染的字段，并按表单顺序排序。
 * 参数：
 * - 无，直接读取当前组件 props。
 * 返回值：
 * - 可直接用于模板渲染的字段列表。
 */
const formFields = computed(() =>
  Object.entries(props.schema)
    .filter(([, config]) => config.formVisible !== false)
    .sort(([, previousConfig], [, nextConfig]) => (previousConfig.formOrder ?? 0) - (nextConfig.formOrder ?? 0)),
)

/**
 * 方法效果：
 * 根据列数设置计算每个字段默认所占的栅格宽度。
 * 参数：
 * - 无，直接读取当前组件 props。
 * 返回值：
 * - 当前表单布局中单字段的默认列宽。
 */
const defaultSpan = computed(() => Math.max(Math.floor(24 / props.columns), 6))

/**
 * 方法效果：
 * 合并外层传入的表单规则与字段级规则，形成可直接交给 Element Plus 的校验对象。
 * 参数：
 * - 无，直接读取当前组件 props 和 schema。
 * 返回值：
 * - 当前表单完整校验规则。
 */
const mergedRules = computed<FormRules>(() => {
  const schemaRules = Object.entries(props.schema).reduce<FormRules>((rules, [fieldKey, fieldConfig]) => {
    if (fieldConfig.rules) {
      rules[fieldKey] = Array.isArray(fieldConfig.rules) ? fieldConfig.rules : [fieldConfig.rules]
    }
    return rules
  }, {})

  return {
    ...schemaRules,
    ...props.rules,
  }
})

/**
 * 方法效果：
 * 基于字段路径从表单模型中读取当前值。
 * 参数：
 * - `fieldKey`：字段路径键，例如 `user.name`。
 * 返回值：
 * - 当前字段对应的模型值。
 */
const readFieldValue = (fieldKey: string) => getValueByPath(props.modelValue, fieldKey)

/**
 * 方法效果：
 * 更新指定字段对应的模型值，并通过 `v-model` 向外同步新对象。
 * 参数：
 * - `fieldKey`：字段路径键，例如 `user.name`。
 * - `value`：控件最新输入值。
 * 返回值：
 * - 无返回值；副作用是向父层触发 `update:modelValue`。
 */
const updateFieldValue = (fieldKey: string, value: unknown) => {
  const nextModel = structuredClone(toRaw(props.modelValue))
  setValueByPath(nextModel, fieldKey, value)
  emit('update:modelValue', nextModel)
}

/**
 * 方法效果：
 * 统一为日期类控件补充默认类型和数据格式配置。
 * 参数：
 * - `fieldConfig`：当前字段配置。
 * 返回值：
 * - 可直接透传给日期控件的 props 对象。
 */
const buildDateProps = (fieldConfig: SharedFieldSchemaItem) => {
  const inputType = fieldConfig.inputType ?? 'date'

  if (inputType === 'daterange') {
    return {
      type: 'daterange',
      valueFormat: 'YYYY-MM-DD',
      startPlaceholder: '开始时间',
      endPlaceholder: '结束时间',
      ...fieldConfig.props,
    }
  }

  if (inputType === 'datetime') {
    return {
      type: 'datetime',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
      ...fieldConfig.props,
    }
  }

  return {
    type: 'date',
    valueFormat: 'YYYY-MM-DD',
    ...fieldConfig.props,
  }
}

/**
 * 方法效果：
 * 统一判断当前操作按钮是否应该显示。
 * 参数：
 * - `action`：单个按钮配置。
 * 返回值：
 * - `true` 表示渲染该按钮。
 */
const isActionVisible = (action: SharedActionConfig<Record<string, unknown>>) => {
  if (typeof action.visible === 'function') {
    return action.visible(props.modelValue)
  }

  return action.visible !== false
}

/**
 * 方法效果：
 * 统一判断当前操作按钮是否应该禁用。
 * 参数：
 * - `action`：单个按钮配置。
 * 返回值：
 * - `true` 表示按钮禁用。
 */
const isActionDisabled = (action: SharedActionConfig<Record<string, unknown>>) => {
  if (typeof action.disabled === 'function') {
    return action.disabled(props.modelValue)
  }

  return Boolean(action.disabled)
}

/**
 * 方法效果：
 * 处理表单底部按钮点击，并在需要时触发外部传入的业务方法。
 * 参数：
 * - `action`：单个按钮配置。
 * 返回值：
 * - 无返回值；副作用是执行外部回调。
 */
const handleActionClick = async (action: SharedActionConfig<Record<string, unknown>>) => {
  if (action.onClick) {
    await action.onClick(props.modelValue)
  }
}

/**
 * 方法效果：
 * 关闭弹窗模式表单，并向外同步可见状态。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发 `cancel` 和 `update:visible`。
 */
const handleCancel = () => {
  emit('cancel')
  emit('update:visible', false)
}

/**
 * 方法效果：
 * 提交当前表单模型，交给外部页面决定实际保存逻辑。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发 `submit` 事件。
 */
const handleSubmit = async () => {
  if (!formRef.value) {
    emit('submit', props.modelValue)
    return
  }

  const isValid = await formRef.value.validate().catch(() => false)

  if (!isValid) {
    emit('invalid')
    return
  }

  emit('submit', props.modelValue)
}
</script>

<template>
  <!-- 公共表单区域 -->
  <ElDialog
    v-if="mode === 'dialog'"
    :model-value="visible"
    :title="title"
    width="720px"
    destroy-on-close
    @close="handleCancel"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="description" class="shared-form-panel__description">{{ description }}</div>
    <ElForm ref="formRef" class="shared-form-panel__form" :model="modelValue" :rules="mergedRules" :label-width="labelWidth">
      <ElRow :gutter="16">
        <ElCol
          v-for="[fieldKey, fieldConfig] in formFields"
          :key="fieldKey"
          :span="fieldConfig.span ?? defaultSpan"
        >
          <ElFormItem :label="fieldConfig.label" :prop="fieldKey">
            <ElInput
              v-if="!fieldConfig.inputType || fieldConfig.inputType === 'text'"
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else-if="fieldConfig.inputType === 'password'"
              :model-value="readFieldValue(fieldKey) as string"
              type="password"
              show-password
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else-if="fieldConfig.inputType === 'textarea'"
              :model-value="readFieldValue(fieldKey) as string"
              type="textarea"
              :rows="4"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInputNumber
              v-else-if="fieldConfig.inputType === 'number'"
              class="shared-form-panel__number"
              :model-value="readFieldValue(fieldKey) as number | undefined"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSelect
              v-else-if="fieldConfig.inputType === 'select'"
              :model-value="readFieldValue(fieldKey)"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            >
              <ElOption
                v-for="option in fieldConfig.options ?? []"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>

            <ElDatePicker
              v-else-if="['date', 'datetime', 'daterange'].includes(fieldConfig.inputType)"
              class="shared-form-panel__date"
              :model-value="readFieldValue(fieldKey)"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :size="size"
              v-bind="buildDateProps(fieldConfig)"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSwitch
              v-else-if="fieldConfig.inputType === 'switch'"
              :model-value="Boolean(readFieldValue(fieldKey))"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>

    <template #footer>
      <div class="shared-form-panel__actions">
        <ElButton :size="size" @click="handleCancel">取消</ElButton>
        <ElButton
          v-for="action in actions.filter((item) => isActionVisible(item))"
          :key="action.key"
          :type="action.buttonType || 'primary'"
          :plain="action.plain"
          :text="action.text"
          :disabled="isActionDisabled(action) || loading"
          :size="size"
          @click="handleActionClick(action)"
        >
          {{ action.label }}
        </ElButton>
        <ElButton v-if="showSubmitButton" type="primary" :loading="loading" :size="size" @click="handleSubmit">
          {{ submitText }}
        </ElButton>
      </div>
    </template>
  </ElDialog>

  <section v-else class="shared-form-panel" :class="{ 'is-compact': compact }">
    <header v-if="title || description" class="shared-form-panel__head">
      <strong v-if="title" class="shared-form-panel__title">{{ title }}</strong>
      <p v-if="description" class="shared-form-panel__description">{{ description }}</p>
    </header>

    <ElForm ref="formRef" class="shared-form-panel__form" :model="modelValue" :rules="mergedRules" :label-width="labelWidth">
      <ElRow :gutter="16">
        <ElCol
          v-for="[fieldKey, fieldConfig] in formFields"
          :key="fieldKey"
          :span="fieldConfig.span ?? defaultSpan"
        >
          <ElFormItem :label="fieldConfig.label" :prop="fieldKey">
            <ElInput
              v-if="!fieldConfig.inputType || fieldConfig.inputType === 'text'"
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else-if="fieldConfig.inputType === 'password'"
              :model-value="readFieldValue(fieldKey) as string"
              type="password"
              show-password
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else-if="fieldConfig.inputType === 'textarea'"
              :model-value="readFieldValue(fieldKey) as string"
              type="textarea"
              :rows="4"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInputNumber
              v-else-if="fieldConfig.inputType === 'number'"
              class="shared-form-panel__number"
              :model-value="readFieldValue(fieldKey) as number | undefined"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSelect
              v-else-if="fieldConfig.inputType === 'select'"
              :model-value="readFieldValue(fieldKey)"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            >
              <ElOption
                v-for="option in fieldConfig.options ?? []"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>

            <ElDatePicker
              v-else-if="['date', 'datetime', 'daterange'].includes(fieldConfig.inputType)"
              class="shared-form-panel__date"
              :model-value="readFieldValue(fieldKey)"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :size="size"
              v-bind="buildDateProps(fieldConfig)"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSwitch
              v-else-if="fieldConfig.inputType === 'switch'"
              :model-value="Boolean(readFieldValue(fieldKey))"
              :disabled="fieldConfig.disabled"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              :size="size"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>

    <div class="shared-form-panel__actions">
      <ElButton
        v-for="action in actions.filter((item) => isActionVisible(item))"
        :key="action.key"
        :type="action.buttonType || 'primary'"
        :plain="action.plain"
        :text="action.text"
        :disabled="isActionDisabled(action) || loading"
        :size="size"
        @click="handleActionClick(action)"
      >
        {{ action.label }}
      </ElButton>
      <ElButton v-if="showSubmitButton" type="primary" :loading="loading" :size="size" @click="handleSubmit">
        {{ submitText }}
      </ElButton>
    </div>
  </section>
</template>

<style scoped>
.shared-form-panel {
  display: grid;
  gap: 18px;
}

.shared-form-panel__head {
  display: grid;
  gap: 8px;
}

.shared-form-panel__title {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-lg);
}

.shared-form-panel__description {
  margin: 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.shared-form-panel__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.shared-form-panel__form {
  overflow-x: hidden;
}

.shared-form-panel__form :deep(.el-row) {
  margin-left: 0 !important;
  margin-right: 0 !important;
}

.shared-form-panel__form :deep(.el-col) {
  padding-left: 8px !important;
  padding-right: 8px !important;
}

.shared-form-panel__form :deep(.el-input__wrapper),
.shared-form-panel__form :deep(.el-textarea__inner),
.shared-form-panel__form :deep(.el-select__wrapper),
.shared-form-panel__form :deep(.el-input-number),
.shared-form-panel__form :deep(.el-date-editor) {
  width: 100%;
}

.shared-form-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.shared-form-panel__number,
.shared-form-panel__date {
  width: 100%;
}

.shared-form-panel.is-compact {
  gap: 10px;
}

.shared-form-panel.is-compact .shared-form-panel__actions {
  gap: 8px;
}

.shared-form-panel.is-compact .shared-form-panel__form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.shared-form-panel.is-compact .shared-form-panel__form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.shared-form-panel.is-compact .shared-form-panel__form :deep(.el-col) {
  padding-left: 4px !important;
  padding-right: 4px !important;
}
</style>
