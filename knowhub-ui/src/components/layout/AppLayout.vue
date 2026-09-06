<!--
  AppLayout —— knowhub 前台主壳体
  ------------------------------------------------------------------
  顶栏 + 主内容区（router-view）+ 页脚。主内容区 min-height 撑满视口。
-->
<script setup lang="ts">
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import KhNoticeDetailDialog from './KhNoticeDetailDialog.vue'
import { useNoticeStore } from '@/stores/notice'

/** 全局通知详情弹窗态挂在 noticeStore，AppLayout 只绑定 visible/notice 给全局组件 */
const noticeStore = useNoticeStore()
</script>

<template>
  <div class="kh-app">
    <AppHeader />
    <main class="kh-main">
      <slot />
    </main>
    <AppFooter />

    <!-- 全局通知详情弹窗：顶栏下拉点通知项 / 公告列表页查看详情 / 任意位置都可触发 -->
    <KhNoticeDetailDialog
      v-model:visible="noticeStore.detailVisible"
      :notice="noticeStore.currentNotice"
    />
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
