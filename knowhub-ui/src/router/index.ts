import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'
import { useDictStore } from '@/stores/dict'
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/common'
import type { SysUserProfile } from '@/types/api/user'

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
      path: '/blog/create',
      name: 'blog-create',
      component: () => import('@/views/blog/create.vue'),
      meta: { title: '写博客', requiresAuth: true },
    },
    {
      path: '/article/create',
      name: 'article-create',
      component: () => import('@/views/article/create.vue'),
      meta: { title: '写文章', requiresAuth: true },
    },
    {
      path: '/article/:id/chapters',
      name: 'article-chapters',
      component: () => import('@/views/article/chapters.vue'),
      meta: { title: '章节管理', requiresAuth: true },
    },
    {
      path: '/article/:id/chapter/edit',
      name: 'article-chapter-edit',
      component: () => import('@/views/article/chapter-edit.vue'),
      meta: { title: '写章节', requiresAuth: true },
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
    {
      path: '/docs/:id',
      name: 'doc-detail',
      component: () => import('@/views/doc/detail.vue'),
      meta: { title: '文档详情' },
    },
    {
      path: '/docs/:id/read/:chapterId',
      name: 'doc-read',
      component: () => import('@/views/doc/read.vue'),
      meta: { title: '文档阅读' },
    },
    {
      path: '/resource/:id',
      name: 'resource-detail',
      component: () => import('@/views/resource/detail.vue'),
      meta: { title: '资源详情' },
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/history/index.vue'),
      meta: { title: '浏览历史', requiresAuth: true },
    },
  ],
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 }
  },
})

/**
 * 前置守卫：最简鉴权 + 进站登录态探活。
 * - 已登录再进 /login 或 /register：直接回首页，避免重复登录。
 * - 需登录页无 token：跳 /login 并带 redirect 回填来源。
 * - 进站探活：本次 SPA 会话首次进入时，若本地存有 token，主动调 /person 验证是否过期；
 *   过期则 logout() 全清登录态（含 token/userInfo/公告/字典/系统配置缓存），
 *   不强制跳登录——让用户继续停在当前公开页，顶栏自然变游客。
 *   探活用 skipAuthRedirect:true，避开 http.ts 的 401 整页甩登录逻辑，由守卫自己清。
 * sessionChecked 模块级闸：一次 SPA 会话只探一次（刷新=新会话会再探，符合"每次进网站校验"）。
 */
let sessionChecked = false

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const noticeStore = useNoticeStore()
  const dictStore = useDictStore()

  // 进站一次性探活：本地有 token 才验，无 token 是纯游客，跳过。
  // 避开 /login、/register 自身——这俩页不应触发探活（探活成功会被下面"已登录进登录页"回首页）。
  if (!sessionChecked && userStore.isAuthenticated && to.path !== '/login' && to.path !== '/register') {
    sessionChecked = true
    try {
      const result = await get<ApiResult<SysUserProfile>>('/person', { skipAuthRedirect: true })
      if (result.data) {
        userStore.setUserProfile(result.data)
      }
      // 探活成功：惰性拉公告与字典，让顶栏铃铛与字典在登录态恢复后即就绪（失败不阻塞）
      noticeStore.fetchMyNotices().catch(() => undefined)
      dictStore.initializeDictionaries().catch(() => undefined)
    } catch {
      // /person 401 或网络错 → token 已失效，全清登录态（含各缓存 store reset），
      // 不跳登录：用户继续停当前公开页，顶栏变游客；若目标是 requiresAuth 页，
      // 下面 requiresAuth 分支会自然把他送去 /login。
      userStore.logout()
    }
  }

  if (userStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
    return '/'
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  return true
})

export default router