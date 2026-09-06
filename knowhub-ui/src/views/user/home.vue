<!--
  用户公开主页 /user/:id
  ------------------------------------------------------------------
  点击评论区用户名 / 作品作者名跳转目标。完全套用个人中心 /profile 的两栏样式：
    左 sticky 用户卡（头像 + 昵称 + 性别 + @username + 加入时间 + 创作统计前 4 项）
    / 右 Tab 作品列表（横条展示，非卡片网格——与个人中心「我的博客/文章/项目/资源」行同款）。
  与个人中心的差异：只展示前 4 个 tab（博客/文档/项目/资源），无收藏/协作/消息；
  左侧统计也只用前 4 项（博客/文档/项目/资源 各真实 total），点统计卡可切对应 tab；
  全只读——无创作下拉、无编辑资料、无行内编辑/删除按钮（看的是别人的主页，行尾只有「查看」）。
  每类带 authorId 调对应 /portal/*/search 按作者筛作品，返回的是该作者已发布作品，
  不显草稿/审核态，改显作品类型标签（博客/文档可见性/项目类型+等级/资源类别+类型）+ 浏览/下载 + 时间。
  <p>
  作品列表复用各模块 search 接口（带 authorId）即继承越级 locked 标记（prior session 落地
  fillLockedForList + where level<=userViewLevel+1）：列表不锁、点详情按各模块锁态加锁。
  路由不带 requiresAuth，对齐列表公开口径，未登录游客也能访问看公开作品。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import { getUserPublicApi } from '@/api/knowhub/user-portal'
import { searchBlogsApi } from '@/api/knowhub/blog'
import { searchProjectsApi } from '@/api/knowhub/project-portal'
import { searchArticlesApi } from '@/api/knowhub/article'
import { searchResourcesApi } from '@/api/knowhub/resource-portal'
import type { UserPublicRecord } from '@/types/api/knowhub/user-portal'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'
import type { ProjectPortalRecord } from '@/types/api/knowhub/project-portal'
import type { ArticlePortalRecord } from '@/types/api/knowhub/article'
import type { ResourcePortalRecord } from '@/types/api/knowhub/resource'
import { formatDateTime, formatDate } from '@/utils/format'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'

const router = useRouter()
const route = useRoute()
/** 路由 :id → userId（pathParam 非响应式，用 computed 从 route.params 取，切用户时重拉） */
const userId = computed(() => Number(route.params.id))

type TabKey = 'BLOG' | 'ARTICLE' | 'PROJECT' | 'RESOURCE'
/** 4 tab：对齐个人中心前 4 个创作 tab（博客/文章/项目/资源），不显收藏/协作/消息 */
const tabs: { key: TabKey; label: string; icon: string; tone: string }[] = [
  { key: 'BLOG', label: '博客', icon: 'blog', tone: 'var(--kh-primary)' },
  { key: 'ARTICLE', label: '文档', icon: 'doc', tone: 'var(--kh-accent)' },
  { key: 'PROJECT', label: '项目', icon: 'project', tone: 'var(--kh-warm)' },
  { key: 'RESOURCE', label: '资源', icon: 'resource', tone: 'var(--kh-info)' },
]

/** 用户公开主页信息（左侧用户卡展示用；后端不返手机号等敏感字段） */
const profile = ref<UserPublicRecord | null>(null)
const profileLoading = ref(false)
const profileError = ref(false)

/** 性别字典硬编码（后端 sys_user.sex：1=男 / 0=女；与 profile/edit.vue 一致，不依赖字典 store） */
const sexLabel = computed(() => {
  const s = profile.value?.sex
  if (s === '1') return '男'
  if (s === '0') return '女'
  return ''
})

/** 拉用户公开主页信息（失败置 error 态，左侧用户卡显「用户不存在」） */
const fetchProfile = async () => {
  profileLoading.value = true
  profileError.value = false
  try {
    const res = await getUserPublicApi(userId.value)
    profile.value = res.data ?? null
    if (!profile.value) profileError.value = true
  } catch {
    // 后端用户不存在/已停用抛 ServiceException(404) → http util 已弹提示，此处置 error 态显降级文案
    profile.value = null
    profileError.value = true
  } finally {
    profileLoading.value = false
  }
}

