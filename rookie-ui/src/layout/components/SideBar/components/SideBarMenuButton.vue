<script setup lang="ts">
import { computed } from 'vue'
import type { SideBarMenuButtonProps } from '@/types/components/navigation'

const props = defineProps<SideBarMenuButtonProps>()

/**
 * 不同层级的菜单复用同一按钮结构，
 * 通过深度偏移控制缩进，避免为每一级单独维护一套样式。
 */
const depthOffset = computed(() => `${Math.max(props.depth ?? 0, 0) * 18}px`)
</script>

<template>
  <!-- 侧边栏菜单按钮区域 -->
  <div
    class="side-bar-menu-button"
    :class="[
      {
        'is-active': active,
        'is-ancestor-active': ancestorActive,
        'is-collapsed': collapsed,
      },
    ]"
    :style="{ '--side-bar-depth-offset': depthOffset }"
  >
    <span class="side-bar-menu-button__icon">
      <component :is="icon" />
    </span>

    <span v-if="!collapsed" class="side-bar-menu-button__label">{{ label }}</span>
  </div>
</template>

<style scoped>
.side-bar-menu-button {
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 0 14px 0 calc(14px + var(--side-bar-depth-offset, 0px));
  border-radius: var(--rookie-radius-md);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-md);
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.side-bar-menu-button__icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-surface-weak);
  border: 1px solid transparent;
  flex: none;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.side-bar-menu-button__icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.side-bar-menu-button__label {
  min-width: 0;
  flex: 1;
  font-size: var(--rookie-font-size-md);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/**
 * 目录被标记时只改变文字和图标颜色，不改变背景，
 * 这样能明确表达“当前展示的是它下面的菜单”，但不会误导成它自己被选中了。
 */
.side-bar-menu-button.is-ancestor-active {
  color: var(--rookie-primary-strong);
}

.side-bar-menu-button.is-ancestor-active .side-bar-menu-button__icon {
  color: var(--rookie-primary);
  border-color: var(--rookie-primary-border);
}

.side-bar-menu-button.is-active {
  color: var(--rookie-primary-strong);
  background: var(--rookie-primary-soft);
}

.side-bar-menu-button.is-active .side-bar-menu-button__icon {
  color: var(--rookie-primary);
  border-color: var(--rookie-primary-border);
  background: var(--rookie-surface-strong);
}

.side-bar-menu-button.is-collapsed {
  justify-content: center;
  width: 46px;
  margin-inline: auto;
  padding: 0;
}
</style>
