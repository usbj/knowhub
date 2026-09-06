<!--
  AppHeader —— knowhub 前台顶栏
  ------------------------------------------------------------------
  结构：左侧 logo+品牌 / 中间主导航 / 右侧全局搜索 + 公告下拉 + 用户下拉（含个人中心入口）。
  个人中心不占主导航位，从用户下拉菜单进入。滚动时加阴影与毛玻璃。
  公告下拉用 el-popover，展示系统公告列表，"查看全部公告"跳 /notices。
-->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Search,
  Bell,
  User,
  CaretBottom,
} from '@element-plus/icons-vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'
import { formatDateTime } from '@/utils/format'

/** 主导航项：个人中心不在导航，从用户下拉进入 */
const navItems: { to: string; label: string; exact?: boolean }[] = [
  { to: '/', label: '首页', exact: true },
  { to: '/blogs', label: '笔记导航' },
  { to: '/projects', label: '项目展示' },
  { to: '/articles', label: '文档学习' },
  { to: '/resources', label: '资源推荐' },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()
/** 当前激活导航判定：首页精确匹配，其余前缀匹配 */
const isActive = (to: string, exact?: boolean) =>
  exact ? route.path === to : route.path.startsWith(to) && to !== '/'

/** 滚动监听：超过 8px 加阴影与毛玻璃 */
const scrolled = ref(false)
if (typeof window !== 'undefined') {
  window.addEventListener(
    'scroll',
    () => {
      scrolled.value = window.scrollY > 8
    },
    { passive: true },
  )
}

const headerClass = computed(() => ['kh-header', { 'is-scrolled': scrolled.value }])

/**
 * 通知类型字典 code → 中文标签：与 sql/rookie.sql 的 sys_notice_type 对齐。
 * 后台走 resolveDictLabel('sys_notice_type', ...)；前台不引字典缓存，做内联映射兜底。
 * 未命中时退回原始 code，保证有值可显示。
 */
const noticeTypeMap: Record<string, string> = {
  NOTICE: '公告',
  NOTIFY: '通知',
  REMIND: '提醒',
}
/** 通知类型标签色：公告 warm / 通知 success / 提醒 primary，其余 info */
const noticeTagType: Record<string, 'warm' | 'warning' | 'primary' | 'info' | 'success'> = {
  NOTICE: 'warm',
  NOTIFY: 'success',
  REMIND: 'primary',
}
const resolveNoticeTypeLabel = (code: string) => noticeTypeMap[code] ?? code
const resolveNoticeTagType = (code: string) => noticeTagType[code] ?? 'info'

/**
 * 把后端 SysNoticeRecord 映射成下拉项展示模型。
 * - id：下拉 key + markAsRead 入参，用 noticeId；
 * - unread：取反 hasRead，驱动未读小圆点；
 * - summary：content 前 40 字预览；
 * - time：publishTime 经 formatDateTime 智能截断（只到日则不显示时分秒）。
 */
const noticeItems = computed(() =>
  noticeStore.myNotices.map((n) => ({
    id: Number(n.noticeId),
    title: n.title,
    summary: (n.content ?? '').slice(0, 40),
    time: formatDateTime(n.publishTime),
    unread: !n.hasRead,
    isTop: Number(n.isTop) === 1,
    typeLabel: resolveNoticeTypeLabel(n.noticeType),
    tagType: resolveNoticeTagType(n.noticeType),
  })),
)

/**
 * el-dropdown 实例 ref：Visibleity 不可控，但组件暴露 handleClose()（内部 popperRef.onClose()）可手动收起。
 * 替代死绑定的 v-model:visible（el-dropdown 没有 visible prop，也不传 visible 给 el-tooltip，
 * 故写 noticeVisible=false 对 popper 无效——#3「点公告后不收起」根因）。
 */
const noticeDropdown = ref<{ handleClose?: () => void } | null>(null)

const goNotices = () => {
  // 跳公告列表前同样要收起下拉：直接调实例 handleClose（noticeVisible=false 无效）
  noticeDropdown.value?.handleClose?.()
  router.push('/notices')
}

/**
 * 全部已读：乐观标已加载列表全部已读 + 徽标清零（store 内），后端批量覆盖所有未读（含未加载页）。
 * 收起下拉避免点击后列表原地全部变已读的视觉跳动；失败由 store 下次拉取纠正。
 * 无未读时按钮不渲染（unreadCount===0 隐藏），不会误触发。
 */
const handleMarkAllRead = () => {
  noticeDropdown.value?.handleClose?.()
  void noticeStore.markAllAsRead()
}

/** 顶栏搜索框触发：跳全局搜索结果页 */
const goSearch = () => router.push('/search')

/**
 * 点某条公告：先用暴露的 handleClose() 真正收起下拉（不靠死绑定 noticeVisible），
 * 再等一帧让下拉收起过渡与详情弹窗展开过渡拉开，避免同帧竞争卡顿；
 * 然后标记已读（乐观，未读才请求）+ 开全局详情弹窗（挂在 AppLayout 的 <KhNoticeDetailDialog>，store.openDetail 注入当前 notice）。
 * 失败由 store 内 next fetch 纠正，不阻塞弹窗。
 */
const openNotice = async (noticeId: number) => {
  noticeDropdown.value?.handleClose?.()
  await nextTick()
  const notice = noticeStore.getNoticeById(noticeId)
  if (notice) {
    void noticeStore.markAsRead(noticeId)
    noticeStore.openDetail(notice)
  } else {
    // 兜底：store 已清/未加载，跳公告列表页保证有反馈
    router.push('/notices')
  }
}

/**
 * 通知下拉滚动容器选择器。
 * knowhub 下拉用自定义 .kh-notice-menu__scroll div 做内部滚动（非 EP ElScrollbar），
 * 滚动监听直接挂它，与后台 rookie NavBar 挂 .el-scrollbar__wrap 不同。
 */
const NOTICE_SCROLL_SELECTOR = '.kh-notice-menu__scroll'

/**
 * 通知下拉滚动到接近底部时触发下一页懒加载（距底 8px 内）。
 */
const handleNoticeScroll = (event: Event) => {
  const el = event.currentTarget as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 8) {
    void noticeStore.loadMoreNotices()
  }
}

