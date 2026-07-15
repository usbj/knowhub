<!--
  KhContentToc —— 内容目录卡（正文小标题列表）
  ------------------------------------------------------------------
  从正文 ## 标题提取的小标题列表，博客详情 / 文档阅读页共用同一套"目录"卡。
  items：小标题字符串数组；title：卡头标题，默认"目录"。
-->
<script setup lang="ts">
import KhCard from './KhCard.vue'
import KhIcon from './KhIcon.vue'

withDefaults(
  defineProps<{
    items: string[]
    title?: string
  }>(),
  {
    title: '目录',
  },
)
</script>

<template>
  <KhCard padding="md" class="kh-toc">
    <div class="kh-toc__head">
      <KhIcon name="doc" :size="16" /> {{ title }}
    </div>
    <ul v-if="items.length" class="kh-toc__list">
      <li v-for="(t, i) in items" :key="i" class="kh-toc__item">
        <span class="kh-toc__no">{{ String(i + 1).padStart(2, '0') }}</span>
        <span class="kh-toc__text kh-line-clamp-2">{{ t }}</span>
      </li>
    </ul>
    <div v-else class="kh-toc__empty">暂无目录</div>
  </KhCard>
</template>

<style scoped>
.kh-toc__head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-4);
}
.kh-toc__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kh-toc__item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--kh-radius-sm);
  cursor: default;
  transition: background var(--kh-transition-fast);
}
.kh-toc__item:hover {
  background: var(--kh-surface-muted);
}
.kh-toc__no {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
  padding-top: 1px;
}
.kh-toc__text {
  font-size: 13px;
  color: var(--kh-text-secondary);
  line-height: 1.4;
}
.kh-toc__empty {
  padding: var(--kh-space-3) 0;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  text-align: center;
}
</style>