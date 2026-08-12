<!--
  全局搜索结果页 /search?keyword=
  ------------------------------------------------------------------
  首页 Hero 与顶栏搜索框的落脚页。后端无统一跨内容搜索接口，前端并发放 4 个 /portal/*/search
  按 keyword 检索，Tab 分内容类聚合：
    - 综合：四类各取首页少量预览（并发），点单类切到该类完整分页视图；
    - 博客/项目/文档/资源：页内分页器驱动对应 search 接口 pageNum，不跳走、懒加载、独立缓存分页态。
  结果卡复用既有组件（BlogRow/ProjectCard/ArticleCard/ResourceCard），不引 mock。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import BlogRow from '@/components/blog/BlogRow.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import ArticleCard from '@/components/article/ArticleCard.vue'
import ResourceCard from '@/components/resource/ResourceCard.vue'
import { searchBlogsApi } from '@/api/knowhub/blog'
import { searchProjectsApi } from '@/api/knowhub/project-portal'
import { searchArticlesApi } from '@/api/knowhub/article'
import { searchResourcesApi } from '@/api/knowhub/resource-portal'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'
import type { ProjectPortalRecord } from '@/types/api/knowhub/project-portal'
import type { ArticlePortalRecord } from '@/types/api/knowhub/article'
import type { ResourcePortalRecord } from '@/types/api/knowhub/resource'

type TabKey = 'ALL' | 'BLOG' | 'PROJECT' | 'DOC' | 'RESOURCE'
const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: 'ALL', label: '综合', icon: 'search' },
  { key: 'BLOG', label: '博客', icon: 'blog' },
  { key: 'PROJECT', label: '项目', icon: 'project' },
  { key: 'DOC', label: '文档', icon: 'doc' },
  { key: 'RESOURCE', label: '资源', icon: 'resource' },
]
const activeTab = ref<TabKey>('ALL')

const route = useRoute()
const router = useRouter()
const keyword = ref(String(route.query.keyword ?? ''))
const inputValue = ref(keyword.value)

const PREVIEW_SIZE = 3 /** 综合页每类预览条数 */
const PAGE_SIZE = 10 /** 单类分页每页条数 */

/** 四类各自的数据/分页/加载态。综合页复用单类首页结果，避免重复请求。 */
type CategoryState<T> = {
  records: T[]
  total: number
  pageNum: number
  loaded: boolean
  loading: boolean
}
const blogState = ref<CategoryState<BlogPortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const projectState = ref<CategoryState<ProjectPortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const docState = ref<CategoryState<ArticlePortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })
const resourceState = ref<CategoryState<ResourcePortalRecord>>({ records: [], total: 0, pageNum: 1, loaded: false, loading: false })

/** 综合：拉四类各 PREVIEW_SIZE 条预览（并发，单类失败不阻塞） */
const fetchAllPreview = async () => {
  resetAll()
  const [b, p, d, r] = await Promise.allSettled([
    searchBlogsApi({ keyword: keyword.value, pageNum: 1, pageSize: PREVIEW_SIZE, sort: 'RELEVANCE' }),
    searchProjectsApi({ keyword: keyword.value, pageNum: 1, pageSize: PREVIEW_SIZE, sort: 'HOT' }),
    searchArticlesApi({ keyword: keyword.value, pageNum: 1, pageSize: PREVIEW_SIZE, sort: 'RELEVANCE' }),
    searchResourcesApi({ keyword: keyword.value, pageNum: 1, pageSize: PREVIEW_SIZE, sort: 'RELEVANCE' }),
  ])
  if (b.status === 'fulfilled') {
    blogState.value = { records: b.value.records ?? [], total: b.value.total ?? 0, pageNum: 1, loaded: true, loading: false }
  }
  if (p.status === 'fulfilled') {
    projectState.value = { records: p.value.records ?? [], total: p.value.total ?? 0, pageNum: 1, loaded: true, loading: false }
  }
  if (d.status === 'fulfilled') {
    docState.value = { records: d.value.records ?? [], total: d.value.total ?? 0, pageNum: 1, loaded: true, loading: false }
  }
  if (r.status === 'fulfilled') {
    resourceState.value = { records: r.value.records ?? [], total: r.value.total ?? 0, pageNum: 1, loaded: true, loading: false }
  }
}