const PAGE_SIZE = 10
/** 共享每页条数（对齐个人中心「我的」列表每页 10，[10,20,50] 候选） */
const pageSize = ref(PAGE_SIZE)
const PAGE_SIZES = [10, 20, 50]

/** 四类作品各自的数据/分页/加载态。切 tab 首次懒加载该类首页，翻页复用。 */
type CategoryState<T> = {
  records: T[]
  total: number
  pageNum: number
  loaded: boolean
  loading: boolean
}
const blogState = ref<CategoryState<BlogPortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const articleState = ref<CategoryState<ArticlePortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const projectState = ref<CategoryState<ProjectPortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const resourceState = ref<CategoryState<ResourcePortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })

const activeTab = ref<TabKey>('BLOG')

/** 各类单页拉取（带 authorId 按作者筛；分页由对应 state.pageNum 驱动） */
const fetchBlogPage = async () => {
  blogState.value.loading = true
  try {
    const page = await searchBlogsApi({ authorId: userId.value, pageNum: blogState.value.pageNum, pageSize: pageSize.value, sort: 'LATEST' })
    blogState.value.records = page.records ?? []
    blogState.value.total = page.total ?? 0
    blogState.value.loaded = true
  } finally {
    blogState.value.loading = false
  }
}
const fetchArticlePage = async () => {
  articleState.value.loading = true
  try {
    const page = await searchArticlesApi({ authorId: userId.value, pageNum: articleState.value.pageNum, pageSize: pageSize.value, sort: 'LATEST' })
    articleState.value.records = page.records ?? []
    articleState.value.total = page.total ?? 0
    articleState.value.loaded = true
  } finally {
    articleState.value.loading = false
  }
}
const fetchProjectPage = async () => {
  projectState.value.loading = true
  try {
    const page = await searchProjectsApi({ authorId: userId.value, pageNum: projectState.value.pageNum, pageSize: pageSize.value, sort: 'LATEST' })
    projectState.value.records = page.records ?? []
    projectState.value.total = page.total ?? 0
    projectState.value.loaded = true
  } finally {
    projectState.value.loading = false
  }
}
const fetchResourcePage = async () => {
  resourceState.value.loading = true
  try {
    const page = await searchResourcesApi({ authorId: userId.value, pageNum: resourceState.value.pageNum, pageSize: pageSize.value, sort: 'LATEST' })
    resourceState.value.records = page.records ?? []
    resourceState.value.total = page.total ?? 0
    resourceState.value.loaded = true
  } finally {
    resourceState.value.loading = false
  }
}

/** 重置四类 state（切用户时调，避免上一用户作品残留） */
const resetAll = () => {
  blogState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  articleState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  projectState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  resourceState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  activeTab.value = 'BLOG'
}

/**
 * 首页预取：拉横幅成功后**并发拉四类作品第 1 页**，
 * 让左侧统计卡四个数字与四个 tab 角标一进页就显示真实总数（而非默认 0、点 tab 才回填）。
 * 与个人中心 onMounted 全量并发范式一致。fetchXxxPage 内部已置 loaded，切 tab 直接展示缓存不复取。
 */
const fetchAllFirstPages = async () => {
  await Promise.all([fetchBlogPage(), fetchArticlePage(), fetchProjectPage(), fetchResourcePage()])
}

/** 切 tab：已加载过则直接展示缓存，不重复请求（首页预取后通常已 loaded） */
const changeTab = (key: TabKey) => {
  activeTab.value = key
  if (key === 'BLOG' && !blogState.value.loaded) void fetchBlogPage()
  else if (key === 'ARTICLE' && !articleState.value.loaded) void fetchArticlePage()
  else if (key === 'PROJECT' && !projectState.value.loaded) void fetchProjectPage()
  else if (key === 'RESOURCE' && !resourceState.value.loaded) void fetchResourcePage()
}

/** 翻页：置该类 pageNum 后重拉该页 */
const onPageChange = (key: TabKey, p: number, sz: number) => {
  pageSize.value = sz
  if (key === 'BLOG') { blogState.value.pageNum = p; void fetchBlogPage() }
  else if (key === 'ARTICLE') { articleState.value.pageNum = p; void fetchArticlePage() }
  else if (key === 'PROJECT') { projectState.value.pageNum = p; void fetchProjectPage() }
  else if (key === 'RESOURCE') { resourceState.value.pageNum = p; void fetchResourcePage() }
}

