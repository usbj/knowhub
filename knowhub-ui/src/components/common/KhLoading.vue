<!--
  KhLoading —— 统一加载占位（详情页级别）
  ------------------------------------------------------------------
  温暖社区感：与 KhEmpty 同源插画块（渐变圆球 + 卡片方块 + 小点），
  但点用 pulse 动画 + 标题文案，传达"正在加载"而非"无数据"。
  用于博客/项目/文章/章节/资源等动态详情页首屏取数时的统一加载占位，
  替代各页原先 `v-if="data"` 守护导致的纯空白帧。
  用法：
    <KhLoading />                              默认 title="加载中…"
    <KhLoading title="正在拉取章节…" />        自定义文案
  size：sm（行内小块）/ md（默认，详情页主体级）/ lg（全屏占位，如路由切换）。
-->
<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    size?: 'sm' | 'md' | 'lg'
  }>(),
  {
    title: '加载中…',
    size: 'md',
  },
)
</script>

<template>
  <div class="kh-loading" :class="`kh-loading--${size}`" role="status" aria-live="polite">
    <div class="kh-loading__art" aria-hidden="true">
      <div class="kh-loading__circle" />
      <div class="kh-loading__square" />
      <div class="kh-loading__dot" />
    </div>
    <p class="kh-loading__text">{{ title }}</p>
  </div>
</template>

<style scoped>
.kh-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--kh-space-12) var(--kh-space-6);
}
.kh-loading__art {
  position: relative;
  width: 120px;
  height: 80px;
  margin-bottom: var(--kh-space-4);
}
/* 与 KhEmpty 同源插画，但加 pulse 动画——区分"加载中"与"空状态" */
.kh-loading__circle {
  position: absolute;
  left: 12px;
  bottom: 0;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--kh-gradient-warm);
  opacity: 0.6;
  animation: kh-loading-pulse 1.4s ease-in-out infinite;
}
.kh-loading__square {
  position: absolute;
  right: 18px;
  bottom: 8px;
  width: 48px;
  height: 48px;
  border-radius: var(--kh-radius);
  background: var(--kh-gradient-card);
  border: 1px solid var(--kh-border-soft);
  animation: kh-loading-pulse 1.4s ease-in-out infinite;
  animation-delay: 0.4s;
}
.kh-loading__dot {
  position: absolute;
  right: 8px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--kh-accent);
  opacity: 0.5;
  animation: kh-loading-pulse 1.4s ease-in-out infinite;
  animation-delay: 0.8s;
}
.kh-loading__text {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  letter-spacing: 0.04em;
}
.kh-loading--sm {
  padding: var(--kh-space-6);
}
.kh-loading--sm .kh-loading__art {
  width: 80px;
  height: 56px;
}
.kh-loading--lg {
  min-height: 60vh;
}
.kh-loading--lg .kh-loading__art {
  width: 160px;
  height: 108px;
}
.kh-loading--lg .kh-loading__text {
  font-size: var(--kh-font-size-md);
}

@keyframes kh-loading-pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.08); opacity: 0.9; }
}

@media (prefers-reduced-motion: reduce) {
  .kh-loading__circle,
  .kh-loading__square,
  .kh-loading__dot {
    animation: none;
  }
}
</style>