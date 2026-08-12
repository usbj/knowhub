<!--
  个人中心 /profile
  ------------------------------------------------------------------
  从右上角用户下拉菜单进入（不在主导航）。
  两栏布局：左 sticky 用户卡（头像+昵称+简介+创作入口+统计）/ 右 Tab 内容管理列表。
  优化布局密度与层次，不新增功能。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Plus,
  Edit,
  RefreshLeft,
  Delete,
  Collection,
  Bell,
  ChatDotRound,
} from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'
import { formatDateTime } from '@/utils/format'
import { getMyBlogsApi, collectBlogApi, listMyCollectedBlogsApi } from '@/api/knowhub/authoring'
import { getMyArticlesApi } from '@/api/knowhub/article-authoring'
import { collectArticleApi, listMyCollectedArticlesApi } from '@/api/knowhub/article'
import { getMyProjectsApi, toggleProjectCollectApi, listMyCollectedProjectsApi } from '@/api/knowhub/project-authoring'
import { getMyResourcesApi, toggleResourceCollectApi, listMyCollectedResourcesApi } from '@/api/knowhub/resource-authoring'

/** 收藏四段行内统一形态（map 自各 portal record，仅保留个人中心列表所需字段） */
type CollectRow = { id: number; title: string; author: string; time: string }
type BlogCollectRow = CollectRow
type ArticleCollectRow = CollectRow
type ProjectCollectRow = CollectRow
type ResourceCollectRow = CollectRow

const userStore = useUserStore()
const noticeStore = useNoticeStore()
const route = useRoute()

type TabKey = 'blog' | 'article' | 'project' | 'resource' | 'collect' | 'message'
const activeTab = ref<TabKey>('blog')
// 允许通过路由 query 切 tab（创作页存草稿后跳回 ?tab=blog&t=<ts>）
const ROUTE_TABS: TabKey[] = ['blog', 'article', 'project', 'resource', 'collect', 'message']

/** 分页器统一每页候选（收藏/创作列表共用）；后端 collect/list 实际不分页，分页器只在 > pageSize 时出现 */
const PAGE_SIZES = [10, 20, 50]

/**
 * 当前登录用户展示信息。
 * 姓名 / 账号 / 角色优先取 /person 真实资料，无登录态（守卫已拦 requiresAuth 但兜底）回退占位文案。
 * bio 当前无个人简介字段，统一占位文案。
 */
const displayName = computed(
  () => userStore.userInfo?.nickName || userStore.userInfo?.username || '游客',
)
const displayUsername = computed(() => userStore.userInfo?.username || '')
const displayRole = computed(() => {
  const roles = userStore.userInfo?.userRole ?? []
  return roles.length > 0 ? roles[0]!.roleName : '访客'
})
/** 个人简介：当前 /person 无 bio 字段，统一占位文案，避免空白突兀 */
const displayBio = computed(() => '还没有个人简介')

// ============================ 四个"我的"列表（每页 10 / [10,20,50] 真实分页） ============================

/** 当前用户的博客（真实接口拉取，/authoring/blog/list 薄封装 quarryBlog 走作者分支） */
const myBlogs = ref<{ id: number; title: string; status: string; views: number; likes: number; updateTime: string }[]>([])
const myBlogsLoading = ref(false)
const blogPageNum = ref(1)
const blogPageSize = ref(10)
const blogTotal = ref(0)
const fetchMyBlogs = async () => {
  myBlogsLoading.value = true
  try {
    const res = await getMyBlogsApi({ pageNum: blogPageNum.value, pageSize: blogPageSize.value })
    myBlogs.value = (res.records ?? []).map((b) => ({
      id: b.blogId,
      title: b.title,
      status: b.status ?? 'DRAFT',
      views: b.viewCount ?? 0,
      likes: b.likeCount ?? 0,
      updateTime: b.updateTime
        ? (formatDateTime(b.updateTime) as string)
        : b.publishTime
          ? (formatDateTime(b.publishTime) as string)
          : '',
    }))
    blogTotal.value = res.total ?? 0
  } catch {
    myBlogs.value = []
    blogTotal.value = 0
  } finally {
    myBlogsLoading.value = false
  }
}
const onBlogPageChange = (p: number, sz: number) => {
  blogPageNum.value = p
  blogPageSize.value = sz
  void fetchMyBlogs()
}

/** 当前用户的文章（真实接口拉取，/authoring/article/list 薄封装 quarryArticle 走作者分支），文章=章节集合 */
const myArticles = ref<{
  id: number
  title: string
  status: string
  visibility: string
  chapters: number | string
  views: number
  updateTime: string
  coverObjectKey?: string
}[]>([])
const myArticlesLoading = ref(false)
const articlePageNum = ref(1)
const articlePageSize = ref(10)
const articleTotal = ref(0)
const fetchMyArticles = async () => {
  myArticlesLoading.value = true
  try {
    const res = await getMyArticlesApi({ pageNum: articlePageNum.value, pageSize: articlePageSize.value })
    myArticles.value = (res.records ?? []).map((a) => ({
      id: a.articleId,
      title: a.title,
      status: a.status ?? 'DRAFT',
      visibility: a.visibility ?? 'PRIVATE',
      chapters: '—',
      views: a.viewCount ?? 0,
      updateTime: a.updateTime
        ? (formatDateTime(a.updateTime) as string)
        : a.publishTime
          ? (formatDateTime(a.publishTime) as string)
          : '',
      coverObjectKey: a.coverObjectKey,
    }))
    articleTotal.value = res.total ?? 0
  } catch {
    myArticles.value = []
    articleTotal.value = 0
  } finally {
    myArticlesLoading.value = false
  }
}
const onArticlePageChange = (p: number, sz: number) => {
  articlePageNum.value = p
  articlePageSize.value = sz
  void fetchMyArticles()
}

