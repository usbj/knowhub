<!--
  KhRating —— 评分星级
  ------------------------------------------------------------------
  只读展示评分（0-5，支持半星视觉近似）。size 可调。
-->
<script setup lang="ts">
import { computed } from 'vue'
import KhIcon from './KhIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 评分 0-5 */
    value: number
    size?: number
    /** 是否显示数值 */
    showValue?: boolean
  }>(),
  {
    size: 14,
    showValue: false,
  },
)

/** 5 颗星的填充百分比 */
const stars = computed(() => {
  return Array.from({ length: 5 }, (_, i) => {
    const filled = props.value - i
    return Math.max(0, Math.min(1, filled))
  })
})
</script>

<template>
  <span class="kh-rating">
    <span class="kh-rating__stars" aria-hidden="true">
      <span v-for="(p, i) in stars" :key="i" class="kh-rating__star" :style="{ width: `${size}px`, height: `${size}px` }">
        <KhIcon name="star" :size="size" :stroke="1.5" class="kh-rating__empty" />
        <span class="kh-rating__fill" :style="{ width: `${p * 100}%` }">
          <KhIcon name="star" :size="size" :stroke="1.5" />
        </span>
      </span>
    </span>
    <span v-if="showValue" class="kh-rating__value">{{ value.toFixed(1) }}</span>
  </span>
</template>

<style scoped>
.kh-rating {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.kh-rating__stars {
  display: inline-flex;
}
.kh-rating__star {
  position: relative;
  display: inline-block;
  color: var(--kh-border-strong);
}
.kh-rating__fill {
  position: absolute;
  inset: 0;
  overflow: hidden;
  display: inline-flex;
  color: var(--kh-warm);
}
.kh-rating__value {
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-text-secondary);
  font-variant-numeric: tabular-nums;
}
</style>
