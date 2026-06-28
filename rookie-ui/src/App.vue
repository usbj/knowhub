<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PageProgressBar from '@/components/PageProgressBar.vue'

const route = useRoute()

/**
 * 根级页面切换 key。
 * 这里只取第一层匹配路由，让登录页和后台主布局之间有整体过渡，
 * 同时避免后台内部菜单切换触发整页动画。
 */
const rootRouteKey = computed(() => route.matched[0]?.path || route.fullPath)
</script>

<template>
  <!-- 页面根区域 -->
  <div class="app-shell">
    <PageProgressBar />

    <!-- 根路由内容区域 -->
    <RouterView v-slot="{ Component }">
      <div :key="rootRouteKey" class="app-shell__stage">
        <component :is="Component" />
      </div>
    </RouterView>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-shell__stage {
  min-height: 100vh;
  animation: app-shell-stage-enter 0.28s ease;
}

@keyframes app-shell-stage-enter {
  0% {
    opacity: 0;
    transform: translateY(14px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