/** 单类分页拉取（首次切到该 tab 时按当前页码加载，翻页时复用） */
const fetchBlogPage = async () => {
  blogState.value.loading = true
  try {
    const page = await searchBlogsApi({ keyword: keyword.value, pageNum: blogState.value.pageNum, pageSize: PAGE_SIZE, sort: 'RELEVANCE' })
    blogState.value.records = page.records ?? []
    blogState.value.total = page.total ?? 0
    blogState.value.loaded = true
  } finally {
    blogState.value.loading = false
  }
}
const fetchProjectPage = async () => {
  projectState.value.loading = true
  try {
    const page = await searchProjectsApi({ keyword: keyword.value, pageNum: projectState.value.pageNum, pageSize: PAGE_SIZE, sort: 'HOT' })
    projectState.value.records = page.records ?? []
    projectState.value.total = page.total ?? 0
    projectState.value.loaded = true
  } finally {
    projectState.value.loading = false
  }
}
const fetchDocPage = async () => {
  docState.value.loading = true
  try {
    const page = await searchArticlesApi({ keyword: keyword.value, pageNum: docState.value.pageNum, pageSize: PAGE_SIZE, sort: 'RELEVANCE' })
    docState.value.records = page.records ?? []
    docState.value.total = page.total ?? 0
    docState.value.loaded = true
  } finally {
    docState.value.loading = false
  }
}
const fetchResourcePage = async () => {
  resourceState.value.loading = true
  try {
    const page = await searchResourcesApi({ keyword: keyword.value, pageNum: resourceState.value.pageNum, pageSize: PAGE_SIZE, sort: 'RELEVANCE' })
    resourceState.value.records = page.records ?? []
    resourceState.value.total = page.total ?? 0
    resourceState.value.loaded = true
  } finally {
    resourceState.value.loading = false
  }
}

const resetAll = () => {
  blogState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  projectState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  docState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
  resourceState.value = { records: [], total: 0, pageNum: 1, loaded: false, loading: false }
}

/** 切 tab：综合页永远预览态，单类 tab 首次加载该类首页分页（不重复请求已有数据） */
const changeTab = (key: TabKey) => {
  activeTab.value = key
  if (key === 'ALL') return
  if (key === 'BLOG' && !blogState.value.loaded) void fetchBlogPage()
  else if (key === 'PROJECT' && !projectState.value.loaded) void fetchProjectPage()
  else if (key === 'DOC' && !docState.value.loaded) void fetchDocPage()
  else if (key === 'RESOURCE' && !resourceState.value.loaded) void fetchResourcePage()
}

/** 翻页：切到该 tab 的对应页码 */
const changePage = async (key: TabKey, p: number) => {
  if (key === 'BLOG') { blogState.value.pageNum = p; await fetchBlogPage() }
  else if (key === 'PROJECT') { projectState.value.pageNum = p; await fetchProjectPage() }
  else if (key === 'DOC') { docState.value.pageNum = p; await fetchDocPage() }
  else if (key === 'RESOURCE') { resourceState.value.pageNum = p; await fetchResourcePage() }
}

/** 顶部搜索框回车/点按钮：带新 keyword 跳路由（触发 query 变化重拉） */
const submitSearch = () => {
  const kw = inputValue.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw } })
}

const hasKeyword = computed(() => keyword.value.trim().length > 0)
const anyResult = computed(() =>
  blogState.value.records.length + projectState.value.records.length + docState.value.records.length + resourceState.value.records.length > 0,
)

/** 监听路由 keyword 变化（含进入页与站内二次搜索）：重置 + 拉综合预览 */
watch(
  () => route.query.keyword,
  (kw) => {
    keyword.value = String(kw ?? '')
    inputValue.value = keyword.value
    if (hasKeyword.value) void fetchAllPreview()
    activeTab.value = 'ALL'
  },
  { immediate: true },
)
</script>

