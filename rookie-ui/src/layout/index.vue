<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { startPageTransition } from '@/composables/usePageTransition'
import MainContentShell from './components/MainContentShell.vue'
import NavBar from './components/NavBar/index.vue'
import NoticeDetailDialog from '@/components/NoticeDetailDialog.vue'
import SideBar from './components/SideBar/index.vue'
import ThemeSettingsDrawer from './components/ThemeSettingsDrawer.vue'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useNoticeStore } from '@/stores/notice'
import type { SysNoticeRecord } from '@/types/api/system/notice'
import type { NotificationItem } from '@/types/components/theme'

const route = useRoute()
const layoutNavigationStore = useLayoutNavigationStore()
const noticeStore = useNoticeStore()
const collapsed = ref(false)
const settingsVisible = ref(false)
const currentViewKey = ref(0)
const noticeDetailVisible = ref(false)
const currentNotice = ref<SysNoticeRecord | null>(null)

/**
 * 布局层负责把"当前路由"同步给导航 store。
 * 这样侧边栏、面包屑、标签页会围绕同一份 currentPath 更新。
 */
watch(
  () => route.fullPath,
  () => {
    layoutNavigationStore.syncByRoute({
      path: route.path,
      meta: route.meta as Record<string, unknown>,
    })
  },
  { immediate: true },
)

/**
 * 方法效果：
 * 打开通知详情弹窗，展示完整正文（非下拉 summary），
 * 并在打开的同时调用 markAsRead 标记已读、刷新铃铛徽标。
 * 参数：
 * - `item`：头导航通知下拉传递的展示项，内含 noticeId 用于查完整记录。
 * 返回值：
 * - 无返回值；副作用是更新弹窗状态并将通知标记为已读。
 */
const openNoticeDetail = (item: NotificationItem) => {
  currentNotice.value = noticeStore.getNoticeById(item.id)
  noticeDetailVisible.value = true

  // 点开详情即标记已读，铃铛徽标即时减少
  if (item.unread) {
    noticeStore.markAsRead(item.id)
  }
}

const closeNoticeDetail = () => {
  noticeDetailVisible.value = false
}

/**
 * 方法效果：
 * 详情弹窗点击"确认"按钮时调用，调 store 确认接口乐观更新 hasConfirmed，
 * 确认成功后按钮自动隐藏（由 NoticeDetailDialog 的 showConfirmButton 计算）。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 无返回值；副作用是调用 store 确认方法。
 */
const handleConfirmNotice = (noticeId: number) => {
  noticeStore.confirmNotice(noticeId)
}

/**
 * 刷新当前菜单内容时，手动启动一次整页顶部进度条，
 * 再通过 viewKey 强制重挂载当前视图。
 */
const handleRefreshView = () => {
  startPageTransition()
  currentViewKey.value += 1
}
</script>

<template>
  <!-- 应用主布局区域 -->
  <div class="app-layout">
    <a class="skip-link" href="#rookie-main">跳到主内容</a>
    <SideBar :collapsed="collapsed" />
    <div class="app-layout__main-shell">
      <NavBar
        :collapsed="collapsed"
        @open-settings="settingsVisible = true"
        @open-notice="openNoticeDetail"
        @toggle-sidebar="collapsed = !collapsed"
        @refresh-view="handleRefreshView"
      />
      <MainContentShell :view-key="`${route.fullPath}-${currentViewKey}`" />
    </div>
    <NoticeDetailDialog
      :visible="noticeDetailVisible"
      :notice="currentNotice"
      @update:visible="noticeDetailVisible = $event"
      @confirm="handleConfirmNotice"
    />
    <ThemeSettingsDrawer v-model:visible="settingsVisible" />
  </div>
</template>

<style scoped>
.app-layout {
  display: grid;
  grid-template-columns: auto 1fr;
  height: 100vh;
  overflow: hidden;
}

.app-layout__main-shell {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.skip-link {
  position: absolute;
  left: 20px;
  top: 12px;
  z-index: 30;
  padding: 8px 12px;
  border-radius: var(--rookie-radius-sm);
  background: var(--rookie-primary);
  color: var(--rookie-text-inverse);
  transform: translateY(-160%);
  transition: transform 0.2s ease;
}

.skip-link:focus-visible {
  transform: translateY(0);
}

</style>
