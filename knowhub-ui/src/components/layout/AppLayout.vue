<!--
  AppLayout —— knowhub 前台主壳体
  ------------------------------------------------------------------
  顶栏 + 主内容区（router-view）+ 页脚。主内容区 min-height 撑满视口。
-->
<script setup lang="ts">
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
</script>

<template>
  <div class="kh-app">
    <AppHeader />
    <main class="kh-main">
      <slot />
    </main>
    <AppFooter />
  </div>
</template>

<style scoped>
.kh-app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.kh-main {
  flex: 1;
}
</style>
