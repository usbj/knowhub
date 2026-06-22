<script setup lang="ts">
import { computed } from 'vue'
import type { SideBarMenuButtonProps } from '@/types/components/navigation'

const props = defineProps<SideBarMenuButtonProps>()

/**
 * 层级深度用于弱化非顶层节点的视觉权重（图标更浅、文字更轻），
 * 辅助辨识"目录套目录"的层级关系；缩进统一由父级容器提供，不再在此叠加内边距，
 * 避免深层嵌套时文字可用宽度被反复压缩。
 */
const isNested = computed(() => (props.depth ?? 0) > 0)
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
        'is-nested': isNested,
      },
    ]"
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
  /* 缩进统一由父级 __children 的 padding 提供，按钮自身 padding 固定，避免深层嵌套文字被挤 */
  padding: 0 14px;
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
 * 嵌套层级（非顶层）的节点弱化视觉权重：图标略小、文字字重更轻，
 * 让"目录套目录"时子级在视觉上轻于父级，层级关系清晰但不靠缩进堆叠。
 */
.side-bar-menu-button.is-nested {
  font-weight: 500;
  color: var(--rookie-text-tertiary);
}

.side-bar-menu-button.is-nested .side-bar-menu-button__icon {
  width: 28px;
  height: 28px;
  background: transparent;
}

.side-bar-menu-button.is-nested .side-bar-menu-button__icon :deep(svg) {
  width: 16px;
  height: 16px;
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
