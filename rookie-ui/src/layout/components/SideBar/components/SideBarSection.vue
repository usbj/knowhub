<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ElPopover, ElTooltip } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import type { NavigationMenuItem, SideBarSectionProps } from '@/types/components/navigation'
import SideBarMenuButton from './SideBarMenuButton.vue'

defineOptions({
  name: 'SideBarSection',
})

const props = defineProps<SideBarSectionProps>()

const emit = defineEmits<{
  toggle: [menuId: number]
}>()

/**
 * 目录节点负责展开收起，菜单节点负责真实跳转。
 * 这里直接按后端 menuType 和 backlinks 属性驱动表现。
 */
const isDirectory = computed(() => props.item.menuType === 1)
const isExternalLink = computed(() => props.item.backlinks === 1)
const currentDepth = computed(() => props.depth ?? 0)

const selfActive = computed(() => props.item.menuType === 2 && props.item.route === props.currentPath)

const hasActiveDescendant = (children: NavigationMenuItem[]): boolean =>
  children.some((child) => child.route === props.currentPath || hasActiveDescendant(child.children))

const ancestorActive = computed(() => hasActiveDescendant(props.item.children))

const flattenLeafMenus = (children: NavigationMenuItem[], trail: string[] = []) =>
  children.flatMap((child) => {
    const nextTrail = [...trail, child.menuName]

    if (child.menuType === 2) {
      return [
        {
          menuId: child.menuId,
          label: nextTrail.join(' / '),
          route: child.route,
          backlinks: child.backlinks,
        },
      ]
    }

    return flattenLeafMenus(child.children, nextTrail)
  })

const collapsedMenuItems = computed(() => flattenLeafMenus(props.item.children))

const tooltipLabel = computed(() => {
  if (!isDirectory.value) {
    return props.item.menuName
  }

  return `${props.item.menuName}：${props.item.children.map((child) => child.menuName).join(' / ')}`
})
</script>

<template>
  <!-- 侧边栏菜单分组区域 -->
  <section class="side-bar-section">
    <ElPopover
      v-if="collapsed && isDirectory"
      placement="right-start"
      trigger="click"
      width="220"
      popper-class="side-bar-section__popover"
    >
      <template #reference>
        <button class="side-bar-section__link side-bar-section__directory-button" type="button">
          <SideBarMenuButton
            :label="item.menuName"
            :icon="item.iconComponent"
            :ancestor-active="ancestorActive"
            :collapsed="collapsed"
            :depth="currentDepth"
          />
        </button>
      </template>

      <div class="side-bar-section__popover-menu">
        <template v-for="child in collapsedMenuItems" :key="child.menuId">
          <a
            v-if="child.backlinks === 1"
            :href="child.route"
            class="side-bar-section__popover-link"
            target="_blank"
            rel="noreferrer"
          >
            {{ child.label }}
          </a>
          <RouterLink v-else :to="child.route" class="side-bar-section__popover-link">
            {{ child.label }}
          </RouterLink>
        </template>
      </div>
    </ElPopover>

    <template v-else>
      <ElTooltip
        :disabled="!collapsed"
        :content="tooltipLabel"
        placement="right"
        effect="light"
        :show-after="120"
      >
        <button
          v-if="isDirectory"
          class="side-bar-section__link side-bar-section__directory-button"
          type="button"
          @click="emit('toggle', item.menuId)"
        >
          <SideBarMenuButton
            :label="item.menuName"
            :icon="item.iconComponent"
            :ancestor-active="ancestorActive"
            :collapsed="collapsed"
            :depth="currentDepth"
          />
          <ArrowDown
            v-if="!collapsed"
            class="side-bar-section__arrow"
            :class="{ 'is-expanded': expanded }"
          />
        </button>

        <a
          v-else-if="isExternalLink"
          :href="item.route"
          class="side-bar-section__link"
          target="_blank"
          rel="noreferrer"
        >
          <SideBarMenuButton
            :label="item.menuName"
            :icon="item.iconComponent"
            :active="selfActive"
            :collapsed="collapsed"
            :depth="currentDepth"
          />
        </a>

        <RouterLink v-else :to="item.route" class="side-bar-section__link">
          <SideBarMenuButton
            :label="item.menuName"
            :icon="item.iconComponent"
            :active="selfActive"
            :collapsed="collapsed"
            :depth="currentDepth"
          />
        </RouterLink>
      </ElTooltip>
    </template>

    <Transition name="side-bar-section-expand">
      <div v-if="isDirectory && expanded && !collapsed" class="side-bar-section__children">
        <SideBarSection
          v-for="child in item.children"
          :key="child.menuId"
          :item="child"
          :collapsed="collapsed"
          :current-path="currentPath"
          :expanded="expandedDirectoryIds.includes(child.menuId)"
          :expanded-directory-ids="expandedDirectoryIds"
          :depth="currentDepth + 1"
          @toggle="emit('toggle', $event)"
        />
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.side-bar-section {
  display: grid;
  gap: 8px;
}

.side-bar-section__link {
  display: block;
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.side-bar-section__directory-button {
  position: relative;
}

.side-bar-section__link:focus-visible {
  outline: none;
}

.side-bar-section__link:focus-visible :deep(.side-bar-menu-button) {
  box-shadow: var(--rookie-focus-ring);
}

.side-bar-section__link:hover :deep(.side-bar-menu-button) {
  color: var(--rookie-text);
  background: var(--rookie-hover-bg);
}

.side-bar-section__link:hover :deep(.side-bar-menu-button__icon) {
  border-color: var(--rookie-primary-border-soft);
}

.side-bar-section__link:hover :deep(.side-bar-menu-button.is-active) {
  background: var(--rookie-active-hover-bg);
  color: var(--rookie-primary-strong);
}

.side-bar-section__children {
  display: grid;
  gap: 8px;
  padding-left: 10px;
  border-left: 1px solid var(--rookie-border-strong);
  margin-left: 17px;
  overflow: hidden;
}

.side-bar-section__arrow {
  position: absolute;
  right: 14px;
  top: 50%;
  width: 14px;
  height: 14px;
  color: var(--rookie-text-tertiary);
  transform: translateY(-50%);
  transition: transform 0.2s ease;
}

.side-bar-section__arrow.is-expanded {
  transform: translateY(-50%) rotate(180deg);
}

.side-bar-section__popover-menu {
  display: grid;
  gap: 6px;
}

.side-bar-section__popover-link {
  min-height: 36px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-radius: var(--rookie-radius-sm);
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.side-bar-section__popover-link:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.side-bar-section-expand-enter-active,
.side-bar-section-expand-leave-active {
  transition:
    max-height 0.24s ease,
    opacity 0.24s ease,
    margin-top 0.24s ease;
  overflow: hidden;
}

.side-bar-section-expand-enter-from,
.side-bar-section-expand-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: -4px;
}

.side-bar-section-expand-enter-to,
.side-bar-section-expand-leave-from {
  max-height: 520px;
  opacity: 1;
  margin-top: 0;
}
</style>