const hasProfile = computed(() => Boolean(profile.value))
const displayName = computed(() => profile.value?.nickName || profile.value?.username || '用户')

/**
 * 左侧创作统计前 4 项（对齐个人中心 userStats 前 4，只取博客/文档/项目/资源）。
 * count 取各类真实 total（后端 search 分页 total = 该作者全部已发布作品数）。
 * 点击统计卡切到对应 tab（只读浏览页，把统计卡当 tab 入口）。
 */
const userStats = computed(() => [
  { key: 'BLOG' as TabKey, label: '博客', value: blogState.value.total, icon: 'blog' as const, tone: 'var(--kh-primary)' },
  { key: 'ARTICLE' as TabKey, label: '文档', value: articleState.value.total, icon: 'doc' as const, tone: 'var(--kh-accent)' },
  { key: 'PROJECT' as TabKey, label: '项目', value: projectState.value.total, icon: 'project' as const, tone: 'var(--kh-warm)' },
  { key: 'RESOURCE' as TabKey, label: '资源', value: resourceState.value.total, icon: 'resource' as const, tone: 'var(--kh-info)' },
])

/** 当前 Tab 标题（右侧面板头展示用） */
const activeTabLabel = computed(() => tabs.find((t) => t.key === activeTab.value)?.label ?? '')

/** 项目类型字典（与个人中心 typeLabel 同款硬编码：COMPETITION/PRACTICE/OPS） */
const projectTypeLabel: Record<string, string> = { COMPETITION: '比赛', PRACTICE: '练习', OPS: '运维' }

/** 行内时间格式化（publishTime 为后端下发，无则空串） */
const rowTime = (t?: string) => (t ? (formatDateTime(t) as string) : '')

/** 监听路由 :id 变化（进入页 / 切到别的用户主页）：重置 + 拉横幅 + 并发预取四类首页（统计一进页就显真实总数） */
watch(
  () => userId.value,
  () => {
    resetAll()
    void fetchProfile().then(() => {
      // 横幅拉到才并发拉四类首页（用户不存在时不白拉作品列表）
      if (hasProfile.value) void fetchAllFirstPages()
    })
  },
  { immediate: true },
)
</script>

