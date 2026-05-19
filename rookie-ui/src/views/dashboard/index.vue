<script setup lang="ts">
import { computed } from 'vue'
import { DataLine, Files, Grid, Lightning, User } from '@element-plus/icons-vue'
import BaseCard from '@/components/BaseCard.vue'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useUserStore } from '@/stores/user'
import type { NavigationMenuItem } from '@/types/components/navigation'

const userStore = useUserStore()
const layoutNavigationStore = useLayoutNavigationStore()

const flattenMenus = (menus: NavigationMenuItem[]): NavigationMenuItem[] =>
  menus.flatMap((menu) => [menu, ...flattenMenus(menu.children ?? [])])

const flatMenus = computed(() => flattenMenus(layoutNavigationStore.menuTree))

const pageCount = computed(() => flatMenus.value.filter((item) => item.menuType === 2).length)
const directoryCount = computed(() => flatMenus.value.filter((item) => item.menuType === 1).length)
const permissionCount = computed(() => layoutNavigationStore.buttonPermissionKeys.size)
const roleCount = computed(() => userStore.profileSummary?.roleNames.length ?? 0)

const quickEntries = computed(() => {
  const entries = [
    {
      title: '个人中心',
      description: '维护昵称、手机号和登录密码',
      route: '/account/profile',
    },
    ...flatMenus.value
      .filter((item) => item.menuType === 2 && item.route)
      .slice(0, 5)
      .map((item) => ({
        title: item.menuName,
        description: item.permKey || '可访问业务页面',
        route: item.route,
      })),
  ]

  return entries
})

const topModules = computed(() =>
  layoutNavigationStore.menuTree.slice(0, 6).map((item) => ({
    menuId: item.menuId,
    title: item.menuName,
    description:
      item.children.length > 0
        ? `包含 ${item.children.filter((child) => child.menuType === 2).length} 个页面入口`
        : item.permKey || '可直接进入的系统页面',
    route: item.menuType === 2 ? item.route : item.children.find((child) => child.menuType === 2)?.route || '',
  })),
)

const summaryCards = computed(() => [
  {
    title: '可访问页面',
    value: pageCount.value,
    caption: '当前账号已加载的菜单页面',
    icon: Grid,
  },
  {
    title: '目录分组',
    value: directoryCount.value,
    caption: '侧边导航中的目录数量',
    icon: Files,
  },
  {
    title: '按钮权限',
    value: permissionCount.value,
    caption: '当前登录角色拥有的权限点',
    icon: Lightning,
  },
  {
    title: '账号角色',
    value: roleCount.value,
    caption: '当前账号挂载的角色数量',
    icon: User,
  },
])
</script>

<template>
  <section class="dashboard-view">
    <section class="dashboard-view__hero">
      <div class="dashboard-view__hero-copy">
        <span class="dashboard-view__eyebrow">系统首页</span>
        <h1>{{ userStore.displayName }}</h1>
        <p>
          当前账号可直接进入已授权模块，常用入口和权限概览都放在这里，适合做后台落地页。
        </p>
      </div>

      <div class="dashboard-view__hero-side">
        <div class="dashboard-view__identity">
          <span>登录账号</span>
          <strong>{{ userStore.profileSummary?.username || '--' }}</strong>
        </div>
        <div class="dashboard-view__identity">
          <span>角色</span>
          <strong>{{ userStore.profileSummary?.roleNames.join('、') || '未分配角色' }}</strong>
        </div>
      </div>
    </section>

    <section class="dashboard-view__metrics">
      <article v-for="item in summaryCards" :key="item.title" class="dashboard-view__metric">
        <div class="dashboard-view__metric-head">
          <span>{{ item.title }}</span>
          <component :is="item.icon" />
        </div>
        <strong>{{ item.value }}</strong>
        <p>{{ item.caption }}</p>
      </article>
    </section>

    <section class="dashboard-view__grid">
      <BaseCard title="快捷入口" description="把高频页面收拢在固定首页，减少来回展开菜单。">
        <div class="dashboard-view__quick-list">
          <RouterLink
            v-for="item in quickEntries"
            :key="item.route"
            :to="item.route"
            class="dashboard-view__quick-item"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </RouterLink>
        </div>
      </BaseCard>

      <BaseCard title="模块概览" description="当前账号菜单树中可见的一级模块。">
        <div class="dashboard-view__module-list">
          <RouterLink
            v-for="item in topModules"
            :key="item.menuId"
            :to="item.route || '/'"
            class="dashboard-view__module-item"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </RouterLink>
        </div>
      </BaseCard>
    </section>

    <BaseCard title="使用提示" description="这些不是产品说明，而是当前系统里最实用的日常动作。">
      <div class="dashboard-view__tips">
        <div class="dashboard-view__tip">
          <DataLine />
          <div>
            <strong>先从首页进常用页</strong>
            <span>顶部标签会记住已打开页面，适合在用户、角色、菜单之间来回切换。</span>
          </div>
        </div>
        <div class="dashboard-view__tip">
          <User />
          <div>
            <strong>个人资料单独维护</strong>
            <span>昵称、手机号和密码统一在个人中心处理，不混进权限菜单。</span>
          </div>
        </div>
      </div>
    </BaseCard>
  </section>
</template>

<style scoped>
.dashboard-view {
  display: grid;
  gap: 18px;
}

.dashboard-view__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.7fr);
  gap: 18px;
  padding: 24px 26px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.dashboard-view__hero-copy {
  display: grid;
  gap: 10px;
}

.dashboard-view__eyebrow {
  color: var(--rookie-primary);
  font-size: var(--rookie-font-size-sm);
  font-weight: 700;
}

.dashboard-view__hero-copy h1 {
  margin: 0;
  color: var(--rookie-text);
  font-size: 28px;
}

.dashboard-view__hero-copy p {
  margin: 0;
  color: var(--rookie-text-secondary);
  max-width: 680px;
}

.dashboard-view__hero-side {
  display: grid;
  gap: 12px;
  align-content: start;
}

.dashboard-view__identity {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.dashboard-view__identity span,
.dashboard-view__metric p,
.dashboard-view__quick-item span,
.dashboard-view__module-item span,
.dashboard-view__tip span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.dashboard-view__identity strong,
.dashboard-view__quick-item strong,
.dashboard-view__module-item strong,
.dashboard-view__tip strong {
  color: var(--rookie-text);
}

.dashboard-view__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-view__metric {
  display: grid;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.dashboard-view__metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.dashboard-view__metric-head :deep(svg),
.dashboard-view__tip :deep(svg) {
  width: 18px;
  height: 18px;
  color: var(--rookie-primary);
}

.dashboard-view__metric strong {
  color: var(--rookie-text);
  font-size: 28px;
  line-height: 1;
}

.dashboard-view__metric p {
  margin: 0;
}

.dashboard-view__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.dashboard-view__quick-list,
.dashboard-view__module-list {
  display: grid;
  gap: 12px;
}

.dashboard-view__quick-item,
.dashboard-view__module-item {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.dashboard-view__quick-item:hover,
.dashboard-view__module-item:hover {
  border-color: var(--rookie-primary-border);
  background: var(--rookie-hover-bg);
}

.dashboard-view__tips {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-view__tip {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.dashboard-view__tip div {
  display: grid;
  gap: 6px;
}

@media (max-width: 1024px) {
  .dashboard-view__hero,
  .dashboard-view__grid,
  .dashboard-view__tips,
  .dashboard-view__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
