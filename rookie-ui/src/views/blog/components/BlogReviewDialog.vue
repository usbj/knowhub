<!--
  文件作用：
  博客文章审核弹窗，承接博客列表「审核」操作。
  关键参数：
  - `visible`：弹窗显隐，由父层双向绑定控制。
  - `blogId`：当前待审核博客主键（仅展示，提交时回传）。
  关键交互：
  - 单选「通过 / 驳回」；驳回时审核意见必填，通过时可选填。
  - 提交时向父层 emit submit({ blogId, pass, advice })，父层调审核接口。
  - 主题适配：弹窗内单选、文本域均用 base.css 的 --rookie-* 变量，深浅模式自动跟随。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElRadio, ElRadioGroup } from 'element-plus'
import type { ReviewPayload } from '@/types/api/knowhub/blog'

const props = defineProps<{
  visible: boolean
  blogId: number | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: ReviewPayload]
}>()

/** 审核决定：true 通过 → PUBLISHED；false 驳回 → REJECTED */
const pass = ref<boolean>(true)
/** 审核意见，驳回时必填 */
const advice = ref('')

/**
 * 方法效果：
 * 弹窗打开时重置为默认「通过、无意见」，避免上次输入残留。
 * 数据流转：watch visible，true 时重置内部状态。
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

/** 驳回时审核意见必填，通过时可选；据此控制提交按钮禁用 */
const canSubmit = computed(() => (pass.value ? true : advice.value.trim().length > 0))

/**
 * 方法效果：
 * 提交审核决定，向父层抛出 submit 事件。
 * 参数：
 * - 无，直接读取当前表单状态与 blogId。
 * 返回值：
 * - 无返回值；副作用是触发 submit 事件。
 */
const handleSubmit = () => {
  if (!props.blogId || !canSubmit.value) {
    return
  }

  emit('submit', {
    blogId: props.blogId,
    pass: pass.value,
    advice: pass.value ? advice.value.trim() || undefined : advice.value.trim(),
  })
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="博客审核"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm label-width="80px">
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
      <div class="blog-review-dialog__actions">
        <ElButton @click="emit('update:visible', false)">取消</ElButton>
        <ElButton type="primary" :disabled="!canSubmit" @click="handleSubmit">
          确认提交
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.blog-review-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