const myProjects = ref<{
  id: number
  title: string
  type: string
  status: string
  level: number
  updateTime: string
}[]>([])
const myProjectsLoading = ref(false)
const projectPageNum = ref(1)
const projectPageSize = ref(10)
const projectTotal = ref(0)
const fetchMyProjects = async () => {
  myProjectsLoading.value = true
  try {
    const res = await getMyProjectsApi({ pageNum: projectPageNum.value, pageSize: projectPageSize.value })
    myProjects.value = (res.records ?? []).map((p) => ({
      id: p.projectId,
      title: p.title,
      type: p.type,
      status: p.status ?? 'DRAFT',
      level: p.level,
      updateTime: p.updateTime
        ? (formatDateTime(p.updateTime) as string)
        : p.publishTime
          ? (formatDateTime(p.publishTime) as string)
          : '',
    }))
    projectTotal.value = res.total ?? 0
  } catch {
    myProjects.value = []
    projectTotal.value = 0
  } finally {
    myProjectsLoading.value = false
  }
}
const onProjectPageChange = (p: number, sz: number) => {
  projectPageNum.value = p
  projectPageSize.value = sz
  void fetchMyProjects()
}

const myResources = ref<{
  id: number
  title: string
  resourceType: string
  categoryName: string
  status: string
  downloadCount: number
  updateTime: string
}[]>([])
const myResourcesLoading = ref(false)
const resourcePageNum = ref(1)
const resourcePageSize = ref(10)
const resourceTotal = ref(0)
const fetchMyResources = async () => {
  myResourcesLoading.value = true
  try {
    const res = await getMyResourcesApi({ pageNum: resourcePageNum.value, pageSize: resourcePageSize.value })
    myResources.value = (res.records ?? []).map((r) => ({
      id: r.resourceId,
      title: r.title,
      resourceType: r.resourceType ?? 'FILE',
      categoryName: r.categoryName ?? '其他',
      status: r.status ?? 'DRAFT',
      downloadCount: r.downloadCount ?? 0,
      updateTime: r.updateTime
        ? (formatDateTime(r.updateTime) as string)
        : r.publishTime
          ? (formatDateTime(r.publishTime) as string)
          : '',
    }))
    resourceTotal.value = res.total ?? 0
  } catch {
    myResources.value = []
    resourceTotal.value = 0
  } finally {
    myResourcesLoading.value = false
  }
}
const onResourcePageChange = (p: number, sz: number) => {
  resourcePageNum.value = p
  resourcePageSize.value = sz
  void fetchMyResources()
}

// ============================ 收藏 tab：四段独立分页列表 ============================
// 四个内容域各有独立后端 collect/list 接口，无法合并翻页——同 tab 内分四段展示，
// 各带自己的 KhPagination；合计总数供 collect tab 的 tabs 计数与统计卡"收藏"用。
// 后端 listMyCollected 照 article 范式一次返回可见收藏全量（pageNum/pageSize 被忽略），
// 故分页器仅在可见收藏数 > pageSize 时出现，平时不显示——前端只取 records+total 用。

const blogCollects = ref<BlogCollectRow[]>([])
const blogCollectPageNum = ref(1)
const blogCollectPageSize = ref(10)
const blogCollectTotal = ref(0)
const blogCollectLoading = ref(false)
const fetchBlogCollects = async () => {
  blogCollectLoading.value = true
  try {
    const res = await listMyCollectedBlogsApi(blogCollectPageNum.value, blogCollectPageSize.value)
    blogCollects.value = (res.records ?? []).map((b) => ({
      id: b.blogId,
      title: b.title,
      author: b.authorNickname ?? '佚名',
      time: b.publishTime ? (formatDateTime(b.publishTime) as string) : '',
    }))
    blogCollectTotal.value = res.total ?? 0
  } catch {
    blogCollects.value = []
    blogCollectTotal.value = 0
  } finally {
    blogCollectLoading.value = false
  }
}
const onBlogCollectPageChange = (p: number, sz: number) => {
  blogCollectPageNum.value = p
  blogCollectPageSize.value = sz
  void fetchBlogCollects()
}
const removeBlogCollect = async (id: number) => {
  try {
    await collectBlogApi(id, false)
    blogCollects.value = blogCollects.value.filter((c) => c.id !== id)
    blogCollectTotal.value = Math.max(0, blogCollectTotal.value - 1)
  } catch {
    // http 拦截器已弹错
  }
}

const articleCollects = ref<ArticleCollectRow[]>([])
const articleCollectPageNum = ref(1)
const articleCollectPageSize = ref(10)
const articleCollectTotal = ref(0)
const articleCollectLoading = ref(false)
const fetchArticleCollects = async () => {
  articleCollectLoading.value = true
  try {
    const res = await listMyCollectedArticlesApi(articleCollectPageNum.value, articleCollectPageSize.value)
    articleCollects.value = (res.records ?? []).map((a) => ({
      id: a.articleId,
      title: a.title,
      author: a.authorNickname ?? '佚名',
      time: a.publishTime ? (formatDateTime(a.publishTime) as string) : '',
    }))
    articleCollectTotal.value = res.total ?? 0
  } catch {
    articleCollects.value = []
    articleCollectTotal.value = 0
  } finally {
    articleCollectLoading.value = false
  }
}
const onArticleCollectPageChange = (p: number, sz: number) => {
  articleCollectPageNum.value = p
  articleCollectPageSize.value = sz
  void fetchArticleCollects()
}
const removeArticleCollect = async (id: number) => {
  try {
    await collectArticleApi(id, false)
    articleCollects.value = articleCollects.value.filter((c) => c.id !== id)
    articleCollectTotal.value = Math.max(0, articleCollectTotal.value - 1)
  } catch {
    // http 拦截器已弹错
  }
}

