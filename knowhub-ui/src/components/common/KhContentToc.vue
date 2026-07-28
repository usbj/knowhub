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

/**
 * 点击目录项：向父发出该项序号（i）。父按序号取正文内第 i 个 h2（v-md-preview 渲染的
 * github-markdown-body 下的 h2）scrollIntoView。序号对齐靠"toc 从 ## 提取、h2 也由 ## 渲染"
 * 的一一对应，不依赖 slug/锚点插件（v-md-editor github 主题默认不给 heading 加 id）。
 */
const emit = defineEmits<{ (e: 'select', index: number): void }>()

const onSelect = (i: number) => emit('select', i)
</script>

<template>
  <KhCard padding="md" class="kh-toc">
    <div class="kh-toc__head">
      <KhIcon name="doc" :size="16" /> {{ title }}
    </div>
    <ul v-if="items.length" class="kh-toc__list">
      <li
        v-for="(t, i) in items"
        :key="i"
        class="kh-toc__item"
        role="button"
        tabindex="0"
        @click="onSelect(i)"
        @keydown.enter.prevent="onSelect(i)"
      >
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
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.kh-toc__item:hover {
  background: var(--kh-surface-muted);
}
.kh-toc__item:hover .kh-toc__text {
  color: var(--kh-primary);
}
.kh-toc__item:focus-visible {
  outline: 2px solid var(--kh-primary-border);
  outline-offset: -2px;
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