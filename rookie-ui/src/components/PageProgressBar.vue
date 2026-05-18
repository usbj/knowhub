<script setup lang="ts">
/**
 * 文件作用：
 * 渲染页面最上方的全局进度条，
 * 并根据页面切换状态展示当前加载进度。
 */
import { usePageTransition } from '@/composables/usePageTransition'

const pageTransition = usePageTransition()
</script>

<template>
  <!-- 页面顶部进度条区域 -->
  <Transition name="page-progress-bar">
    <div
      v-if="pageTransition.isPageTransitioning.value"
      class="page-progress-bar"
      aria-hidden="true"
    >
      <span class="page-progress-bar__value" :style="{ width: `${pageTransition.progress.value}%` }"></span>
    </div>
  </Transition>
</template>

<style scoped>
.page-progress-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 120;
  height: 3px;
  background: transparent;
  pointer-events: none;
}

.page-progress-bar__value {
  display: block;
  height: 100%;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--rookie-primary) 88%, white),
    var(--rookie-primary),
    var(--rookie-primary-strong)
  );
  box-shadow: 0 0 12px color-mix(in srgb, var(--rookie-primary) 30%, transparent);
  transition: width 0.2s ease;
}

.page-progress-bar-enter-active,
.page-progress-bar-leave-active {
  transition: opacity 0.18s ease;
}

.page-progress-bar-enter-from,
.page-progress-bar-leave-to {
  opacity: 0;
}
</style>