/**
 * 下拉打开期间补页直到菜单可滚动：首屏 10 条在 480px 内往往放得下，
 * 菜单没有滚动条时滚轮无处可滚、懒加载触发不了，这里自动加载下一页，
 * 直到内容溢出可滚动或没有更多为止（之后交给用户滚动触发）。
 */
const ensureNoticeScrollable = async () => {
  const el = document.querySelector<HTMLElement>(NOTICE_SCROLL_SELECTOR)
  if (!el || !noticeStore.hasMore || noticeStore.loadingMore) {
    return
  }
  if (el.scrollHeight - el.clientHeight <= 2) {
    try {
      await noticeStore.loadMoreNotices()
    } catch {
      // 补页失败时停止自动补页，滚动/重新打开时会重试
      return
    }
    await ensureNoticeScrollable()
  }
}

/** 通知下拉当前是否显示，控制列表变化后的自动补页只在打开期间生效 */
const isNoticeVisible = ref(false)

/**
 * 下拉显示时拉取首屏列表 + 刷新未读数（已加载则 store 内 loaded 去重，只补未读数），
 * 在滚动容器上挂懒加载监听并尝试自动补页；隐藏时卸载监听。
 * el-dropdown @visible-change 在 popper 展开/收起时触发，比 v-model:visible 可靠。
 */
const handleNoticeVisible = (visible: boolean) => {
  isNoticeVisible.value = visible
  if (visible) {
    // 打开下拉 force 重拉首页：保证每次展开都是最新通知（含审核/评论/协作等私发），
    // 规避 loaded 守卫命中旧列表致徽标亮但下拉空（bug 1）；同时刷未读数保证徽标准确。
    void noticeStore.fetchMyNotices(true)
    void noticeStore.fetchUnreadCount()
    // popper teleport 到 body 后再找滚动容器，避免取到未渲染节点
    nextTick(() => {
      document.querySelector<HTMLElement>(NOTICE_SCROLL_SELECTOR)?.addEventListener('scroll', handleNoticeScroll)
      void ensureNoticeScrollable()
    })
  } else {
    document.querySelector<HTMLElement>(NOTICE_SCROLL_SELECTOR)?.removeEventListener('scroll', handleNoticeScroll)
  }
}