const resourceCollects = ref<ResourceCollectRow[]>([])
const resourceCollectPageNum = ref(1)
const resourceCollectPageSize = ref(10)
const resourceCollectTotal = ref(0)
const resourceCollectLoading = ref(false)
const fetchResourceCollects = async () => {
  resourceCollectLoading.value = true
  try {
    const res = await listMyCollectedResourcesApi(resourceCollectPageNum.value, resourceCollectPageSize.value)
    resourceCollects.value = (res.records ?? []).map((r) => ({
      id: r.resourceId,
      title: r.title,
      author: r.authorNickname ?? '佚名',
      time: r.publishTime ? (formatDateTime(r.publishTime) as string) : '',
    }))
    resourceCollectTotal.value = res.total ?? 0
  } catch {
    resourceCollects.value = []
    resourceCollectTotal.value = 0
  } finally {
    resourceCollectLoading.value = false
  }
}
const onResourceCollectPageChange = (p: number, sz: number) => {
  resourceCollectPageNum.value = p
  resourceCollectPageSize.value = sz
  void fetchResourceCollects()
}
const removeResourceCollect = async (id: number) => {
  try {
    await toggleResourceCollectApi(id, false)
    resourceCollects.value = resourceCollects.value.filter((c) => c.id !== id)
    resourceCollectTotal.value = Math.max(0, resourceCollectTotal.value - 1)
  } catch {
    // http 拦截器已弹错
  }
}

const projectCollects = ref<ProjectCollectRow[]>([])
const projectCollectPageNum = ref(1)
const projectCollectPageSize = ref(10)
const projectCollectTotal = ref(0)
const projectCollectLoading = ref(false)
const fetchProjectCollects = async () => {
  projectCollectLoading.value = true
  try {
    const res = await listMyCollectedProjectsApi(projectCollectPageNum.value, projectCollectPageSize.value)
    projectCollects.value = (res.records ?? []).map((p) => ({
      id: p.projectId,
      title: p.title,
      author: p.authorNickname ?? '佚名',
      time: p.publishTime ? (formatDateTime(p.publishTime) as string) : '',
    }))
    projectCollectTotal.value = res.total ?? 0
  } catch {
    projectCollects.value = []
    projectCollectTotal.value = 0
  } finally {
    projectCollectLoading.value = false
  }
}
const onProjectCollectPageChange = (p: number, sz: number) => {
  projectCollectPageNum.value = p
  projectCollectPageSize.value = sz
  void fetchProjectCollects()
}
const removeProjectCollect = async (id: number) => {
  try {
    await toggleProjectCollectApi(id, false)
    projectCollects.value = projectCollects.value.filter((c) => c.id !== id)
    projectCollectTotal.value = Math.max(0, projectCollectTotal.value - 1)
  } catch {
    // http 拦截器已弹错
  }
}

/** 四段收藏是否已拉过（切 collect tab 才首拉，切走再回来不重拉） */
const collectLoaded = ref(false)
const fetchAllCollects = async () => {
  await Promise.all([
    fetchBlogCollects(),
    fetchArticleCollects(),
    fetchResourceCollects(),
    fetchProjectCollects(),
  ])
  collectLoaded.value = true
}
/** 四段合计是否全空（显整体空态用） */
const collectAllEmpty = computed(
  () =>
    !blogCollects.value.length &&
    !articleCollects.value.length &&
    !resourceCollects.value.length &&
    !projectCollects.value.length,
)

/**
 * 收藏 tab 内的二级小 tab：博客/文章/资源/项目四类收藏单段切换，
 * 避免四段平铺过长；count 取各段 total，与小 tab 角标同步。
 */
type CollectSubKey = 'blog' | 'article' | 'resource' | 'project'
const collectSubTab = ref<CollectSubKey>('blog')
const collectSubTabs = computed<{ key: CollectSubKey; label: string; count: number }[]>(() => [
  { key: 'blog', label: '博客', count: blogCollectTotal.value },
  { key: 'article', label: '文章', count: articleCollectTotal.value },
  { key: 'resource', label: '资源', count: resourceCollectTotal.value },
  { key: 'project', label: '项目', count: projectCollectTotal.value },
])

// ============================ 消息 tab：前端切片分页（store 全量） ============================
const msgPageNum = ref(1)
const msgPageSize = ref(10)
const msgTotal = computed(() => noticeStore.myNotices.length)
const myMessages = computed(() => {
  const start = (msgPageNum.value - 1) * msgPageSize.value
  return noticeStore.myNotices.slice(start, start + msgPageSize.value)
})
const onMsgPageChange = (p: number, sz: number) => {
  msgPageNum.value = p
  msgPageSize.value = sz
}
const markMsgRead = (noticeId?: number) => {
  if (noticeId != null) void noticeStore.markAsRead(noticeId)
}

// ============================ tabs / 统计 卡（computed 响应式 count） ============================
const tabs = computed<{ key: TabKey; label: string; count: number }[]>(() => [
  { key: 'blog', label: '我的博客', count: blogTotal.value },
  { key: 'article', label: '我的文章', count: articleTotal.value },
  { key: 'project', label: '我的项目', count: projectTotal.value },
  { key: 'resource', label: '我的资源', count: resourceTotal.value },
  {
    key: 'collect',
    label: '我的收藏',
    count:
      blogCollectTotal.value +
      articleCollectTotal.value +
      resourceCollectTotal.value +
      projectCollectTotal.value,
  },
  { key: 'message', label: '消息通知', count: noticeStore.myNotices.length },
])

