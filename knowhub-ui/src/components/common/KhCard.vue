<!--
  KhCard —— knowhub 前台统一卡片容器
  ------------------------------------------------------------------
  大圆角 + 轻阴影 + hover lift。可选 clickable（点击跳转/触发）。
  padding 可调，可选渐变装饰底。
-->
<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 是否可点击（加 hover lift + cursor pointer） */
    clickable?: boolean
    /** 内边距档位 */
    padding?: 'none' | 'sm' | 'md' | 'lg'
    /** 是否带柔和渐变底 */
    gradient?: boolean
  }>(),
  {
    clickable: false,
    padding: 'md',
    gradient: false,
  },
)
</script>

<template>
  <div
    class="kh-card"
    :class="[`kh-card--p-${padding}`, { 'kh-card--clickable': clickable, 'kh-card--gradient': gradient }]"
  >
    <slot />
  </div>
</template>

<style scoped>
.kh-card {
  position: relative;
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  box-shadow: var(--kh-shadow-xs);
  transition:
    transform var(--kh-transition),
    box-shadow var(--kh-transition),
    border-color var(--kh-transition);
  overflow: hidden;
}
.kh-card--gradient::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--kh-gradient-card);
  pointer-events: none;
}
.kh-card--clickable {
  cursor: pointer;
}
.kh-card--clickable:hover {
  transform: translateY(-4px);
  box-shadow: var(--kh-shadow-lg);
  border-color: var(--kh-border-strong);
}
.kh-card--p-none {
  padding: 0;
}
.kh-card--p-sm {
  padding: var(--kh-space-4);
}
.kh-card--p-md {
  padding: var(--kh-space-6);
}
.kh-card--p-lg {
  padding: var(--kh-space-8);
}
</style>