// 首屏数据返回 / 自动补页追加后重新检查：下拉仍打开且内容仍不满一屏则继续补页
watch(
  [() => noticeStore.myNotices.length, () => noticeStore.loadingMore],
  () => {
    if (isNoticeVisible.value) {
      nextTick(() => void ensureNoticeScrollable())
    }
  },
)

/**
 * 轮询未读数：登录态下每 60s 刷未读计数。检测到徽标增长（有新通知到达）且下拉未打开时，
 * 主动 force 重拉 myNotices 首页——否则下拉用 store loaded 守卫命中的旧列表不含新通知，
 * 徽标亮但下拉空（bug 1 根因）。下拉正打开时不重置分页（用户可能正滚到第 3 页），打开时本就会非 force 拉一次。
 * 未登录不启动轮询；用户 store 登录态变化时自动挂/卸。
 */
let noticePollTimer: ReturnType<typeof setInterval> | undefined
const startNoticePolling = () => {
  if (noticePollTimer) return
  noticePollTimer = setInterval(async () => {
    const increased = await noticeStore.fetchUnreadCount()
    if (increased && !isNoticeVisible.value) {
      void noticeStore.fetchMyNotices(true)
    }
  }, 60_000)
}
const stopNoticePolling = () => {
  if (noticePollTimer) {
    clearInterval(noticePollTimer)
    noticePollTimer = undefined
  }
}
watch(
  () => userStore.isAuthenticated,
  (authed) => {
    if (authed) startNoticePolling()
    else stopNoticePolling()
  },
  { immediate: true },
)
onBeforeUnmount(stopNoticePolling)

/**
 * 退出登录：前台无后端登出接口，纯前端清态后整页跳 /login。
 * 用整页跳转而非路由 push，确保所有页面残留的用户态被彻底复位。
 */