<template>
  <div class="search">
    <!-- 顶部搜索区 -->
    <section class="search__hero">
      <div class="kh-container kh-container--wide">
        <div class="search__bar">
          <el-icon class="search__bar-icon"><Search /></el-icon>
          <input v-model="inputValue" class="search__bar-input" placeholder="搜索博客、项目、文档、资源…" @keyup.enter="submitSearch" />
          <button class="search__bar-btn" type="button" @click="submitSearch">搜索</button>
        </div>
        <p v-if="hasKeyword" class="search__hint">
          搜索「<span class="search__hint-kw">{{ keyword }}</span>」的相关结果
        </p>
      </div>
    </section>

    <section class="kh-container kh-container--wide search__body">
      <!-- Tab -->
      <div class="search__tabs">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="search__tab"
          :class="{ 'is-active': activeTab === t.key }"
          type="button"
          @click="changeTab(t.key)"
        >
          <KhIcon :name="t.icon" :size="15" />
          {{ t.label }}
        </button>
      </div>

      <!-- 无关键词提示 -->
      <KhCard v-if="!hasKeyword" padding="lg" class="search__empty">
        <KhIcon name="search" :size="40" :stroke="1.4" />
        <p>请输入要搜索的内容</p>
      </KhCard>

      <!-- 综合 Tab：四类预览 + 查看更多切到单类 tab -->
      <template v-else-if="activeTab === 'ALL'">
        <div v-if="anyResult" class="search__overview">
          <div v-if="blogState.records.length" class="search__group">
            <div class="search__group-head">
              <KhIcon name="blog" :size="16" /> <h3>博客</h3>
              <span class="search__group-count">共 {{ blogState.total }} 条</span>
              <button v-if="blogState.total > blogState.records.length" class="search__group-more" type="button" @click="changeTab('BLOG')">
                查看全部 <el-icon><Search /></el-icon>
              </button>
            </div>
            <div class="search__list"><BlogRow v-for="b in blogState.records" :key="b.blogId" :blog="b" /></div>
          </div>

          <div v-if="projectState.records.length" class="search__group">
            <div class="search__group-head">
              <KhIcon name="project" :size="16" /> <h3>项目</h3>
              <span class="search__group-count">共 {{ projectState.total }} 条</span>
              <button v-if="projectState.total > projectState.records.length" class="search__group-more" type="button" @click="changeTab('PROJECT')">
                查看全部 <el-icon><Search /></el-icon>
              </button>
            </div>
            <div class="search__grid-2"><ProjectCard v-for="p in projectState.records" :key="p.projectId" :project="p" /></div>
          </div>

          <div v-if="docState.records.length" class="search__group">
            <div class="search__group-head">
              <KhIcon name="doc" :size="16" /> <h3>文档</h3>
              <span class="search__group-count">共 {{ docState.total }} 条</span>
              <button v-if="docState.total > docState.records.length" class="search__group-more" type="button" @click="changeTab('DOC')">
                查看全部 <el-icon><Search /></el-icon>
              </button>
            </div>
            <div class="search__grid-3"><ArticleCard v-for="d in docState.records" :key="d.articleId" :doc="d" /></div>
          </div>

          <div v-if="resourceState.records.length" class="search__group">
            <div class="search__group-head">
              <KhIcon name="resource" :size="16" /> <h3>资源</h3>
              <span class="search__group-count">共 {{ resourceState.total }} 条</span>
              <button v-if="resourceState.total > resourceState.records.length" class="search__group-more" type="button" @click="changeTab('RESOURCE')">
                查看全部 <el-icon><Search /></el-icon>
              </button>
            </div>
            <div class="search__grid-3"><ResourceCard v-for="r in resourceState.records" :key="r.resourceId" :resource="r" /></div>
          </div>
        </div>
        <KhCard v-else padding="lg" class="search__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>未搜到相关内容</p>
        </KhCard>
      </template>

      <!-- 单类 Tab：完整分页 -->
      <template v-else>
        <!-- 博客 -->
        <template v-if="activeTab === 'BLOG'">
          <div v-if="blogState.records.length" class="search__list"><BlogRow v-for="b in blogState.records" :key="b.blogId" :blog="b" /></div>
          <KhCard v-else-if="!blogState.loading" padding="lg" class="search__empty"><KhIcon name="blog" :size="40" :stroke="1.4" /><p>未搜到相关博客</p></KhCard>
          <el-pagination
            v-if="blogState.total > PAGE_SIZE"
            class="search__pager"
            background
            layout="prev, pager, next"
            :total="blogState.total"
            :page-size="PAGE_SIZE"
            :current-page="blogState.pageNum"
            @current-change="(p: number) => changePage('BLOG', p)"
          />
        </template>

        <!-- 项目 -->
        <template v-if="activeTab === 'PROJECT'">
          <div v-if="projectState.records.length" class="search__grid-2"><ProjectCard v-for="p in projectState.records" :key="p.projectId" :project="p" /></div>
          <KhCard v-else-if="!projectState.loading" padding="lg" class="search__empty"><KhIcon name="project" :size="40" :stroke="1.4" /><p>未搜到相关项目</p></KhCard>
          <el-pagination
            v-if="projectState.total > PAGE_SIZE"
            class="search__pager"
            background
            layout="prev, pager, next"
            :total="projectState.total"
            :page-size="PAGE_SIZE"
            :current-page="projectState.pageNum"
            @current-change="(p: number) => changePage('PROJECT', p)"
          />
        </template>

        <!-- 文档 -->
        <template v-if="activeTab === 'DOC'">
          <div v-if="docState.records.length" class="search__grid-3"><ArticleCard v-for="d in docState.records" :key="d.articleId" :doc="d" /></div>
          <KhCard v-else-if="!docState.loading" padding="lg" class="search__empty"><KhIcon name="doc" :size="40" :stroke="1.4" /><p>未搜到相关文档</p></KhCard>
          <el-pagination
            v-if="docState.total > PAGE_SIZE"
            class="search__pager"
            background
            layout="prev, pager, next"
            :total="docState.total"
            :page-size="PAGE_SIZE"
            :current-page="docState.pageNum"
            @current-change="(p: number) => changePage('DOC', p)"
          />
        </template>

        <!-- 资源 -->
        <template v-if="activeTab === 'RESOURCE'">
          <div v-if="resourceState.records.length" class="search__grid-3"><ResourceCard v-for="r in resourceState.records" :key="r.resourceId" :resource="r" /></div>
          <KhCard v-else-if="!resourceState.loading" padding="lg" class="search__empty"><KhIcon name="resource" :size="40" :stroke="1.4" /><p>未搜到相关资源</p></KhCard>
          <el-pagination
            v-if="resourceState.total > PAGE_SIZE"
            class="search__pager"
            background
            layout="prev, pager, next"
            :total="resourceState.total"
            :page-size="PAGE_SIZE"
            :current-page="resourceState.pageNum"
            @current-change="(p: number) => changePage('RESOURCE', p)"
          />
        </template>
      </template>
    </section>
  </div>
