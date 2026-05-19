<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed, ref } from 'vue'
import {
  ArrowLeftBold,
  ArrowRightBold,
  ArrowDown,
  Bell,
  Close,
  MoonNight,
  Refresh,
  Search,
  Setting,
  Sunny,
} from '@element-plus/icons-vue'
import { ElBadge, ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useThemePreferenceStore } from '@/stores/themePreference'
import { unregisterDynamicRoutes } from '@/router/dynamicRoutes'
import type { NotificationItem } from '@/types/components/theme'
import NavBreadcrumb from './components/NavBreadcrumb.vue'
import NavTabs from './components/NavTabs.vue'

defineProps<{
  collapsed: boolean
}>()

const router = useRouter()
const route = useRoute()
/**
 * 当前用户状态由 user store 统一维护。
 * 顶部导航只读取展示，不直接处理登录接口。
 */
const userStore = useUserStore()
const layoutNavigationStore = useLayoutNavigationStore()
const themePreferenceStore = useThemePreferenceStore()
const emit = defineEmits<{
  openSettings: []
  toggleSidebar: []
  refreshView: []
  openNotice: [item: NotificationItem]
}>()

/**
 * 当前通知列表先保持空数组，等待后端真实通知接口接入。
 * 这样可以避免界面继续依赖本地演示数据。
 */
const notifications = ref<NotificationItem[]>([])

const unreadNotificationCount = computed(
  () => notifications.value.filter((item) => item.unread).length,
)

/**
 * 关闭标签后，如果关掉的是当前页，就自动切到相邻标签，
 * 这样顶部标签栏和主内容区不会出现“当前页面还开着但标签没了”的断裂状态。
 */
const handleCloseTab = async (targetRoute: string) => {
  const nextRoute = layoutNavigationStore.closeTab(targetRoute)

  if (targetRoute === layoutNavigationStore.currentPath && nextRoute) {
    await router.push(nextRoute)
  }
}

/**
 * 当前页刷新通过重新挂载视图完成，不改动当前路由与参数。
 */
const refreshCurrentTab = () => {
  emit('refreshView')
}

/**
 * 方法效果：
 * 处理标签栏批量操作命令，例如关闭其他标签或关闭全部标签。
 * 参数：
 * - `command`：下拉菜单返回的操作命令。
 * 返回值：
 * - 无返回值；副作用是更新导航 store 中的标签状态，并在必要时触发路由跳转。
 */
const handleTabAction = async (command: string) => {
  if (command === 'close-other') {
    layoutNavigationStore.closeOtherTabs(layoutNavigationStore.currentPath)
    return
  }

  if (command === 'close-all') {
    const nextRoute = layoutNavigationStore.closeAllTabs()
    if (nextRoute && nextRoute !== route.path) {
      await router.push(nextRoute)
    }
  }
}

/**
 * 方法效果：
 * 处理当前用户下拉菜单命令，包括跳转个人中心和退出登录。
 * 参数：
 * - `command`：下拉菜单返回的操作命令。
 * 返回值：
 * - 无返回值；副作用是执行对应跳转或清理登录状态。
 */
const handleProfileCommand = async (command: string) => {
  if (command === 'profile') {
    await router.push('/account/profile')
    return
  }

  if (command === 'logout') {
    userStore.logout()
    layoutNavigationStore.resetNavigationState()
    unregisterDynamicRoutes(router)
    await router.replace('/login')
  }
}
</script>