const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <header :class="headerClass">
    <div class="kh-header__inner kh-container kh-container--wide">
      <!-- 品牌 -->
      <RouterLink to="/" class="kh-brand" aria-label="知枢 knowhub 首页">
        <span class="kh-brand__mark">
          <img src="@/assets/image/logo.png" alt="知枢 logo" width="36" height="36" />
        </span>
        <span class="kh-brand__text">
          <span class="kh-brand__name">知枢</span>
          <span class="kh-brand__sub">knowhub</span>
        </span>
      </RouterLink>

      <!-- 主导航 -->
      <nav class="kh-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="kh-nav__item"
          :class="{ 'is-active': isActive(item.to, item.exact) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <!-- 右侧操作 -->
      <div class="kh-actions">
        <button class="kh-search-trigger" type="button" aria-label="搜索" @click="goSearch">
          <el-icon><Search /></el-icon>
          <span class="kh-search-trigger__hint">搜索内容…</span>
          <kbd>⌘K</kbd>
        </button>

        <!-- 公告下拉：已登录显示铃铛+未读徽标+下拉列表（参考后台 NavBar 通知下拉形态）。
             el-dropdown 无 visible 双向，v-model:visible 是死绑定（不传 visible 给 el-tooltip）；
             收起只能走 noticeDropdown 暴露的 handleClose()（见 openNotice/goNotices）。
             @visible-change 在 popper 展开/收起时触发，挂滚动懒加载 + 首屏自动补页；@command 收命令式事件。 -->
        <el-dropdown
          v-if="userStore.isAuthenticated"
          ref="noticeDropdown"
          trigger="click"
          placement="bottom-end"
          popper-class="kh-notice-dropdown"
          :hide-on-click="false"
          @command="openNotice"
          @visible-change="handleNoticeVisible"
        >
          <button class="kh-icon-btn kh-icon-btn--badge" type="button" aria-label="系统公告">
            <el-icon><Bell /></el-icon>
            <span v-if="noticeStore.unreadCount > 0" class="kh-badge-dot">{{ noticeStore.unreadCount }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu class="kh-notice-menu">
              <div class="kh-notice-menu__head">
                <span>系统公告</span>
                <div class="kh-notice-menu__head-actions">
                  <button
                    v-if="noticeStore.unreadCount > 0"
                    class="kh-notice-menu__read-all"
                    type="button"
                    @click="handleMarkAllRead"
                  >全部已读</button>
                  <button class="kh-notice-menu__all" type="button" @click="goNotices">
                    查看全部 <el-icon><CaretBottom /></el-icon>
                  </button>
                </div>
              </div>
              <!-- 下拉栏高度封顶 + 列表内滚动：通知条数多时不无限往下扩，避免视觉过长 -->
              <div class="kh-notice-menu__scroll">
                <div v-if="noticeItems.length === 0" class="kh-notice-menu__empty">暂无公告</div>
                <el-dropdown-item
                  v-for="item in noticeItems"
                  :key="item.id"
                  class="kh-notice-menu__item"
                  :command="item.id"
                >
                  <div class="kh-notice-menu__copy">
                    <div class="kh-notice-menu__top-row">
                      <KhTag size="sm" :type="item.tagType">{{ item.typeLabel }}</KhTag>
                      <span v-if="item.isTop" class="kh-notice-menu__pin">置顶</span>
                      <span v-if="item.unread" class="kh-notice-menu__dot"></span>
                    </div>
                    <div class="kh-notice-menu__title kh-line-clamp-1">{{ item.title }}</div>
                    <div class="kh-notice-menu__summary kh-line-clamp-1">{{ item.summary }}</div>
                    <small class="kh-notice-menu__time">{{ item.time }}</small>
                  </div>
                </el-dropdown-item>
              </div>
              <!-- 底部状态行：hasMore 提示继续滚动加载更多、loadingMore 显加载中、!hasMore 显已加载全部 -->
              <div class="kh-notice-menu__status">
                <span v-if="noticeStore.loadingMore">加载中…</span>
                <span v-else-if="noticeStore.hasMore">继续滚动加载更多</span>
                <span v-else-if="noticeItems.length > 0">已加载全部</span>
              </div>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- el-dropdown @command：用 command 模式替代 item 直接 @click（el-dropdown-item 的 click 派发不稳定，
             且 hide-on-click=false 时 popper 不会自动收起）。统一走 openNotice，其内 noticeDropdown.handleClose() 真正收起下拉 -->

        <!-- 用户入口：未登录显示登录/注册，已登录显示用户下拉 -->
        <template v-if="userStore.isAuthenticated">
          <el-dropdown trigger="click" placement="bottom-end">
            <button class="kh-user" type="button">
              <KhAvatar :item="{ label: userStore.displayName || '客', src: userStore.avatarUrl ?? undefined }" :size="28" />
              <span class="kh-user__name">{{ userStore.displayName }}</span>
              <el-icon class="kh-user__caret"><CaretBottom /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="User" @click="$router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="$router.push('/blog/create')">
                  <KhIcon name="blog" :size="14" style="margin-right: 6px" /> 写博客
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/article/create')">
                  <KhIcon name="doc" :size="14" style="margin-right: 6px" /> 写文章
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/project/create')">
                  <KhIcon name="project" :size="14" style="margin-right: 6px" /> 创建项目
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/resource/upload')">
                  <KhIcon name="resource" :size="14" style="margin-right: 6px" /> 上传资源
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <div v-else class="kh-auth-actions">
          <RouterLink to="/register" class="kh-auth-link kh-auth-link--ghost">注册</RouterLink>
          <RouterLink to="/login" class="kh-auth-link kh-auth-link--primary">登录</RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.kh-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--kh-header-height);
  background: color-mix(in srgb, var(--kh-surface) 80%, transparent);
  backdrop-filter: saturate(1.4) blur(12px);
  border-bottom: 1px solid transparent;
  transition:
    box-shadow var(--kh-transition),
    border-color var(--kh-transition),
    background var(--kh-transition);
}
.kh-header.is-scrolled {
  box-shadow: var(--kh-shadow-sm);
  border-bottom-color: var(--kh-border-soft);
}
.kh-header__inner {
  height: 100%;
  display: flex;
  align-items: center;
  gap: var(--kh-space-6);
}

