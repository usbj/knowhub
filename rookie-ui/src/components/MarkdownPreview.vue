/**
 * 文件作用：
 * 封装 v-md-editor 预览为只读展示组件，供通知详情等博客式正文渲染复用。
 * 关键参数：
 * - `modelValue`：markdown 原文字符串，对外保持受控组件约定。
 * 关键依赖：
 * - 预览组件已在 utils/markdown 经 app.use 全局注册为 v-md-preview，
 *   主题（github + highlight.js）与中文语言同处统一注入。
 * 关键约定：
 * - v-md-preview 的 prop 名是 `text`（不是 modelValue），这里对外收 modelValue、
 *   对内绑 :text，否则正文不渲染。
 */
<script setup lang="ts">
defineProps<{
  modelValue: string
}>()
</script>

<template>
  <v-md-preview :text="modelValue" />
</template>
