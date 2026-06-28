<script setup lang="ts">
import { House, ArrowRight } from '@element-plus/icons-vue'
import type { NavBreadcrumbProps } from '@/types/components/navigation'

defineProps<NavBreadcrumbProps>()
</script>

<template>
  <nav class="nav-breadcrumb" aria-label="面包屑导航">
    <span class="nav-breadcrumb__item nav-breadcrumb__item--home">
      <House />
      <span>首页</span>
    </span>

    <template v-for="item in items" :key="item.menuId">
      <ArrowRight class="nav-breadcrumb__separator" />
      <span class="nav-breadcrumb__item">{{ item.menuName }}</span>
    </template>
  </nav>
</template>

<style scoped>
.nav-breadcrumb {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-lg);
}

.nav-breadcrumb__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  white-space: nowrap;
}

.nav-breadcrumb__item--home {
  color: var(--rookie-text-secondary);
}

.nav-breadcrumb__separator {
  width: 16px;
  height: 16px;
  flex: none;
}
</style>