/* —— 品牌 —— */
.kh-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--kh-text);
  flex: none;
}
.kh-brand__mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
}
.kh-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.05;
}
.kh-brand__name {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: var(--kh-font-size-lg);
  color: var(--kh-text);
}
.kh-brand__sub {
  font-family: var(--kh-font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--kh-text-tertiary);
  text-transform: uppercase;
}

/* —— 主导航 —— */
.kh-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 0 auto;
}
.kh-nav__item {
  position: relative;
  padding: 8px 14px;
  font-size: var(--kh-font-size-md);
  color: var(--kh-text-secondary);
  border-radius: var(--kh-radius);
  font-weight: 500;
  cursor: pointer;
  transition:
    color var(--kh-transition-fast),
    background var(--kh-transition-fast);
}
.kh-nav__item:hover {
  color: var(--kh-text);
  background: var(--kh-surface-hover);
}
.kh-nav__item.is-active {
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.kh-nav__item.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -2px;
  width: 18px;
  height: 3px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary);
  transform: translateX(-50%);
}

/* —— 右侧操作 —— */
.kh-actions {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex: none;
}
.kh-search-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px 0 12px;
  width: 220px;
  background: var(--kh-surface-muted);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition:
    border-color var(--kh-transition-fast),
    background var(--kh-transition-fast);
}
.kh-search-trigger:hover {
  border-color: var(--kh-border-strong);
  background: var(--kh-surface);
}
.kh-search-trigger__hint {
  flex: 1;
  text-align: left;
}
.kh-search-trigger kbd {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  color: var(--kh-text-tertiary);
}
.kh-icon-btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--kh-radius);
  border: 1px solid transparent;
  background: transparent;
  color: var(--kh-text-secondary);
  cursor: pointer;
  transition:
    background var(--kh-transition-fast),
    color var(--kh-transition-fast);
}
.kh-icon-btn:hover {
  background: var(--kh-surface-hover);
  color: var(--kh-text);
}
.kh-badge-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-weight: 600;
}
.kh-user {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 8px 0 6px;
  border-radius: var(--kh-radius-pill);
  border: 1px solid var(--kh-border-soft);
  background: var(--kh-surface);
  cursor: pointer;
  transition:
    border-color var(--kh-transition-fast),
    box-shadow var(--kh-transition-fast);
}
.kh-user:hover {
  border-color: var(--kh-border-strong);
  box-shadow: var(--kh-shadow-xs);
}
.kh-user__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--kh-primary), var(--kh-accent));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: grid;
  place-items: center;
}
.kh-user__name {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  font-weight: 500;
}
.kh-user__caret {
  color: var(--kh-text-tertiary);
  font-size: 12px;
}

/* —— 未登录态：登录/注册入口 —— */
.kh-auth-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kh-auth-link {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 16px;
  border-radius: var(--kh-radius-pill);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    background var(--kh-transition-fast),
    border-color var(--kh-transition-fast),
    color var(--kh-transition-fast);
}
.kh-auth-link--ghost {
  border: 1px solid var(--kh-border);
  background: transparent;
  color: var(--kh-text-secondary);
}
.kh-auth-link--ghost:hover {
  border-color: var(--kh-border-strong);
  color: var(--kh-text);
  background: var(--kh-surface-hover);
}
.kh-auth-link--primary {
  border: 1px solid var(--kh-primary-border);
  background: var(--kh-primary);
  color: #fff;
}
.kh-auth-link--primary:hover {
  background: var(--kh-primary-strong);
}

@media (max-width: 900px) {
  .kh-search-trigger {
    width: auto;
  }
  .kh-search-trigger__hint,
  .kh-search-trigger kbd {
    display: none;
  }
}
</style>