<template>
  <div class="uh">
    <div class="kh-container kh-container--wide uh__body">
      <!-- 加载中：整页骨架（横幅 + 列表都在拉） -->
      <div v-if="profileLoading" class="uh__fullstate">
        <KhIcon name="user" :size="40" :stroke="1.4" />
        <p>加载中…</p>
      </div>
      <!-- 用户不存在 / 已停用：整页降级 -->
      <div v-else-if="profileError" class="uh__fullstate">
        <KhIcon name="user" :size="48" :stroke="1.4" />
        <h2>用户不存在</h2>
        <p>该用户可能已注销或被停用</p>
      </div>

      <template v-else-if="profile">
        <!-- 左 sticky 侧栏：用户卡 + 创作统计（前 4 项） -->
        <aside class="uh__side">
          <KhCard padding="lg" class="uh__card">
            <div class="uh__head">
              <KhAvatar :item="{ label: displayName, src: profile.avatar ?? undefined }" :size="72" />
              <div class="uh__info">
                <div class="uh__name-row">
                  <h1 class="uh__name">{{ displayName }}</h1>
                  <KhTag v-if="sexLabel" type="primary" dot>{{ sexLabel }}</KhTag>
                </div>
                <div v-if="profile.username" class="uh__username">@{{ profile.username }}</div>
              </div>
            </div>

            <p class="uh__bio">还没有个人简介</p>

            <!-- 注册时间：作为 bio 下的次级展示行（与个人中心 bio 同位置的 border-top 分隔） -->
            <p v-if="profile.createTime" class="uh__joined">
              <KhIcon name="clock" :size="13" /> 加入于 {{ formatDate(profile.createTime) }}
            </p>

            <!-- 创作统计前 4 项：点击切对应 tab（只读浏览页，统计卡当 tab 入口） -->
            <div class="uh__stats">
              <button
                v-for="s in userStats"
                :key="s.key"
                type="button"
                class="uh__stat"
                :class="{ 'is-active': activeTab === s.key }"
                @click="changeTab(s.key)"
              >
                <div class="uh__stat-icon" :style="{ color: s.tone, background: `${s.tone}1a` }">
                  <KhIcon :name="s.icon" :size="16" />
                </div>
                <div>
                  <div class="uh__stat-value">{{ s.value > 99999 ? '99999+' : s.value }}</div>
                  <div class="uh__stat-label">{{ s.label }}</div>
                </div>
              </button>
            </div>
          </KhCard>
        </aside>

        <!-- 右：Tab + 作品列表（横条展示，非卡片网格——对齐个人中心「我的」列表行样式） -->
        <section class="uh__main">
          <KhCard padding="none" class="uh__tabs-card">
            <div class="uh__tabs">
              <button
                v-for="t in tabs"
                :key="t.key"
                class="uh__tab"
                :class="{ 'is-active': activeTab === t.key }"
                type="button"
                @click="changeTab(t.key)"
              >
                {{ t.label }}
                <span class="uh__tab-count">{{ userStats.find((s) => s.key === t.key)?.value ?? 0 }}</span>
              </button>
            </div>

            <div class="uh__tab-content">
              <div class="uh__tab-head">
                <h2 class="uh__tab-title">{{ activeTabLabel }}</h2>
              </div>

              <!-- 博客（横条行，对齐个人中心「我的博客」行） -->
              <div v-if="activeTab === 'BLOG'" class="uh__list">
                <div v-if="blogState.loading" class="uh__placeholder"><p>加载中…</p></div>
                <div v-else-if="!blogState.records.length" class="uh__list-empty">
                  <KhIcon name="blog" :size="40" :stroke="1.4" />
                  <p>该用户暂未发布博客</p>
                </div>
                <div v-for="b in blogState.records" :key="b.blogId" class="uh__row">
                  <div class="uh__row-main">
                    <div class="uh__row-title" @click="router.push(`/blog/${b.blogId}`)">
                      <KhTag size="sm" type="primary">博客</KhTag> {{ b.title }}
                    </div>
                    <div class="uh__row-meta">
                      <span>{{ b.viewCount ?? 0 }} 阅读</span>
                      <span>·</span>
                      <span>{{ b.likeCount ?? 0 }} 赞</span>
                      <span>·</span>
                      <span>{{ rowTime(b.publishTime) }}</span>
                    </div>
                  </div>
                  <div class="uh__row-actions">
                    <button class="uh__row-btn" type="button" title="查看" @click="router.push(`/blog/${b.blogId}`)">
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
                <KhPagination
                  v-if="blogState.total > pageSize"
                  v-model:current="blogState.pageNum"
                  v-model:page-size="pageSize"
                  :total="blogState.total"
                  :page-sizes="PAGE_SIZES"
                  @change="(p: number, sz: number) => onPageChange('BLOG', p, sz)"
                />
              </div>

              <!-- 文档（横条行，对齐个人中心「我的文章」行） -->
              <div v-else-if="activeTab === 'ARTICLE'" class="uh__list">
                <div v-if="articleState.loading" class="uh__placeholder"><p>加载中…</p></div>
                <div v-else-if="!articleState.records.length" class="uh__list-empty">
                  <KhIcon name="doc" :size="40" :stroke="1.4" />
                  <p>该用户暂未发布文档</p>
                </div>
                <div v-for="a in articleState.records" :key="a.articleId" class="uh__row">
                  <div class="uh__row-main">
                    <div class="uh__row-title" @click="router.push(`/article/${a.articleId}`)">
                      <KhTag size="sm" type="accent">文档</KhTag> {{ a.title }}
                    </div>
                    <div class="uh__row-meta">
                      <KhTag v-if="a.level" size="sm" :type="viewLevelTagType[a.level as 1|2|3] ?? 'neutral'">{{ getViewLevelLabel(a.level) }}</KhTag>
                      <span>{{ a.chapterCount ?? 0 }} 章</span>
                      <span>·</span>
                      <span>{{ a.viewCount ?? 0 }} 阅读</span>
                      <span>·</span>
                      <span>{{ rowTime(a.publishTime) }}</span>
                    </div>
                  </div>
                  <div class="uh__row-actions">
                    <button class="uh__row-btn" type="button" title="查看" @click="router.push(`/article/${a.articleId}`)">
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
                <KhPagination
                  v-if="articleState.total > pageSize"
                  v-model:current="articleState.pageNum"
                  v-model:page-size="pageSize"
                  :total="articleState.total"
                  :page-sizes="PAGE_SIZES"
                  @change="(p: number, sz: number) => onPageChange('ARTICLE', p, sz)"
                />
              </div>

              <!-- 项目（横条行，对齐个人中心「我的项目」行） -->
              <div v-else-if="activeTab === 'PROJECT'" class="uh__list">
                <div v-if="projectState.loading" class="uh__placeholder"><p>加载中…</p></div>
                <div v-else-if="!projectState.records.length" class="uh__list-empty">
                  <KhIcon name="project" :size="40" :stroke="1.4" />
                  <p>该用户暂未发布项目</p>
                </div>
                <div v-for="p in projectState.records" :key="p.projectId" class="uh__row">
                  <div class="uh__row-main">
                    <div class="uh__row-title" @click="router.push(`/project/${p.projectId}`)">
                      <KhTag size="sm" type="primary">{{ projectTypeLabel[p.type ?? ''] ?? p.type ?? '项目' }}</KhTag> {{ p.title }}
                    </div>
                    <div class="uh__row-meta">
                      <KhTag v-if="p.level" size="sm" :type="viewLevelTagType[p.level as 1|2|3] ?? 'neutral'">{{ getViewLevelLabel(p.level) }}</KhTag>
                      <span>{{ p.viewCount ?? 0 }} 阅读</span>
                      <span>·</span>
                      <span>{{ p.downloadCount ?? 0 }} 下载</span>
                      <span>·</span>
                      <span>{{ rowTime(p.publishTime) }}</span>
                    </div>
                  </div>
                  <div class="uh__row-actions">
                    <button class="uh__row-btn" type="button" title="查看" @click="router.push(`/project/${p.projectId}`)">
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
                <KhPagination
                  v-if="projectState.total > pageSize"
                  v-model:current="projectState.pageNum"
                  v-model:page-size="pageSize"
                  :total="projectState.total"
                  :page-sizes="PAGE_SIZES"
                  @change="(p: number, sz: number) => onPageChange('PROJECT', p, sz)"
                />
              </div>

              <!-- 资源（横条行，对齐个人中心「我的资源」行） -->
              <div v-else class="uh__list">
                <div v-if="resourceState.loading" class="uh__placeholder"><p>加载中…</p></div>
                <div v-else-if="!resourceState.records.length" class="uh__list-empty">
                  <KhIcon name="resource" :size="40" :stroke="1.4" />
                  <p>该用户暂未发布资源</p>
                </div>
                <div v-for="r in resourceState.records" :key="r.resourceId" class="uh__row">
                  <div class="uh__row-main">
                    <div class="uh__row-title" @click="router.push(`/resource/${r.resourceId}`)">
                      <KhTag size="sm" type="accent">{{ r.categoryName ?? '其他' }}</KhTag> {{ r.title }}
                    </div>
                    <div class="uh__row-meta">
                      <KhTag size="sm" type="info">{{ r.resourceType === 'LINK' ? '链接' : '文件' }}</KhTag>
                      <template v-if="r.resourceType === 'LINK'">
                        <span>{{ r.viewCount ?? 0 }} 浏览</span>
                      </template>
                      <template v-else>
                        <span>{{ r.downloadCount ?? 0 }} 下载</span>
                      </template>
                      <span>·</span>
                      <span>{{ rowTime(r.publishTime) }}</span>
                    </div>
                  </div>
                  <div class="uh__row-actions">
                    <button class="uh__row-btn" type="button" title="查看" @click="router.push(`/resource/${r.resourceId}`)">
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
                <KhPagination
                  v-if="resourceState.total > pageSize"
                  v-model:current="resourceState.pageNum"
                  v-model:page-size="pageSize"
                  :total="resourceState.total"
                  :page-sizes="PAGE_SIZES"
                  @change="(p: number, sz: number) => onPageChange('RESOURCE', p, sz)"
                />
              </div>
            </div>
          </KhCard>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.uh {
  padding-top: var(--kh-space-8);
  padding-bottom: var(--kh-space-12);
  background: var(--kh-bg);
  min-height: 60vh;
}
.uh__body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--kh-space-6);
  align-items: start;
}