</template>

<style scoped>
.search__hero {
  padding: var(--kh-space-10) 0 var(--kh-space-6);
  background: var(--kh-gradient-hero);
}
.search__bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  max-width: 720px;
  height: 56px;
  padding: 0 6px 0 var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  transition: border-color var(--kh-transition), box-shadow var(--kh-transition);
}
.search__bar:focus-within {
  border-color: var(--kh-primary-border);
  box-shadow: var(--kh-focus-ring);
}
.search__bar-icon {
  color: var(--kh-text-tertiary);
  font-size: 20px;
}
.search__bar-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
  color: var(--kh-text);
}
.search__bar-btn {
  height: 44px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
.search__hint {
  margin-top: var(--kh-space-4);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.search__hint-kw {
  color: var(--kh-primary);
  font-weight: 600;
}

.search__body {
  padding-bottom: var(--kh-space-12);
}
.search__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kh-space-2);
  margin-bottom: var(--kh-space-6);
}
.search__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.search__tab:hover {
  border-color: var(--kh-border-strong);
  color: var(--kh-text);
}
.search__tab.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}

.search__overview {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-8);
}
.search__group-head {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding-bottom: var(--kh-space-3);
  border-bottom: 1px solid var(--kh-border-soft);
  margin-bottom: var(--kh-space-4);
  color: var(--kh-text-secondary);
}
.search__group-head h3 {
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-text);
}
.search__group-count {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}
.search__group-more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: gap var(--kh-transition-fast);
}
.search__group-more:hover {
  gap: 8px;
}

.search__list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.search__grid-2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--kh-space-5);
}
.search__grid-3 {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--kh-space-4);
}

.search__pager {
  margin-top: var(--kh-space-6);
  justify-content: center;
}
.search__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}

@media (max-width: 768px) {
  .search__grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>