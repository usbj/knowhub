<!--
  KhStatPill —— 统计小药丸（图标 + 数值 + 标签）
  ------------------------------------------------------------------
  用于卡片底部元信息行、统计区等。
-->
<script setup lang="ts">
import KhIcon from './KhIcon.vue'

defineProps<{
  /** 图标名（KhIcon 的 IconName） */
  icon: string
  value: string | number
  label?: string
  /** 图标色 */
  color?: string
}>()
</script>

<template>
  <span class="kh-stat-pill">
    <KhIcon :name="icon" :size="14" :style="{ color: color ?? 'var(--kh-text-tertiary)' }" />
    <span class="kh-stat-pill__value">{{ value }}</span>
    <span v-if="label" class="kh-stat-pill__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.kh-stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kh-text-tertiary);
  font-size: 12px;
  line-height: 1;
}
.kh-stat-pill__value {
  font-weight: 600;
  color: var(--kh-text-secondary);
  font-variant-numeric: tabular-nums;
}
.kh-stat-pill__label {
  color: var(--kh-text-tertiary);
}
</style>