/* —— 整页加载/错误降级（跨两栏，居中） —— */
.uh__fullstate {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-16) 0;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.uh__fullstate h2 {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
}

/* —— 左侧用户卡 —— */
.uh__side {
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.uh__card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.uh__head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.uh__info {
  flex: 1;
  min-width: 0;
}
.uh__name-row {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  flex-wrap: wrap;
}
.uh__name {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
}
.uh__username {
  font-family: var(--kh-font-mono);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}
.uh__bio {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  line-height: 1.7;
  padding-top: var(--kh-space-3);
  border-top: 1px solid var(--kh-border-soft);
  margin: 0;
}
.uh__joined {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kh-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}

/* 创作统计前 4 项（2×2 网格，点击切 tab，对齐个人中心 stat 样式） */
.uh__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kh-space-3) var(--kh-space-4);
  padding-top: var(--kh-space-4);
  border-top: 1px solid var(--kh-border-soft);
}
.uh__stat {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  min-width: 0;
  padding: 6px;
  margin: -6px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.uh__stat:hover {
  background: var(--kh-surface-muted);
}
.uh__stat.is-active {
  background: var(--kh-primary-soft);
}
.uh__stat-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--kh-radius);
  display: grid;
  place-items: center;
  flex: none;
}
.uh__stat-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
  color: var(--kh-text);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.uh__stat-label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
}

