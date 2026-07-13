import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'
import { useDictStore } from '@/stores/dict'

/**
 * knowhub 前台路由（静态 + 最简鉴权守卫）
 * ------------------------------------------------------------------
 * meta.bare：true 表示该页自带全屏布局（登录/注册），App.vue 跳过 AppLayout 壳体。
 * meta.requiresAuth：true 表示该页需登录，守卫无 token 时跳 /login 并带 redirect。
 * 前台是公开展示站：首页/博客/项目/资源/文档/笔记导航等均不拦截，仅个人中心等需登录。
 * 详情页用 :id，从 mock 按 id 取数据（后续接接口再改为动态拉取）。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/home/index.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/notes',
      name: 'notes',
      component: () => import('@/views/notes/index.vue'),
      meta: { title: '笔记导航' },
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/projects/index.vue'),
      meta: { title: '项目展示' },
    },
    {
      path: '/docs',
      name: 'docs',
      component: () => import('@/views/docs/index.vue'),
      meta: { title: '文档学习' },
    },
    {
      path: '/resources',
      name: 'resources',
      component: () => import('@/views/resources/index.vue'),
      meta: { title: '资源推荐' },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/profile/index.vue'),
      meta: { title: '个人中心', requiresAuth: true },
    },
    {
      path: '/notices',
      name: 'notices',
      component: () => import('@/views/notices/index.vue'),
      meta: { title: '系统公告' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { title: '登录', bare: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { title: '注册', bare: true },
    },
    {
      path: '/blog/:id',
      name: 'blog-detail',
      component: () => import('@/views/blog/detail.vue'),
      meta: { title: '博客详情' },
    },
    {
      path: '/project/:id',
      name: 'project-detail',
      component: () => import('@/views/project/detail.vue'),
      meta: { title: '项目详情' },
    },
  ],
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 }
  },
})

/**
 * 前置守卫：最简鉴权。
 * - 已登录再进 /login 或 /register：直接回首页，避免重复登录。
 * - 需登录页无 token：跳 /login 并带 redirect 回填来源。
 * - 需登录页有 token 但 userInfo 未恢复（刷新页面后）：惰性拉一次 /person。
 * 公开页一律放行，不触发任何鉴权副作用。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const noticeStore = useNoticeStore()
  const dictStore = useDictStore()

  if (userStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
    return '/'
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  // 已登录但刷新页面导致 store userInfo 丢失时，补拉一次个人资料兜底；
  // 同步惰性拉取公告与字典，让顶栏铃铛与字典展示在恢复登录态后即就绪（失败不阻塞）
  if (to.meta.requiresAuth && userStore.isAuthenticated) {
    try {
      if (!userStore.userInfo) {
        await userStore.fetchUserProfile()
      }
      noticeStore.fetchMyNotices().catch(() => undefined)
      dictStore.initializeDictionaries().catch(() => undefined)
    } catch {
      // /person 拉取失败（token 失效等）时 http 工具已处理跳登录，这里不再额外处置
    }
  }

  return true
})

export default router