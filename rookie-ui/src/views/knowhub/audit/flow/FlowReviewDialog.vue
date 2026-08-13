<!--
  文件作用：
  资金流水审核弹窗，承接流水列表「通过/驳回」操作。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `flowId`：当前待审核流水主键（提交时回传）。
  - `flow`：待审核流水详情（金额/分类/备注），供审核员参考决策。
  - `decide`：审核决定 'approve' 通过 / 'reject' 驳回；默认按传入值渲染标题与按钮文案。
  关键交互：
  - 展示流水金额/类型/分类/备注供审核员参考。
  - 驳回时审核意见必填，通过时可选填；提交时向父层 emit submit({ blogId=flowId, pass, advice })。
  - 主题适配：弹窗内容用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElEmpty,
} from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { formatAmount } from './config'
import type { FundFlowRecord, FundFlowReviewPayload } from '@/types/api/knowhub/audit'

const props = withDefaults(
  defineProps<{
    visible: boolean
    flowId: number | null
    flow: FundFlowRecord | null
    decide?: 'approve' | 'reject'
  }>(),
  {
    decide: 'approve',
  },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: FundFlowReviewPayload]
}>()

/** 审核决定：true 通过 → APPROVED；false 驳回 → REJECTED（advice 必填） */
const pass = ref<boolean>(true)
/** 审核意见，驳回时必填 */
const advice = ref('')

/**
 * 方法效果：
 * 弹窗打开时按外部传入 decide 预设决定（approve→true / reject→false），清空上次意见残留。
 * 数据流转：watch visible + decide，true 时重置内部状态。
 */
watch(
  () => [props.visible, props.decide] as const,
  ([visible, decide]) => {
    if (visible) {
      pass.value = decide !== 'reject'
      advice.value = ''
    }
  },
)

const dialogTitle = computed(() => (props.decide === 'reject' ? '驳回资金流水' : '审核通过流水'))

/** 驳回时审核意见必填，通过时可选；据此控制提交按钮禁用。 */
const canSubmit = computed(
  () => pass.value ? true : advice.value.trim().length > 0,
)

/**
 * 方法效果：
 * 提交审核决定，向父层抛出 submit 事件。
 * 参数：
 * - 无，直接读取当前表单状态与 flowId。
 * 返回值：
 * - 无返回值；副作用是触发 submit 事件。
 */
const handleSubmit = () => {
  if (!props.flowId || !canSubmit.value) {
    return
  }

  emit('submit', {
    blogId: props.flowId,
    pass: pass.value,
    advice: pass.value ? advice.value.trim() || undefined : advice.value.trim(),
  })
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <section v-if="flow" class="audit-flow-review__content">
      <div class="audit-flow-review__row">
        <span><em>金额</em>{{ formatAmount(flow.amount) }} 元</span>
        <span><em>类型</em><DictTag dict-key="audit_flow_type" :value="flow.flowType" /></span>
        <span v-if="flow.category"><em>分类</em><DictTag dict-key="audit_expense_category" :value="flow.category" /></span>
      </div>
      <div class="audit-flow-review__row">
        <span><em>主体</em>{{ flow.subjectName || flow.subjectId }}</span>
        <span><em>经办人</em>{{ flow.handlerNickname || flow.handlerId || '--' }}</span>
      </div>
      <p v-if="flow.note" class="audit-flow-review__note">{{ flow.note }}</p>
    </section>
    <ElEmpty v-else description="未加载到流水内容" :image-size="56" />

    <ElForm label-width="80px" class="audit-flow-review__form">
      <ElFormItem label="审核决定" required>
        <ElRadioGroup v-model="pass">
          <ElRadio :value="true">通过</ElRadio>
          <ElRadio :value="false">驳回</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem :label="pass ? '审核意见' : '驳回原因'" :required="!pass">
        <ElInput
          v-model="advice"
          type="textarea"
          :rows="3"
          :placeholder="pass ? '可选，填写审核意见' : '请填写驳回原因（必填）'"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <div class="audit-flow-review__actions">
        <ElButton @click="emit('update:visible', false)">取消</ElButton>
        <ElButton type="primary" :disabled="!canSubmit" @click="handleSubmit">
          确认提交
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.audit-flow-review__content {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.audit-flow-review__row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.audit-flow-review__row em {
  font-style: normal;
  color: var(--rookie-text-tertiary);
  margin-right: 8px;
}

.audit-flow-review__note {
  margin: 0;
  padding: 6px 10px;
  border-left: 2px solid var(--rookie-border);
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-flow-review__form {
  margin-top: 4px;
}

.audit-flow-review__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>