/** 统计卡：创作四类走真实 total（我共有 N 篇而非本页 N 条）；收藏走四段合计；消息走 store 全量 */
const userStats = computed(() => [
  { label: '博客', value: blogTotal.value, icon: 'blog' as const, tone: 'var(--kh-primary)' },
  { label: '文章', value: articleTotal.value, icon: 'doc' as const, tone: 'var(--kh-accent)' },
  { label: '项目', value: projectTotal.value, icon: 'project' as const, tone: 'var(--kh-warm)' },
  { label: '资源', value: resourceTotal.value, icon: 'resource' as const, tone: 'var(--kh-info)' },
  {
    label: '收藏',
    value:
      blogCollectTotal.value +
      articleCollectTotal.value +
      resourceCollectTotal.value +
      projectCollectTotal.value,
    icon: 'bookmark' as const,
    tone: 'var(--kh-danger)',
  },
  { label: '消息', value: noticeStore.myNotices.length, icon: 'megaphone' as const, tone: 'var(--kh-accent)' },
])

/** 当前 Tab 标题 */
const activeTabLabel = computed(() => tabs.value.find((t) => t.key === activeTab.value)?.label ?? '')

/** 创作下拉 */
const createItems: { icon: string; label: string; tone: string; to?: string }[] = [
  { icon: 'blog', label: '创作博客', tone: 'var(--kh-primary)', to: '/blog/create' },
  { icon: 'doc', label: '创作文章', tone: 'var(--kh-accent)', to: '/article/create' },
  { icon: 'project', label: '创建项目', tone: 'var(--kh-warm)', to: '/project/create' },
  { icon: 'resource', label: '上传资源', tone: 'var(--kh-success)', to: '/resource/upload' },
]

const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  PENDING_AUTHOR_REVIEW: { text: '待作者审', type: 'warning' },
  REJECTED: { text: '已驳回', type: 'danger' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REVOKED: { text: '已撤回', type: 'neutral' },
  ARCHIVED: { text: '已归档', type: 'info' },
}

const typeLabel: Record<string, string> = { COMPETITION: '比赛', PRACTICE: '练习', OPS: '运维' }

/** 文章内部可见性三档 label（决定章节提交审不审，与等级正交） */
const visibilityLabel: Record<string, string> = {
  PRIVATE: '未公开',
  SEMIPUBLIC: '半公开',
  PUBLIC: '全公开',
}

/**
 * onMounted：
 * - ?tab=xxx：从外部跳进来切到指定 tab（创作页存草稿后跳 ?tab=blog）。
 * - **进页即全量并发拉取**：四个"我的"列表 + 四段收藏 + 消息一次拉齐，
 *   让左侧统计卡六个数字与各 tab 内容一进页就显示真实数据（而非切到才拉、统计卡留 0）。
 *   各 pageNum 重置为 1 避免越界；collectLoaded 由 fetchAllCollects 拉完置位。
 */
onMounted(() => {
  const tab = route.query.tab
  if (typeof tab === 'string' && ROUTE_TABS.includes(tab as TabKey)) {
    activeTab.value = tab as TabKey
  }
  blogPageNum.value = 1
  articlePageNum.value = 1
  projectPageNum.value = 1
  resourcePageNum.value = 1
  void Promise.all([
    fetchMyBlogs(),
    fetchMyArticles(),
    fetchMyProjects(),
    fetchMyResources(),
    fetchAllCollects(),
    noticeStore.loaded ? Promise.resolve() : noticeStore.fetchMyNotices(true).catch(() => undefined),
  ])
})

/**
 * tab 切换 / route query t 变化（带时间戳跳转）：
 * - blog/article/project/resource：重置对应 pageNum=1 再重拉（保证新建草稿首页可见、避免越界）
 * - collect：未拉过才并发拉四段，已拉过直接复用（不重复请求）
 * - message：store 已在守卫拉，loaded=false 时补拉
 */
watch(
  () => [activeTab.value, route.query.t],
  ([tab]) => {
    if (tab === 'blog') {
      blogPageNum.value = 1
      void fetchMyBlogs()
    } else if (tab === 'article') {
      articlePageNum.value = 1
      void fetchMyArticles()
    } else if (tab === 'project') {
      projectPageNum.value = 1
      void fetchMyProjects()
    } else if (tab === 'resource') {
      resourcePageNum.value = 1
      void fetchMyResources()
    } else if (tab === 'collect') {
      if (!collectLoaded.value) void fetchAllCollects()
    } else if (tab === 'message') {
      if (!noticeStore.loaded) void noticeStore.fetchMyNotices(true)
    }
  },
)
</script>