<template>
  <!-- 头导航区域 -->
  <header class="nav-bar">
    <div class="nav-bar__top-row">
      <div class="nav-bar__identity">
        <button
          class="nav-bar__icon-button nav-bar__collapse-button"
          type="button"
          aria-label="切换侧边栏"
          @click="emit('toggleSidebar')"
        >
          <ArrowRightBold v-if="$props.collapsed" />
          <ArrowLeftBold v-else />
        </button>

        <NavBreadcrumb :items="layoutNavigationStore.breadcrumbs" />
      </div>

      <div class="nav-bar__actions">
        <label class="nav-bar__search" aria-label="搜索">
          <Search class="nav-bar__search-icon" />
          <input type="text" placeholder="搜索菜单、页面或操作" />
          <span class="nav-bar__shortcut">Ctrl K</span>
        </label>

        <button class="nav-bar__icon-button" type="button" aria-label="切换明暗主题" @click="themePreferenceStore.toggleThemeMode()">
          <Sunny v-if="themePreferenceStore.isDarkMode" />
          <MoonNight v-else />
        </button>

        <ElDropdown
          trigger="click"
          placement="bottom-end"
          popper-class="nav-bar-notice-dropdown"
          :show-arrow="false"
        >
          <button class="nav-bar__icon-button" type="button" aria-label="通知">
            <ElBadge :value="unreadNotificationCount" :hidden="unreadNotificationCount === 0">
              <Bell />
            </ElBadge>
          </button>

          <template #dropdown>
            <ElDropdownMenu class="nav-bar__notice-menu">
              <div class="nav-bar__notice-head">通知</div>
              <div v-if="notifications.length === 0" class="nav-bar__notice-empty">暂无通知</div>
              <ElDropdownItem
                v-for="item in notifications"
                :key="item.id"
                class="nav-bar__notice-item"
                @click="emit('openNotice', item)"
              >
                <div class="nav-bar__notice-copy">
                  <strong>
                    {{ item.title }}
                    <span v-if="item.unread" class="nav-bar__notice-dot"></span>
                  </strong>
                  <span>{{ item.summary }}</span>
                  <small>{{ item.time }}</small>
                </div>
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>

        <button class="nav-bar__icon-button" type="button" aria-label="界面设置" @click="emit('openSettings')">
          <Setting />
        </button>

        <ElDropdown
          trigger="click"
          placement="bottom-end"
          popper-class="nav-bar-profile-dropdown"
          @command="handleProfileCommand"
        >
          <button class="nav-bar__profile" type="button" aria-label="当前用户菜单">
            <span class="nav-bar__profile-avatar">
              {{ userStore.displayName.slice(0, 1).toUpperCase() }}
            </span>
            <span class="nav-bar__profile-copy">
              <strong>{{ userStore.displayName }}</strong>
            </span>
            <ArrowDown class="nav-bar__profile-arrow" />
          </button>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="profile">个人中心</ElDropdownItem>
              <ElDropdownItem command="logout">退出登录</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>

    <div class="nav-bar__tab-row">
      <NavTabs
        :tabs="layoutNavigationStore.visitedTabs"
        :active-route="layoutNavigationStore.currentPath"
        @close="handleCloseTab"
      />

      <div class="nav-bar__tab-actions">
        <button class="nav-bar__tab-button" type="button" aria-label="刷新当前标签页" @click="refreshCurrentTab">
          <Refresh />
        </button>

        <ElDropdown trigger="click" placement="bottom-end" popper-class="nav-bar-profile-dropdown" @command="handleTabAction">
          <button class="nav-bar__tab-button" type="button" aria-label="标签操作">
            <Close />
          </button>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="close-other">关闭其他标签</ElDropdownItem>
              <ElDropdownItem command="close-all">关闭所有标签</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>

  </header>
</template>

<style scoped>
.nav-bar {
  display: grid;
  background: var(--rookie-surface-strong);
  backdrop-filter: blur(14px);
  position: sticky;
  top: 0;
  z-index: 20;
}

.nav-bar__top-row,
.nav-bar__tab-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding-inline: 24px;
}

.nav-bar__top-row {
  padding-top: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--rookie-border);
}

.nav-bar__tab-row {
  min-height: 54px;
  align-items: flex-end;
  border-bottom: 1px solid var(--rookie-border);
}

.nav-bar__identity {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.nav-bar__collapse-button {
  flex: none;
}

.nav-bar__search {
  width: min(304px, 22vw);
  height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-bg-elevated);
  box-shadow: var(--rookie-input-shadow);
}

.nav-bar__search:focus-within {
  border-color: var(--rookie-primary-border);
  box-shadow: var(--rookie-input-focus-shadow);
}

.nav-bar__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--rookie-text);
}

.nav-bar__search input::placeholder {
  color: var(--rookie-text-tertiary);
}

.nav-bar__search-icon,
.nav-bar__icon-button :deep(svg) {
  width: 18px;
  height: 18px;
}

.nav-bar__search-icon {
  color: var(--rookie-text-tertiary);
  flex: none;
}

.nav-bar__shortcut {
  flex: none;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--rookie-card-bg);
  border: 1px solid var(--rookie-border);
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  font-weight: 600;
}

.nav-bar__icon-button {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
  transition:
    color 0.2s ease,
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.nav-bar__icon-button:hover,
.nav-bar__icon-button:focus-visible {
  color: var(--rookie-primary);
  border-color: var(--rookie-primary-border);
  background: var(--rookie-hover-bg);
  outline: none;
}

.nav-bar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-right: 1rem;
}

.nav-bar__profile {
  min-width: 0;
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.nav-bar__profile-avatar {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--rookie-avatar-bg);
  color: var(--rookie-primary-strong);
  font-weight: 700;
  flex: none;
}

.nav-bar__profile-copy {
  display: flex;
  align-items: center;
  text-align: left;
  line-height: 1.15;
}

.nav-bar__profile-copy strong {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.nav-bar__profile-arrow {
  width: 14px;
  height: 14px;
  color: var(--rookie-text-tertiary);
}

.nav-bar__tab-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
}

.nav-bar__tab-button {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.nav-bar__tab-button:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.nav-bar__notice-head {
  padding: 8px 14px 4px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  font-weight: 700;
}

.nav-bar__notice-item {
  min-width: 280px;
}

.nav-bar__notice-empty {
  min-width: 280px;
  padding: 14px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.nav-bar__notice-copy {
  display: grid;
  gap: 4px;
  white-space: normal;
}

.nav-bar__notice-copy strong {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--rookie-text);
}

.nav-bar__notice-copy span,
.nav-bar__notice-copy small {
  color: var(--rookie-text-secondary);
}

.nav-bar__notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--rookie-primary);
}

@media (max-width: 1024px) {
  .nav-bar__top-row,
  .nav-bar__tab-row {
    flex-wrap: wrap;
  }

  .nav-bar__actions {
    width: 100%;
    justify-content: space-between;
  }

  .nav-bar__search {
    width: 100%;
  }
}
</style>
