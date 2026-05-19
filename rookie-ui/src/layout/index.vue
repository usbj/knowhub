<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { startPageTransition } from '@/composables/usePageTransition'
import MainContentShell from './components/MainContentShell.vue'
import NavBar from './components/NavBar/index.vue'
import PromptPanel from './components/PromptPanel.vue'
import SideBar from './components/SideBar/index.vue'
import ThemeSettingsDrawer from './components/ThemeSettingsDrawer.vue'
import { useLayoutNavigationStore } from '@/stores/navigation'
import type { PromptMode, PromptNoticeMeta } from '@/types/components/prompt'
import type { NotificationItem } from '@/types/components/theme'

const route = useRoute()
const layoutNavigationStore = useLayoutNavigationStore()
const collapsed = ref(false)
const settingsVisible = ref(false)
const currentViewKey = ref(0)
const promptVisible = ref(false)
const promptMode = ref<PromptMode>('prompt')
const promptTitle = ref('')
const promptContent = ref('')
const promptNoticeMeta = ref<PromptNoticeMeta>()

/**
 * 布局层负责把“当前路由”同步给导航 store。
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

const openNoticePrompt = (item: NotificationItem) => {
  promptMode.value = 'notice'
  promptVisible.value = true
  promptTitle.value = item.title
  promptContent.value = item.summary
  promptNoticeMeta.value = {
    publisher: item.publisher || '系统公告',
    publishTime: item.time,
    category: item.category,
  }
}

const closePrompt = () => {
  promptVisible.value = false
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
        @open-notice="openNoticePrompt"
        @toggle-sidebar="collapsed = !collapsed"
        @refresh-view="handleRefreshView"
      />
      <MainContentShell :view-key="`${route.fullPath}-${currentViewKey}`" />
    </div>
    <PromptPanel
      :visible="promptVisible"
      :mode="promptMode"
      :title="promptTitle"
      :content="promptContent"
      :notice-meta="promptNoticeMeta"
      @close="closePrompt"
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