/* —— 右侧 Tab 区 —— */
.uh__main {
  min-width: 0;
}
.uh__tabs-card {
  overflow: hidden;
}
.uh__tabs {
  display: flex;
  gap: 2px;
  padding: var(--kh-space-3) var(--kh-space-3) 0;
  border-bottom: 1px solid var(--kh-border-soft);
  overflow-x: auto;
}
.uh__tab {
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
.uh__tab:hover {
  color: var(--kh-text);
}
.uh__tab.is-active {
  color: var(--kh-primary);
  border-bottom-color: var(--kh-primary);
  font-weight: 600;
}
.uh__tab-count {
  padding: 1px 7px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
  font-size: 11px;
  font-weight: 500;
}
.uh__tab.is-active .uh__tab-count {
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}

.uh__tab-content {
  padding: 0 var(--kh-space-5);
}
.uh__tab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kh-space-5) 0 var(--kh-space-4);
}
.uh__tab-title {
  font-size: var(--kh-font-size-lg);
  font-weight: 700;
}

/* —— 作品行（对齐个人中心 .profile__row 横条样式） —— */
.uh__list {
  display: flex;
  flex-direction: column;
}
.uh__list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-10) 0;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.uh__list-empty p {
  font-size: var(--kh-font-size-sm);
}
.uh__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  padding: var(--kh-space-4) var(--kh-space-2);
  border-bottom: 1px solid var(--kh-border-soft);
  transition: background var(--kh-transition-fast);
}
.uh__row:hover {
  background: var(--kh-surface-muted);
}
.uh__row:last-of-type {
  border-bottom: none;
}
.uh__row-main {
  min-width: 0;
  flex: 1;
}
.uh__row-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kh-font-size-md);
  font-weight: 500;
  color: var(--kh-text);
  cursor: pointer;
}
.uh__row-title:hover {
  color: var(--kh-primary);
}
.uh__row-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  flex-wrap: wrap;
}
.uh__row-actions {
  display: flex;
  gap: 6px;
  flex: none;
}
.uh__row-btn {
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
.uh__row-btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.uh__placeholder {
  text-align: center;
  color: var(--kh-text-tertiary);
  padding: var(--kh-space-10);
}

@media (max-width: 900px) {
  .uh__body {
    grid-template-columns: 1fr;
  }
  .uh__side {
    position: static;
  }
}
</style>
