/**
 * 文件作用：
 * 封装 v-md-editor 编辑器为受控组件，供公共表单 inputType:'markdown' 复用。
 * 关键参数：
 * - `modelValue`：markdown 原文字符串，由外层表单字段双向绑定。
 * - `placeholder` / `height`：占位文案与编辑器高度。
 * 关键依赖：
 * - 编辑器组件已在 utils/markdown 经 app.use 全局注册为 v-md-editor，
 *   主题（github + highlight.js）与中文语言同处统一注入。
 */
<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    height?: string
  }>(),
  {
    placeholder: '请输入正文内容（支持 Markdown）',
    height: '420px',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <v-md-editor
    :model-value="modelValue"
    :placeholder="placeholder"
    :height="height"
    mode="edit"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