<template>
  <div class="profile">
    <div class="kh-container kh-container--wide profile__body">
      <!-- 左 sticky 侧栏：用户卡 + 创作入口 + 统计 -->
      <aside class="profile__side">
        <KhCard padding="lg" class="profile__card">
          <div class="profile__head">
            <KhAvatar :item="{ label: displayName }" :size="72" />
            <div class="profile__info">
              <div class="profile__name-row">
                <h1 class="profile__name">{{ displayName }}</h1>
                <KhTag type="primary" dot>{{ displayRole }}</KhTag>
              </div>
              <div class="profile__username">@{{ displayUsername }}</div>
            </div>
          </div>
          <p class="profile__bio">{{ displayBio }}</p>

          <div class="profile__actions">
            <el-dropdown trigger="click">
              <button class="profile__create" type="button">
                <el-icon><Plus /></el-icon> 创作
                <el-icon class="profile__create-caret"><KhIcon name="chevron-right" :size="12" /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="c in createItems"
                    :key="c.label"
                    @click="c.to ? $router.push(c.to) : undefined"
                  >
                    <KhIcon :name="c.icon" :size="14" :style="{ color: c.tone }" /> {{ c.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button class="profile__edit" type="button" @click="$router.push('/profile/edit')">
              <el-icon><Edit /></el-icon> 编辑资料
            </button>
          </div>

          <div class="profile__stats">
            <div v-for="s in userStats" :key="s.label" class="profile__stat">
              <div class="profile__stat-icon" :style="{ color: s.tone, background: `${s.tone}1a` }">
                <KhIcon :name="s.icon" :size="16" />
              </div>
              <div>
                <div class="profile__stat-value">{{ s.value > 99999 ? '99999+' : s.value }}</div>
                <div class="profile__stat-label">{{ s.label }}</div>
              </div>
            </div>
          </div>
        </KhCard>
      </aside>

      <!-- 右：Tab + 内容管理 -->
      <section class="profile__main">
        <KhCard padding="none" class="profile__tabs-card">
          <div class="profile__tabs">
            <button
              v-for="t in tabs"
              :key="t.key"
              class="profile__tab"
              :class="{ 'is-active': activeTab === t.key }"
              type="button"
              @click="activeTab = t.key"
            >
              {{ t.label }}
              <span class="profile__tab-count">{{ t.count }}</span>
            </button>
          </div>

          <div class="profile__tab-content">
            <div class="profile__tab-head">
              <h2 class="profile__tab-title">{{ activeTabLabel }}</h2>
              <div class="profile__tab-tools">
                <button v-if="activeTab === 'blog'" class="profile__tab-tool" type="button" @click="$router.push('/blog/create')"><el-icon><Plus /></el-icon> 新建</button>
                <button v-if="activeTab === 'article'" class="profile__tab-tool" type="button" @click="$router.push('/article/create')"><el-icon><Plus /></el-icon> 新建</button>
                <button v-if="activeTab === 'project'" class="profile__tab-tool" type="button" @click="$router.push('/project/create')"><el-icon><Plus /></el-icon> 新建</button>
                <button v-if="activeTab === 'resource'" class="profile__tab-tool" type="button" @click="$router.push('/resource/upload')"><el-icon><Plus /></el-icon> 上传</button>
              </div>
            </div>

            <!-- 我的博客 -->
            <div v-if="activeTab === 'blog'" class="profile__list">
              <div v-if="myBlogsLoading" class="profile__placeholder">
                <p>加载中…</p>
              </div>
              <div v-else-if="!myBlogs.length" class="profile__placeholder">
                <KhIcon name="blog" :size="40" :stroke="1.4" />
                <p>还没有博客，点上方「新建」开始创作</p>
              </div>
              <div v-for="b in myBlogs" :key="b.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title" @click="$router.push(`/blog/create?id=${b.id}`)">{{ b.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" :type="statusMeta[b.status]?.type ?? 'neutral'">{{ statusMeta[b.status]?.text ?? '未知' }}</KhTag>
                    <span>{{ b.views }} 阅读</span>
                    <span>·</span>
                    <span>{{ b.likes }} 赞</span>
                    <span>·</span>
                    <span>{{ b.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑" @click="$router.push(`/blog/create?id=${b.id}`)"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn" type="button" title="撤回"><el-icon><RefreshLeft /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
              <KhPagination
                v-if="blogTotal > blogPageSize"
                v-model:current="blogPageNum"
                v-model:page-size="blogPageSize"
                :total="blogTotal"
                :page-sizes="PAGE_SIZES"
                @change="onBlogPageChange"
              />
            </div>

            <!-- 我的文章（真实接口 /authoring/article/list，文章=章节集合） -->
            <div v-else-if="activeTab === 'article'" class="profile__list">
              <div v-if="myArticlesLoading" class="profile__placeholder">
                <p>加载中…</p>
              </div>
              <div v-else-if="!myArticles.length" class="profile__placeholder">
                <KhIcon name="doc" :size="40" :stroke="1.4" />
                <p>还没有文章，点上方「新建」开始创作（文章 = 章节集合，正文写在各章节里）</p>
              </div>
              <div v-for="a in myArticles" :key="a.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title" @click="$router.push(`/article/${a.id}/chapters`)">{{ a.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" :type="statusMeta[a.status]?.type ?? 'neutral'">{{ statusMeta[a.status]?.text ?? '未知' }}</KhTag>
                    <KhTag size="sm" type="info">{{ visibilityLabel[a.visibility] ?? a.visibility }}</KhTag>
                    <span>{{ a.views }} 阅读</span>
                    <span>·</span>
                    <span>{{ a.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <!-- 章节管理：文章的核心操作是管理章节（新增/编辑/发布/撤回章节） -->
                  <button class="profile__row-btn" type="button" title="章节管理" @click="$router.push(`/article/${a.id}/chapters`)"><el-icon><Plus /></el-icon></button>
                  <!-- 编辑文章元信息（标题/前言/等级/可见性/封面/标签） -->
                  <button class="profile__row-btn" type="button" title="编辑文章信息" @click="$router.push(`/article/create?id=${a.id}`)"><el-icon><Edit /></el-icon></button>
                </div>
              </div>
              <KhPagination
                v-if="articleTotal > articlePageSize"
                v-model:current="articlePageNum"
                v-model:page-size="articlePageSize"
                :total="articleTotal"
                :page-sizes="PAGE_SIZES"
                @change="onArticlePageChange"
              />
            </div>

            <!-- 我的项目（真实接口 /authoring/project/list，薄封装 quarryProject 走作者分支） -->
            <div v-else-if="activeTab === 'project'" class="profile__list">
              <div v-if="myProjectsLoading" class="profile__placeholder">
                <p>加载中…</p>
              </div>
              <div v-else-if="!myProjects.length" class="profile__placeholder">
                <KhIcon name="project" :size="40" :stroke="1.4" />
                <p>还没有项目，点上方「新建」开始创建</p>
              </div>
              <div v-for="p in myProjects" :key="p.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title" @click="$router.push(`/project/${p.id}`)">{{ p.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" type="primary">{{ typeLabel[p.type] ?? p.type }}</KhTag>
                    <KhTag size="sm" :type="statusMeta[p.status]?.type ?? 'neutral'">{{ statusMeta[p.status]?.text ?? '未知' }}</KhTag>
                    <KhTag size="sm" :type="viewLevelTagType[p.level] ?? 'neutral'">{{ getViewLevelLabel(p.level) }}</KhTag>
                    <span>·</span>
                    <span>{{ p.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑" @click="$router.push(`/project/create?id=${p.id}`)"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
              <KhPagination
                v-if="projectTotal > projectPageSize"
                v-model:current="projectPageNum"
                v-model:page-size="projectPageSize"
                :total="projectTotal"
                :page-sizes="PAGE_SIZES"
                @change="onProjectPageChange"
              />
            </div>

            <!-- 我的资源（真实接口 /authoring/resource/list，薄封装 listMyResources 走 author_id 分支） -->
            <div v-else-if="activeTab === 'resource'" class="profile__list">
              <div v-if="myResourcesLoading" class="profile__placeholder">
                <p>加载中…</p>
              </div>
              <div v-else-if="!myResources.length" class="profile__placeholder">
                <KhIcon name="resource" :size="40" :stroke="1.4" />
                <p>还没有资源，点上方「上传」开始上传</p>
              </div>
              <div v-for="r in myResources" :key="r.id" class="profile__row">
                <div class="profile__row-main">
                  <div class="profile__row-title" @click="$router.push(`/resource/upload?id=${r.id}`)">{{ r.title }}</div>
                  <div class="profile__row-meta">
                    <KhTag size="sm" type="accent">{{ r.categoryName }}</KhTag>
                    <KhTag size="sm" type="info">{{ r.resourceType === 'LINK' ? '链接' : '文件' }}</KhTag>
                    <KhTag size="sm" :type="statusMeta[r.status]?.type ?? 'neutral'">{{ statusMeta[r.status]?.text ?? '未知' }}</KhTag>
                    <span>·</span>
                    <span>{{ r.downloadCount }} 下载</span>
                    <span>·</span>
                    <span>{{ r.updateTime }}</span>
                  </div>
                </div>
                <div class="profile__row-actions">
                  <button class="profile__row-btn" type="button" title="编辑" @click="$router.push(`/resource/upload?id=${r.id}`)"><el-icon><Edit /></el-icon></button>
                  <button class="profile__row-btn profile__row-btn--danger" type="button" title="删除"><el-icon><Delete /></el-icon></button>
                </div>
              </div>
              <KhPagination
                v-if="resourceTotal > resourcePageSize"
                v-model:current="resourcePageNum"
                v-model:page-size="resourcePageSize"
                :total="resourceTotal"
                :page-sizes="PAGE_SIZES"
                @change="onResourcePageChange"
              />
            </div>

            <!-- 我的收藏：小 tab（博客/文章/资源/项目）切换单段展示 -->
            <div v-else-if="activeTab === 'collect'" class="profile__collect">
              <!-- 二级小 tab：四类收藏单段切换（无论有无收藏始终显示，空时角标为 0） -->
              <div class="profile__collect-subtabs">
                <button
                  v-for="st in collectSubTabs"
                  :key="st.key"
                  class="profile__collect-subtab"
                  :class="{ 'is-active': collectSubTab === st.key }"
                  type="button"
                  @click="collectSubTab = st.key"
                >
                  {{ st.label }}
                  <span class="profile__collect-subtab-count">{{ st.count }}</span>
                </button>
              </div>

              <!-- 整体空态：四段全空才显（小 tab 仍可见，告知用户有四类收藏位） -->
              <div v-if="collectAllEmpty && !blogCollectLoading && !articleCollectLoading && !resourceCollectLoading && !projectCollectLoading" class="profile__list-empty">
                <el-icon><Collection /></el-icon>
                <p>暂无收藏，去浏览内容收藏喜欢的吧</p>
              </div>

              <template v-else>

                <!-- 博客收藏 -->
                <div v-if="collectSubTab === 'blog'" class="profile__collect-section">
                  <template v-if="blogCollects.length">
                    <div v-for="c in blogCollects" :key="`b-${c.id}`" class="profile__row">
                      <div class="profile__row-main">
                        <div class="profile__row-title" @click="$router.push(`/blog/${c.id}`)">
                          <KhTag size="sm" type="primary">博客</KhTag> {{ c.title }}
                        </div>
                        <div class="profile__row-meta">
                          <span>{{ c.author }}</span>
                          <span>·</span>
                          <span>{{ c.time }}</span>
                        </div>
                      </div>
                      <div class="profile__row-actions">
                        <button class="profile__row-btn profile__row-btn--danger" type="button" title="取消收藏" @click="removeBlogCollect(c.id)"><el-icon><Delete /></el-icon></button>
                      </div>
                    </div>
                    <KhPagination
                      v-if="blogCollectTotal > blogCollectPageSize"
                      v-model:current="blogCollectPageNum"
                      v-model:page-size="blogCollectPageSize"
                      :total="blogCollectTotal"
                      :page-sizes="PAGE_SIZES"
                      @change="onBlogCollectPageChange"
                    />
                  </template>
                  <div v-else-if="!blogCollectLoading" class="profile__collect-empty">暂无博客收藏</div>
                </div>

                <!-- 文章收藏 -->
                <div v-if="collectSubTab === 'article'" class="profile__collect-section">
                  <template v-if="articleCollects.length">
                    <div v-for="c in articleCollects" :key="`a-${c.id}`" class="profile__row">
                      <div class="profile__row-main">
                        <div class="profile__row-title" @click="$router.push(`/article/${c.id}`)">
                          <KhTag size="sm" type="accent">文章</KhTag> {{ c.title }}
                        </div>
                        <div class="profile__row-meta">
                          <span>{{ c.author }}</span>
                          <span>·</span>
                          <span>{{ c.time }}</span>
                        </div>
                      </div>
                      <div class="profile__row-actions">
                        <button class="profile__row-btn profile__row-btn--danger" type="button" title="取消收藏" @click="removeArticleCollect(c.id)"><el-icon><Delete /></el-icon></button>
                      </div>
                    </div>
                    <KhPagination
                      v-if="articleCollectTotal > articleCollectPageSize"
                      v-model:current="articleCollectPageNum"
                      v-model:page-size="articleCollectPageSize"
                      :total="articleCollectTotal"
                      :page-sizes="PAGE_SIZES"
                      @change="onArticleCollectPageChange"
                    />
                  </template>
                  <div v-else-if="!articleCollectLoading" class="profile__collect-empty">暂无文章收藏</div>
                </div>

                <!-- 资源收藏 -->
                <div v-if="collectSubTab === 'resource'" class="profile__collect-section">
                  <template v-if="resourceCollects.length">
                    <div v-for="c in resourceCollects" :key="`r-${c.id}`" class="profile__row">
                      <div class="profile__row-main">
                        <div class="profile__row-title" @click="$router.push(`/resource/${c.id}`)">
                          <KhTag size="sm" type="info">资源</KhTag> {{ c.title }}
                        </div>
                        <div class="profile__row-meta">
                          <span>{{ c.author }}</span>
                          <span>·</span>
                          <span>{{ c.time }}</span>
                        </div>
                      </div>
                      <div class="profile__row-actions">
                        <button class="profile__row-btn profile__row-btn--danger" type="button" title="取消收藏" @click="removeResourceCollect(c.id)"><el-icon><Delete /></el-icon></button>
                      </div>
                    </div>
                    <KhPagination
                      v-if="resourceCollectTotal > resourceCollectPageSize"
                      v-model:current="resourceCollectPageNum"
                      v-model:page-size="resourceCollectPageSize"
                      :total="resourceCollectTotal"
                      :page-sizes="PAGE_SIZES"
                      @change="onResourceCollectPageChange"
                    />
                  </template>
                  <div v-else-if="!resourceCollectLoading" class="profile__collect-empty">暂无资源收藏</div>
                </div>

                <!-- 项目收藏 -->
                <div v-if="collectSubTab === 'project'" class="profile__collect-section">
                  <template v-if="projectCollects.length">
                    <div v-for="c in projectCollects" :key="`p-${c.id}`" class="profile__row">
                      <div class="profile__row-main">
                        <div class="profile__row-title" @click="$router.push(`/project/${c.id}`)">
                          <KhTag size="sm" type="warm">项目</KhTag> {{ c.title }}
                        </div>
                        <div class="profile__row-meta">
                          <span>{{ c.author }}</span>
                          <span>·</span>
                          <span>{{ c.time }}</span>
                        </div>
                      </div>
                      <div class="profile__row-actions">
                        <button class="profile__row-btn profile__row-btn--danger" type="button" title="取消收藏" @click="removeProjectCollect(c.id)"><el-icon><Delete /></el-icon></button>
                      </div>
                    </div>
                    <KhPagination
                      v-if="projectCollectTotal > projectCollectPageSize"
                      v-model:current="projectCollectPageNum"
                      v-model:page-size="projectCollectPageSize"
                      :total="projectCollectTotal"
                      :page-sizes="PAGE_SIZES"
                      @change="onProjectCollectPageChange"
                    />
                  </template>
                  <div v-else-if="!projectCollectLoading" class="profile__collect-empty">暂无项目收藏</div>
                </div>
              </template>
            </div>

            <!-- 消息通知（前端切片分页，store 全量） -->
            <div v-else class="profile__list">
              <template v-if="myMessages.length">
                <div v-for="m in myMessages" :key="m.noticeId" class="profile__row" :class="{ 'is-read': m.hasRead }">
                  <div class="profile__row-main">
                    <div class="profile__row-title">
                      <el-icon class="profile__msg-icon"><ChatDotRound /></el-icon>
                      {{ m.title }}
                    </div>
                    <div class="profile__row-meta">
                      <KhTag size="sm" :type="m.level === '紧急' ? 'danger' : m.level === '重要' ? 'warning' : 'primary'">{{ m.noticeType }}</KhTag>
                      <span>{{ m.createBy ?? '系统' }}</span>
                      <span>·</span>
                      <span>{{ m.publishTime ?? '' }}</span>
                    </div>
                  </div>
                  <div class="profile__row-actions">
                    <button class="profile__row-btn" type="button" title="标记已读" @click="markMsgRead(m.noticeId)"><el-icon><Bell /></el-icon></button>
                  </div>
                </div>
                <KhPagination
                  v-if="msgTotal > msgPageSize"
                  v-model:current="msgPageNum"
                  v-model:page-size="msgPageSize"
                  :total="msgTotal"
                  :page-sizes="PAGE_SIZES"
                  @change="onMsgPageChange"
                />
              </template>
              <div v-else class="profile__list-empty">
                <el-icon><Bell /></el-icon>
                <p>暂无消息通知</p>
              </div>
            </div>
          </div>
        </KhCard>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile {
  padding-top: var(--kh-space-8);
  padding-bottom: var(--kh-space-12);
  background: var(--kh-bg);
  min-height: 60vh;
}
.profile__body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--kh-space-6);
  align-items: start;
}

/* —— 左侧用户卡 —— */
.profile__side {
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.profile__card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.profile__head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.profile__info {
  flex: 1;
  min-width: 0;
}
.profile__name-row {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex-wrap: wrap;
}
.profile__name {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
}
.profile__username {
  font-family: var(--kh-font-mono);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}
.profile__bio {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  line-height: 1.7;
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
  margin: 0;
}
.profile__actions {
  display: flex;
  gap: var(--kh-space-3);
}
.profile__create {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  justify-content: center;
  height: 40px;
  padding: 0 var(--kh-space-4);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
}
.profile__create-caret {
  margin-left: 2px;
}
.profile__edit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-weight: 500;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
}
.profile__edit:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.profile__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kh-space-3) var(--kh-space-4);
  padding-top: var(--kh-space-4);
  border-top: 1px solid var(--kh-border-soft);
}
.profile__stat {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  min-width: 0;
}
.profile__stat-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
}
.profile__stat-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
  color: var(--kh-text);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.profile__stat-label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
}

/* —— 右侧 Tab 区 —— */
.profile__main {
  min-width: 0;
}
.profile__tabs-card {
  overflow: hidden;
}
.profile__tabs {
  display: flex;
  gap: 2px;
  padding: var(--kh-space-3) var(--kh-space-3) 0;
  border-bottom: 1px solid var(--kh-border-soft);
  overflow-x: auto;
}
.profile__tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  transition: color var(--kh-transition-fast);
}
.profile__tab:hover {
  color: var(--kh-text);
}
.profile__tab.is-active {
  color: var(--kh-primary);
  border-bottom-color: var(--kh-primary);
  font-weight: 600;
}
.profile__tab-count {
  padding: 1px 7px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 500;
}
.profile__tab.is-active .profile__tab-count {
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.profile__tab-content {
  padding: 0 var(--kh-space-5);
}
.profile__tab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kh-space-5) 0 var(--kh-space-4);
}
.profile__tab-title {
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
}
.profile__tab-tools {
  display: flex;
  gap: var(--kh-space-2);
}
.profile__tab-tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.profile__tab-tool:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}

.profile__list {
  display: flex;
  flex-direction: column;
}
.profile__list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-10) 0;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.profile__list-empty .el-icon {
  font-size: 32px;
}
.profile__list-empty p {
  font-size: var(--kh-font-size-sm);
}
.profile__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-4) var(--kh-space-2);
  border-bottom: 1px solid var(--kh-border-soft);
  transition: background var(--kh-transition-fast);
}
.profile__row:hover {
  background: var(--kh-surface-muted);
}
.profile__row:last-child {
  border-bottom: none;
}
.profile__row-main {
  min-width: 0;
  flex: 1;
}
.profile__row-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kh-font-size-md);
  font-weight: 500;
  color: var(--kh-text);
  cursor: pointer;
}
.profile__row-title:hover {
  color: var(--kh-primary);
}
.profile__msg-icon {
  color: var(--kh-warm);
}
.profile__row-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex-wrap: wrap;
}
.profile__row-actions {
  display: flex;
  gap: 6px;
  flex: none;
}
.profile__row-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.profile__row-btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.profile__row-btn--danger:hover {
  border-color: var(--kh-danger);
  color: var(--kh-danger);
  background: var(--kh-danger-soft);
}
.profile__placeholder {
  text-align: center;
  color: var(--kh-text-tertiary);
  padding: var(--kh-space-10);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}

