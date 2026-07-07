<!--
  文件作用：
  资源审核弹窗，承接资源列表「审核」操作。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `resourceId`：当前待审核资源主键（提交时回传）。
  - `resource`：待审核资源详情（标题/类型/文件或链接/说明），供审核员参考决策。
  - `loading`：资源详情加载中态，拉取期间禁用提交。
  关键交互：
  - 展示资源标题、类型、文件名或链接URL、详细说明（MarkdownPreview 只读渲染）。
  - 单选「通过 / 驳回」；驳回时审核意见必填，通过时可选填。
  - 提交时向父层 emit submit({ resourceId, pass, advice })，父层调审核接口。
  - 主题适配：弹窗内单选、文本域、说明区均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElRadio, ElRadioGroup, ElEmpty } from 'element-plus'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import DictTag from '@/components/DictTag.vue'
import type { ResourceRecord, ResourceReviewPayload } from '@/types/api/knowhub/resource'

const props = defineProps<{
  visible: boolean
  resourceId: number | null
  resource: ResourceRecord | null
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: ResourceReviewPayload]
}>()

/** 审核决定：true 通过 → PUBLISHED；false 驳回 → REJECTED */
const pass = ref<boolean>(true)
/** 审核意见，驳回时必填 */
const advice = ref('')

/**
 * 方法效果：
 * 弹窗打开时重置为默认「通过、无意见」，避免上次输入残留。
 * 参数：
 * - `value`：当前弹窗显隐。
 * 返回值：
 * - 无返回值；副作用是重置表单状态。
 */
watch(
  () => props.visible,
  (value) => {
    if (value) {
      pass.value = true
      advice.value = ''
    }
  },
)

/** 驳回时审核意见必填，通过时可选；据此控制提交按钮禁用。加载中禁用提交。 */
const canSubmit = computed(
  () => !props.loading && (pass.value ? true : advice.value.trim().length > 0),
)

/**
 * 方法效果：
 * 提交审核决定，向父层抛出 submit 事件。
 * 参数：
 * - 无，直接读取当前表单状态与 resourceId。
 * 返回值：
 * - 无返回值；副作用是触发 submit 事件。
 */
const handleSubmit = () => {
  if (!props.resourceId || !canSubmit.value) {
    return
  }

  emit('submit', {
    resourceId: props.resourceId,
    pass: pass.value,
    advice: pass.value ? advice.value.trim() || undefined : advice.value.trim(),
  })
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="资源审核"
    width="760px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <!-- 待审核资源内容预览：标题 / 类型 / 文件或链接 / 说明 -->
    <section v-if="resource" class="resource-review__content">
      <h2 class="resource-review__title">{{ resource.title }}</h2>

      <div class="resource-review__meta">
        <span><em>类型</em><DictTag dict-key="resource_type" :value="resource.resourceType" /></span>
        <span v-if="resource.resourceType === 'FILE'"><em>文件</em>{{ resource.originalName || '已上传文件' }}</span>
        <span v-if="resource.resourceType === 'LINK'"><em>链接</em>{{ resource.linkUrl || '--' }}</span>
      </div>

      <p v-if="resource.summary" class="resource-review__summary">{{ resource.summary }}</p>

      <MarkdownPreview
        v-if="resource.description"
        class="resource-review__body"
        :model-value="resource.description"
      />
    </section>
    <ElEmpty v-else-if="!loading" description="未加载到资源内容" :image-size="56" />

    <ElForm label-width="80px" class="resource-review__form">
      <ElFormItem label="审核决定" required>
        <ElRadioGroup v-model="pass">
          <ElRadio :value="true">通过（发布）</ElRadio>
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
      <div class="resource-review-dialog__actions">
        <ElButton @click="emit('update:visible', false)">取消</ElButton>
        <ElButton type="primary" :disabled="!canSubmit" @click="handleSubmit">
          确认提交
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
/* 资源内容预览区：与详情弹窗同款视觉语言，深浅模式自动跟随 */
.resource-review__content {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.resource-review__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--rookie-text);
}

.resource-review__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
}

.resource-review__meta em {
  color: var(--rookie-text-tertiary);
  font-style: normal;
  margin-right: 6px;
}

.resource-review__summary {
  margin: 0;
  padding: 8px 12px;
  border-left: 3px solid var(--rookie-primary);
  background: var(--rookie-primary-soft);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  line-height: 1.6;
  border-radius: 0 var(--rookie-radius-sm) var(--rookie-radius-sm) 0;
}

.resource-review__body {
  max-height: 320px;
  overflow-y: auto;
  padding: 4px 0;
}

.resource-review__form {
  margin-top: 4px;
}

.resource-review-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
