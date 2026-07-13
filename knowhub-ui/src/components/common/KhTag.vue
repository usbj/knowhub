<!--
  KhTag —— 标签徽标
  ------------------------------------------------------------------
  多色变体（与内容类型/状态对应）。pill 圆角，柔和底色。
-->
<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 颜色变体 */
    type?: 'primary' | 'accent' | 'warm' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'
    /** 尺寸 */
    size?: 'sm' | 'md'
    /** 是否带圆点 */
    dot?: boolean
  }>(),
  {
    type: 'neutral',
    size: 'md',
    dot: false,
  },
)
</script>

<template>
  <span class="kh-tag" :class="[`kh-tag--${type}`, `kh-tag--${size}`]">
    <span v-if="dot" class="kh-tag__dot" />
    <slot />
  </span>
</template>

<style scoped>
.kh-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: var(--kh-radius-pill);
  font-weight: 500;
  line-height: 1.5;
  white-space: nowrap;
  border: 1px solid transparent;
}
.kh-tag--sm {
  font-size: 11px;
  padding: 1px 8px;
}
.kh-tag--md {
  font-size: 12px;
}
.kh-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.kh-tag--primary {
  color: var(--kh-primary-strong);
  background: var(--kh-primary-soft);
  border-color: var(--kh-primary-border);
}
.kh-tag--accent {
  color: #0369a1;
  background: var(--kh-accent-soft);
  border-color: rgba(14, 165, 233, 0.25);
}
.kh-tag--warm {
  color: #b45309;
  background: var(--kh-warm-soft);
  border-color: rgba(245, 158, 11, 0.28);
}
.kh-tag--success {
  color: #15803d;
  background: var(--kh-success-soft);
  border-color: rgba(22, 163, 74, 0.24);
}
.kh-tag--warning {
  color: #b45309;
  background: var(--kh-warning-soft);
  border-color: rgba(217, 119, 6, 0.24);
}
.kh-tag--danger {
  color: #b91c1c;
  background: var(--kh-danger-soft);
  border-color: rgba(220, 38, 38, 0.22);
}
.kh-tag--info {
  color: #4f46e5;
  background: var(--kh-info-soft);
  border-color: rgba(99, 102, 241, 0.24);
}
.kh-tag--neutral {
  color: var(--kh-text-secondary);
  background: var(--kh-bg-soft);
  border-color: var(--kh-border);
}
</style>
