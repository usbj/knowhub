<!--
  KhEmpty —— 空状态（温暖插画占位）
  ------------------------------------------------------------------
  温暖社区感：柔和插画块 + 文案。用于列表无数据、搜索无结果等。
-->
<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
    size?: 'sm' | 'md' | 'lg'
  }>(),
  {
    title: '暂无内容',
    description: '稍后再来看看，或许会有新的惊喜',
    size: 'md',
  },
)
</script>

<template>
  <div class="kh-empty" :class="`kh-empty--${size}`">
    <div class="kh-empty__art" aria-hidden="true">
      <div class="kh-empty__circle" />
      <div class="kh-empty__square" />
      <div class="kh-empty__dot" />
    </div>
    <h4 class="kh-empty__title">{{ title }}</h4>
    <p class="kh-empty__desc">{{ description }}</p>
  </div>
</template>

<style scoped>
.kh-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--kh-space-12) var(--kh-space-6);
}
.kh-empty__art {
  position: relative;
  width: 120px;
  height: 80px;
  margin-bottom: var(--kh-space-4);
}
.kh-empty__circle {
  position: absolute;
  left: 12px;
  bottom: 0;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--kh-gradient-warm);
  opacity: 0.6;
}
.kh-empty__square {
  position: absolute;
  right: 18px;
  bottom: 8px;
  width: 48px;
  height: 48px;
  border-radius: var(--kh-radius);
  background: var(--kh-gradient-card);
  border: 1px solid var(--kh-border-soft);
}
.kh-empty__dot {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--kh-accent);
  opacity: 0.5;
}
.kh-empty__title {
  font-size: var(--kh-font-size-lg);
  color: var(--kh-text);
  margin-bottom: 6px;
}
.kh-empty__desc {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.kh-empty--sm {
  padding: var(--kh-space-6);
}
.kh-empty--sm .kh-empty__art {
  width: 80px;
  height: 56px;
}
</style>
