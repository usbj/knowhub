/**
 * 文件作用：
 * 提供页面筛选表单和弹窗编辑表单共用的基础表单组件，
 * 只负责基础字段渲染、校验和提交，特殊字段通过页面插槽扩展。
 * 关键参数：
 * - `schema`：字段配置映射，控制表单展示顺序、类型和占位文案。
 * - `modelValue`：当前表单模型对象，组件内部只做字段级读写透传。
 * - `mode` / `visible`：控制面板模式或弹窗模式及弹窗显隐。
 * - `columns` / `labelWidth` / `rules`：控制布局列数、标签宽度与校验规则。
 * 插槽与事件：
 * - `field-字段名`：接管某个特殊字段的渲染。
 * - `update:modelValue` / `submit` / `cancel`：向页面同步模型和交互动作。
 */
<script setup lang="ts">
import { computed, ref, toRaw, useSlots } from 'vue'
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
import { useDict } from '@/composables/useDict'
import type { SharedFieldSchemaItem, SharedFieldSchemaMap } from '@/types/components/data-display'
import { getValueByPath, setValueByPath } from '@/utils/object'

const props = withDefaults(
  defineProps<{
    schema: SharedFieldSchemaMap<any>
    modelValue: Record<string, unknown>
    mode?: 'panel' | 'dialog'
    visible?: boolean
    columns?: number
    labelWidth?: string
    loading?: boolean
    submitText?: string
    showSubmitButton?: boolean
    compact?: boolean
    rules?: FormRules
    dialogTitle?: string
    dialogWidth?: string
  }>(),
  {
    mode: 'panel',
    visible: false,
    columns: 2,
    labelWidth: '96px',
    loading: false,
    submitText: '提交',
    showSubmitButton: true,
    compact: false,
    rules: () => ({}),
    dialogTitle: '',
    dialogWidth: '720px',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  'update:visible': [value: boolean]
  submit: [value: Record<string, unknown>]
  cancel: []
  invalid: []
}>()

const slots = useSlots()
const formRef = ref<FormInstance>()
const { resolveDictOptions } = useDict()

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
 * 根据列数计算每个字段默认占据的栅格宽度。
 * 参数：
 * - 无，直接读取当前组件 props。
 * 返回值：
 * - 当前表单布局中单字段的默认列宽。
 */
const defaultSpan = computed(() => Math.max(Math.floor(24 / props.columns), 6))

/**
 * 方法效果：
 * 合并外层传入的校验规则与字段配置内自带规则。
 * 参数：
 * - 无，直接读取当前组件 props 和 schema。
 * 返回值：
 * - 可直接交给 Element Plus 表单的完整规则对象。
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
 * 更新指定字段的模型值，并把新对象同步回父层页面。
 * 参数：
 * - `fieldKey`：字段路径键。
 * - `value`：控件最新输入值。
 * 返回值：
 * - 无返回值；副作用是触发 `update:modelValue`。
 */
const updateFieldValue = (fieldKey: string, value: unknown) => {
  const nextModel = structuredClone(toRaw(props.modelValue))
  setValueByPath(nextModel, fieldKey, value)
  emit('update:modelValue', nextModel)
}

/**
 * 方法效果：
 * 判断当前字段是否由外层页面通过具名插槽接管渲染。
 * 参数：
 * - `fieldKey`：字段路径键。
 * 返回值：
 * - `true` 表示该字段由页面自定义渲染。
 */
const hasCustomFieldSlot = (fieldKey: string) => Boolean(slots[`field-${fieldKey}`])

/**
 * 方法效果：
 * 为选择类字段统一解析可选项，优先使用页面显式配置，未配置时回退到字典缓存。
 * 参数：
 * - `fieldConfig`：当前字段配置。
 * 返回值：
 * - 可直接用于下拉控件渲染的选项数组。
 */
const resolveSelectOptions = (fieldConfig: SharedFieldSchemaItem) => {
  if (fieldConfig.options?.length) {
    return fieldConfig.options
  }

  if (!fieldConfig.dictKey) {
    return []
  }

  return resolveDictOptions(fieldConfig.dictKey, fieldConfig.dictValueType ?? 'string')
}

/**
 * 方法效果：
 * 统一为日期类控件补充默认类型和数据格式配置。
 * 参数：
 * - `fieldConfig`：当前字段配置。
 * 返回值：
 * - 可直接透传给日期控件的 props 对象。
 */
const buildDateProps = (fieldConfig: SharedFieldSchemaItem): Record<string, unknown> => {
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
 * 提交当前表单模型，并在校验通过后交给外层页面处理保存逻辑。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发 `submit` 或 `invalid` 事件。
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
    :title="dialogTitle"
    :width="dialogWidth"
    destroy-on-close
    @close="handleCancel"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm ref="formRef" class="shared-form-panel__form" :model="modelValue" :rules="mergedRules" :label-width="labelWidth">
      <ElRow :gutter="16">
        <ElCol
          v-for="[fieldKey, fieldConfig] in formFields"
          :key="fieldKey"
          :span="fieldConfig.span ?? defaultSpan"
        >
          <ElFormItem :label="fieldConfig.label" :prop="fieldKey">
            <slot
              v-if="hasCustomFieldSlot(fieldKey)"
              :name="`field-${fieldKey}`"
              :field-key="fieldKey"
              :field-config="fieldConfig"
              :model-value="readFieldValue(fieldKey)"
              :form-model="modelValue"
              :update-field-value="(value: unknown) => updateFieldValue(fieldKey, value)"
            />

            <ElInput
              v-else-if="!fieldConfig.inputType || fieldConfig.inputType === 'text'"
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
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
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInputNumber
              v-else-if="fieldConfig.inputType === 'number'"
              class="shared-form-panel__number"
              :model-value="readFieldValue(fieldKey) as number | undefined"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSelect
              v-else-if="fieldConfig.inputType === 'select'"
              :model-value="readFieldValue(fieldKey) as any"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            >
              <ElOption
                v-for="option in resolveSelectOptions(fieldConfig)"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>

            <ElDatePicker
              v-else-if="['date', 'datetime', 'daterange'].includes(fieldConfig.inputType)"
              class="shared-form-panel__date"
              :model-value="readFieldValue(fieldKey) as any"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              v-bind="buildDateProps(fieldConfig) as any"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSwitch
              v-else-if="fieldConfig.inputType === 'switch'"
              :model-value="Boolean(readFieldValue(fieldKey))"
              :disabled="fieldConfig.disabled"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>

    <template #footer>
      <div class="shared-form-panel__actions">
        <ElButton @click="handleCancel">取消</ElButton>
        <ElButton v-if="showSubmitButton" type="primary" :loading="loading" @click="handleSubmit">
          {{ submitText }}
        </ElButton>
      </div>
    </template>
  </ElDialog>

  <section v-else class="shared-form-panel" :class="{ 'is-compact': compact }">
    <ElForm ref="formRef" class="shared-form-panel__form" :model="modelValue" :rules="mergedRules" :label-width="labelWidth">
      <ElRow :gutter="16">
        <ElCol
          v-for="[fieldKey, fieldConfig] in formFields"
          :key="fieldKey"
          :span="fieldConfig.span ?? defaultSpan"
        >
          <ElFormItem :label="fieldConfig.label" :prop="fieldKey">
            <slot
              v-if="hasCustomFieldSlot(fieldKey)"
              :name="`field-${fieldKey}`"
              :field-key="fieldKey"
              :field-config="fieldConfig"
              :model-value="readFieldValue(fieldKey)"
              :form-model="modelValue"
              :update-field-value="(value: unknown) => updateFieldValue(fieldKey, value)"
            />

            <ElInput
              v-else-if="!fieldConfig.inputType || fieldConfig.inputType === 'text'"
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
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
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInputNumber
              v-else-if="fieldConfig.inputType === 'number'"
              class="shared-form-panel__number"
              :model-value="readFieldValue(fieldKey) as number | undefined"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSelect
              v-else-if="fieldConfig.inputType === 'select'"
              :model-value="readFieldValue(fieldKey) as any"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            >
              <ElOption
                v-for="option in resolveSelectOptions(fieldConfig)"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>

            <ElDatePicker
              v-else-if="['date', 'datetime', 'daterange'].includes(fieldConfig.inputType)"
              class="shared-form-panel__date"
              :model-value="readFieldValue(fieldKey) as any"
              :placeholder="fieldConfig.placeholder || `请选择${fieldConfig.label}`"
              v-bind="buildDateProps(fieldConfig) as any"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElSwitch
              v-else-if="fieldConfig.inputType === 'switch'"
              :model-value="Boolean(readFieldValue(fieldKey))"
              :disabled="fieldConfig.disabled"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />

            <ElInput
              v-else
              :model-value="readFieldValue(fieldKey) as string"
              :placeholder="fieldConfig.placeholder || `请输入${fieldConfig.label}`"
              :disabled="fieldConfig.disabled"
              :clearable="fieldConfig.clearable !== false"
              v-bind="fieldConfig.props"
              @update:model-value="updateFieldValue(fieldKey, $event)"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </ElForm>

    <div class="shared-form-panel__actions">
      <ElButton v-if="showSubmitButton" type="primary" :loading="loading" @click="handleSubmit">
        {{ submitText }}
      </ElButton>
    </div>
  </section>
</template>

<style scoped>
.shared-form-panel {
  display: grid;
  gap: 12px;
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

.shared-form-panel__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.shared-form-panel__form :deep(.el-input__wrapper),
.shared-form-panel__form :deep(.el-textarea__inner),
.shared-form-panel__form :deep(.el-select__wrapper),
.shared-form-panel__form :deep(.el-input-number),
.shared-form-panel__form :deep(.el-date-editor) {
  width: 100%;
}

.shared-form-panel__number,
.shared-form-panel__date {
  width: 100%;
}

.shared-form-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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
