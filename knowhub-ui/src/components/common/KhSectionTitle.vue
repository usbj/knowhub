<!--
  KhSectionTitle —— 分标题
  ------------------------------------------------------------------
  大标题 + 副标题 + 右侧"查看更多"链接。首页/各列表页通用。
-->
<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'

defineProps<{
  title: string
  subtitle?: string
  /** 查看更多链接，不传则不显示 */
  moreTo?: string
  moreText?: string
}>()
</script>

<template>
  <div class="kh-section-title">
    <div class="kh-section-title__head">
      <h2 class="kh-section-title__title">{{ title }}</h2>
      <!-- 右侧自定义位（slot）：可在标题区右侧放附加元素（如行动按钮），与"查看更多"互斥或共用一行 -->
      <div v-if="$slots.default" class="kh-section-title__slot">
        <slot />
      </div>
      <RouterLink v-if="moreTo" :to="moreTo" class="kh-section-title__more">
        {{ moreText ?? '查看更多' }}
        <el-icon><ArrowRight /></el-icon>
      </RouterLink>
    </div>
    <p v-if="subtitle" class="kh-section-title__sub">{{ subtitle }}</p>
  </div>
</template>

<style scoped>
.kh-section-title {
  margin-bottom: var(--kh-space-5);
}
.kh-section-title__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kh-space-4);
}
.kh-section-title__title {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
  letter-spacing: -0.01em;
}
.kh-section-title__slot {
  margin-left: auto;
  flex: none;
}
.kh-section-title__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-primary);
  font-weight: 500;
  cursor: pointer;
  transition: gap var(--kh-transition-fast);
}
.kh-section-title__more:hover {
  gap: 6px;
}
.kh-section-title__sub {
  margin-top: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
</style>