/* —— 收藏二级小 tab + 段落 —— */
.profile__collect {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
  padding-bottom: var(--kh-space-6);
}
.profile__collect-subtabs {
  display: flex;
  gap: var(--kh-space-2);
  padding: var(--kh-space-1) 0;
  border-bottom: 1px solid var(--kh-border-soft);
}
.profile__collect-subtab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color var(--kh-transition-fast);
}
.profile__collect-subtab:hover {
  color: var(--kh-text);
}
.profile__collect-subtab.is-active {
  color: var(--kh-primary);
  border-bottom-color: var(--kh-primary);
  font-weight: 600;
}
.profile__collect-subtab-count {
  padding: 1px 7px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 500;
}
.profile__collect-subtab.is-active .profile__collect-subtab-count {
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.profile__collect-section {
  display: flex;
  flex-direction: column;
}
.profile__collect-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
  padding: var(--kh-space-2) var(--kh-space-2);
  border-bottom: 1px solid var(--kh-border-soft);
}
.profile__collect-count {
  margin-left: 2px;
  padding: 1px 7px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 500;
}
.profile__collect-empty {
  padding: var(--kh-space-5) 0;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  text-align: center;
}
.profile__row.is-read {
  opacity: 0.6;
}

@media (max-width: 900px) {
  .profile__body {
    grid-template-columns: 1fr;
  }
  .profile__side {
    position: static;
  }
}
</style>
