<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import type { NavTabsProps } from '@/types/components/navigation'

defineProps<NavTabsProps>()

const emit = defineEmits<{
  close: [route: string]
}>()
</script>

<template>
  <!-- 标签页区域 -->
  <div class="nav-tabs" aria-label="页面标签">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.route"
      :to="tab.route"
      class="nav-tabs__item"
      :class="{ 'is-active': activeRoute === tab.route }"
    >
      <span class="nav-tabs__title">{{ tab.title }}</span>

      <button
        v-if="tabs.length > 1"
        class="nav-tabs__close"
        type="button"
        aria-label="关闭标签页"
        @click.prevent.stop="emit('close', tab.route)"
      >
        <Close />
      </button>
    </RouterLink>
  </div>
</template>

<style scoped>
.nav-tabs {
  display: flex;
  align-items: flex-end;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding-top: 0.5rem;
  scrollbar-width: none;
}

.nav-tabs::-webkit-scrollbar {
  display: none;
}

.nav-tabs__item {
  min-width: 0;
  max-width: 220px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  margin-bottom: -1px;
  border: 1px solid var(--rookie-border);
  border-right-width: 0;
  border-radius: var(--rookie-radius-lg) var(--rookie-radius-lg) 0 0;
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease;
}

.nav-tabs__item:last-child {
  border-right-width: 1px;
}

.nav-tabs__item:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.nav-tabs__item.is-active {
  position: relative;
  z-index: 1;
  color: var(--rookie-primary-strong);
  border-color: var(--rookie-border);
  border-bottom-color: var(--rookie-primary-soft);
  background: var(--rookie-primary-soft);
  box-shadow: 0 -1px 0 0 var(--rookie-primary) inset;
}

.nav-tabs__item.is-active::after {
  content: '';
  position: absolute;
  left: -1px;
  right: -1px;
  bottom: -1px;
  height: 1px;
  background: var(--rookie-primary-soft);
}

.nav-tabs__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--rookie-font-size-sm);
  font-weight: 600;
}

.nav-tabs__close {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--rookie-text-tertiary);
  cursor: pointer;
  flex: none;
}

.nav-tabs__close:hover {
  background: var(--rookie-muted-hover-bg);
  color: var(--rookie-text);
}

.nav-tabs__close :deep(svg) {
  width: 12px;
  height: 12px;
}
</style>
