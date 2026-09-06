<!--
  App.vue —— knowhub 前台根组件
  ------------------------------------------------------------------
  meta.bare 的路由（登录/注册）自带全屏布局，直接渲染；其余包 AppLayout 壳体。
  路由切换淡入过渡。
-->
<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import { computed } from 'vue'
import AppLayout from '@/components/layout/AppLayout.vue'

const route = useRoute()
const isBare = computed(() => route.meta.bare === true)
</script>

<template>
  <AppLayout v-if="!isBare">
    <RouterView v-slot="{ Component }">
      <transition name="kh-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </RouterView>
  </AppLayout>
  <RouterView v-else />
</template>

<style>
/* 路由切换淡入：尊重 prefers-reduced-motion（base.css 已全局降级） */
.kh-fade-enter-active,
.kh-fade-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}
.kh-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.kh-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>