<!-- 公告 dropdown 内容被 teleport 到 body，scoped 不生效，用全局块 -->
<style>
/* 下拉容器：参考后台 NavBar 的 nav-bar-notice-dropdown 覆写思路 */
.kh-notice-dropdown {
  padding: 0 !important;
  border-radius: var(--kh-radius-lg) !important;
  border: 1px solid var(--kh-border-soft) !important;
  box-shadow: var(--kh-shadow) !important;
  overflow: hidden;
  min-width: 340px;
  max-width: 380px;
}
/* el-dropdown-menu 默认留白与背景不贴合前台主题，统一重置 */
.kh-notice-dropdown .kh-notice-menu.el-dropdown-menu {
  padding: 0;
  background: var(--kh-surface);
  border: none;
}
/* 头部固定区：标题 + 查看全部 */
.kh-notice-menu__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kh-border-soft);
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
  background: var(--kh-surface);
}
/* 头部右侧操作组：全部已读 + 查看全部 */
.kh-notice-menu__head-actions {
  display: inline-flex;
  align-items: center;
  gap: 14px;
}
.kh-notice-menu__read-all {
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  transition: color var(--kh-transition-fast);
}
.kh-notice-menu__read-all:hover {
  color: var(--kh-primary);
}
.kh-notice-menu__all {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
}
.kh-notice-menu__all:hover {
  text-decoration: underline;
}
.kh-notice-menu__empty {
  padding: 28px 16px;
  text-align: center;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}
/* 底部状态行：懒加载提示/加载中/已全部，固定在列表下方，非 sticky（列表内滚时此行随滚走，符合"提示在尾部"语义） */
.kh-notice-menu__status {
  padding: 10px 16px;
  text-align: center;
  border-top: 1px solid var(--kh-border-soft);
  color: var(--kh-text-tertiary);
  font-size: 12px;
  background: var(--kh-surface);
}
/* 列表区高度封顶 + 内部滚动：通知多时不无限往下扩，头部固定不滚走。
   480px ≈ 6~7 项可见，超出在区内 scroll，下拉整体长度收敛（与方案"需要最大长度而非无限阔"对齐） */
.kh-notice-menu__scroll {
  max-height: 480px;
  overflow-y: auto;
  scrollbar-width: thin;
}
.kh-notice-menu__scroll::-webkit-scrollbar {
  width: 6px;
}
.kh-notice-menu__scroll::-webkit-scrollbar-thumb {
  background: var(--kh-border);
  border-radius: 3px;
}
.kh-notice-menu__scroll::-webkit-scrollbar-thumb:hover {
  background: var(--kh-border-strong);
}
/* 列表项：el-dropdown-item 默认自带 padding/border-radius/white 背景，
   前台需要贴边、圆角内嵌、hover 用主题色 */
.kh-notice-dropdown .kh-notice-menu__item.el-dropdown-menu__item {
  padding: 10px 12px;
  margin: 0 6px;
  border-radius: var(--kh-radius-sm);
  background: transparent;
  color: var(--kh-text);
  line-height: 1.5;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.kh-notice-dropdown .kh-notice-menu__item.el-dropdown-menu__item:not(.is-disabled):hover,
.kh-notice-dropdown .kh-notice-menu__item.el-dropdown-menu__item:focus {
  background: var(--kh-surface-muted);
  color: var(--kh-text);
}
.kh-notice-menu__item:first-of-type {
  margin-top: 4px;
}
.kh-notice-menu__item:last-of-type {
  margin-bottom: 6px;
}
.kh-notice-menu__copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.kh-notice-menu__top-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kh-notice-menu__title {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.kh-notice-menu__summary {
  font-size: 12px;
  color: var(--kh-text-secondary);
  line-height: 1.5;
}
.kh-notice-menu__time {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}
.kh-notice-menu__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--kh-primary);
  flex: none;
}
.kh-notice-menu__pin {
  font-size: 10px;
  font-weight: 600;
  color: var(--kh-warm);
  padding: 1px 6px;
  border-radius: var(--kh-radius-pill);
  background: color-mix(in srgb, var(--kh-warm) 14%, transparent);
  flex: none;
}
</style>