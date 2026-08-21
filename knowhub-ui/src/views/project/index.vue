<!--
  项目展示 /projects
  ------------------------------------------------------------------
  照搬博客导航/文档学习范式：hero(标题+搜索) + body 主列表(1fr) | 侧栏(300px)。
  主列表始终走 /portal/project/search（PageHelper 真分页，返回 total/pages），即使无关键词/无类型筛选也走 search，
  保证分页真实可用；/portal/project/recommend 只用于侧栏"热门项目榜"（裸 LIMIT feed，不当主列表）。
  筛选：类型(比赛/练习/运维/全部) + 排序(热度/最新)；不暴露等级筛选（后端按 userViewLevel 自动收窄，前台不向访客暴露内部权限态）。
  搜索：后端 keyword LIKE title OR summary（一词同时匹配两列）。
  统计卡放侧栏，用 list.total 派生（类型分布无聚合接口，列表项派生同 articles 页口径，翻页下限可接受）。
  权限：未登录默认 L1（后端 resolveUserViewLevel Math.max(1, view) 兜底），L2/L3 永不下发前台。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import { searchProjectsApi, recommendProjectsApi } from '@/api/knowhub/project-portal'
import type { ProjectPortalRecord, ProjectPortalSearchQuery } from '@/types/api/knowhub/project-portal'
import type { NormalizedPageResult } from '@/types/api/common'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 类型筛选（状态不暴露：未发布一律不下发前台） */
const typeFilter = ref<'ALL' | 'COMPETITION' | 'PRACTICE' | 'OPS'>('ALL')
const typeOptions: { key: typeof typeFilter.value; label: string; toApi: string | undefined }[] = [
  { key: 'ALL', label: '全部', toApi: undefined },
  { key: 'COMPETITION', label: '比赛', toApi: 'COMPETITION' },
  { key: 'PRACTICE', label: '练习', toApi: 'PRACTICE' },
  { key: 'OPS', label: '运维', toApi: 'OPS' },
]

/** 排序：HOT 热度（默认）/ LATEST 最新 —— 与后端 searchProjects 的 sort 分支对齐 */
const sortKey = ref<'HOT' | 'LATEST'>('HOT')
const sortOptions: { key: typeof sortKey.value; label: string; toApi: ProjectPortalSearchQuery['sort'] }[] = [
  { key: 'HOT', label: '热度', toApi: 'HOT' },
  { key: 'LATEST', label: '最新', toApi: 'LATEST' },
]

const keyword = ref('')
/**
 * 已提交的搜索词：只在点击搜索按钮 / 回车时从 keyword 同步过来，避免输入实时触发。
 * fetchList 据此传后端；keyword 仅作输入框 v-model，不直接参与请求。
 */
const submittedKeyword = ref('')

/** 主项目列表（真实接口分页） */
const list = ref<ProjectPortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(9)
const loading = ref(false)

/** 侧栏热门项目榜（/portal/project/recommend 兜底全局热门，取 6 条） */
const hotProjects = ref<ProjectPortalRecord[]>([])

/** 拉取主列表：始终走 /search 真分页（关键词空也走，后端 keyword 为空不过滤） */
const fetchList = async () => {
  loading.value = true
  try {
    const sortApi = sortOptions.find((o) => o.key === sortKey.value)?.toApi ?? 'HOT'
    const typeApi = typeOptions.find((o) => o.key === typeFilter.value)?.toApi
    const query: ProjectPortalSearchQuery = {
      keyword: submittedKeyword.value || undefined,
      type: typeApi,
      sort: sortApi,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const res: NormalizedPageResult<ProjectPortalRecord> = await searchProjectsApi(query)
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

/** 拉取侧栏热门项目榜（推荐 feed，独立于主列表分页） */
const fetchHot = async () => {
  try {
    const res = await recommendProjectsApi(6)
    hotProjects.value = res.data ?? []
  } catch {
    hotProjects.value = []
  }
}

/** 翻页 */
const onPageChange = (p: number, sz: number) => {
  pageNum.value = p
  pageSize.value = sz
  void fetchList()
}

/** 类型/排序变化时回到第一页重新拉取（keyword 不入 watch——搜索只在点按钮/回车时由 handleSearch 触发，避免输入实时拉） */
watch([typeFilter, sortKey], () => {
  pageNum.value = 1
  void fetchList()
})

/** 搜索按钮 / 回车：把输入框 keyword 提交到 submittedKeyword，回到第一页重拉 */
const handleSearch = () => {
  submittedKeyword.value = keyword.value.trim()
  pageNum.value = 1
  void fetchList()
}

/**
 * 侧栏统计卡：只显"可见项目"真实总数（search 返回 total）。
 * 原比赛/练习/运维三行用 list.filter(p=>p.type===...).length 派生当前页计数，翻页会变且 >pageSize 漏计
 * （无聚合接口拿各类型真实总数）——移除派生行避免错误数字；类型分布由顶部类型筛选按钮承载
 * （切到某类型后主列表"共 N 个项目"显该类型真实 total，按钮本身不带 count 无 bug）。
 */
const stats = computed(() => [
  { label: '可见项目', value: total.value, icon: 'project' as const, tone: 'var(--kh-primary)' },
])

onMounted(() => {
  void fetchHot()
  void fetchList()
})
</script>

<template>
  <div class="projects">
    <section class="projects__hero">
      <div class="kh-container kh-container--wide">
        <div class="projects__hero-head">
          <div>
            <h1 class="projects__title">项目展示</h1>
            <p class="projects__subtitle">实验室当前最活跃的项目都在这里 · 含比赛/练习/运维三类</p>
          </div>
          <div class="projects__hero-actions">
            <div class="projects__search">
              <el-icon class="projects__search-icon"><Search /></el-icon>
              <input
                v-model="keyword"
                class="projects__search-input"
                placeholder="搜索项目标题或简介…"
                @keyup.enter="handleSearch"
              />
              <button class="projects__search-btn" type="button" @click="handleSearch">搜索</button>
            </div>
            <!-- 登录态导流：公开展示页只列 PUBLISHED，自己的草稿/审核中项目在"我的项目" —— 轻量链接导流，不 inline 拉避免展示页变管理页 -->
            <RouterLink v-if="userStore.isAuthenticated" to="/profile?tab=project" class="projects__mine">
              我的项目 →
            </RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- 主体：左侧列表 + 右侧栏 -->
    <section class="kh-container kh-container--wide projects__body">
      <div class="projects__main">
        <div class="projects__toolbar">
          <!-- 左：类型筛选 pills -->
          <div class="projects__filter">
            <button
              v-for="o in typeOptions"
              :key="o.key"
              class="projects__filter-btn"
              :class="{ 'is-active': typeFilter === o.key }"
              type="button"
              @click="typeFilter = o.key"
            >{{ o.label }}</button>
          </div>
          <!-- 右：数量 + 排序 -->
          <div class="projects__sortwrap">
            <span class="projects__count">共 <b>{{ total }}</b> 个项目</span>
            <div class="projects__sort">
              <button
                v-for="o in sortOptions"
                :key="o.key"
                class="projects__sort-btn"
                :class="{ 'is-active': sortKey === o.key }"
                type="button"
                @click="sortKey = o.key"
              >{{ o.label }}</button>
            </div>
          </div>
        </div>

        <div v-if="loading" class="projects__loading">
          <KhIcon name="trending" :size="28" /> 加载中…
        </div>
        <div v-else-if="list.length" class="projects__grid">
          <ProjectCard v-for="p in list" :key="p.projectId" :project="p" />
        </div>
        <KhCard v-else padding="lg" class="projects__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的项目，换个关键词或类型试试</p>
        </KhCard>

        <div v-if="list.length" class="projects__pager">
          <KhPagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total" @change="onPageChange" />
        </div>
      </div>

      <!-- 侧栏：统计卡 + 热门项目榜 -->
      <aside class="projects__aside">
        <KhCard padding="md" class="projects__stat">
          <div class="projects__stat-title">项目统计</div>
          <div class="projects__stat-list">
            <div v-for="s in stats" :key="s.label" class="projects__stat-row">
              <span class="projects__stat-icon" :style="{ color: s.tone, background: `${s.tone}1a` }">
                <KhIcon :name="s.icon" :size="16" />
              </span>
              <span class="projects__stat-label">{{ s.label }}</span>
              <b class="projects__stat-value">{{ s.value }}</b>
            </div>
          </div>
        </KhCard>

        <KhCard padding="md" class="projects__panel">
          <KhSectionTitle title="热门项目" subtitle="推荐项目" />
          <ul v-if="hotProjects.length" class="projects__hot">
            <li
              v-for="(p, i) in hotProjects"
              :key="p.projectId"
              class="projects__hot-item"
              @click="$router.push(`/project/${p.projectId}`)"
            >
              <span class="projects__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="projects__hot-text">
                <div class="projects__hot-title kh-line-clamp-2">{{ p.title }}</div>
                <div class="projects__hot-meta">{{ p.viewCount ?? 0 }} 浏览 · {{ p.downloadCount ?? 0 }} 下载</div>
              </div>
            </li>
          </ul>
          <div v-else class="projects__panel-empty">
            <KhIcon name="project" :size="28" :stroke="1.4" />
            <p>暂无热门项目</p>
          </div>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.projects__hero {
  padding: var(--kh-space-12) 0 var(--kh-space-10);
  background: var(--kh-gradient-hero);
}
.projects__hero-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--kh-space-6);
  flex-wrap: wrap;
}
.projects__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.projects__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.projects__hero-actions {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex-wrap: wrap;
}
.projects__search {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  height: 48px;
  padding: 0 6px 0 var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  min-width: 320px;
}
.projects__search-icon {
  color: var(--kh-text-tertiary);
}
.projects__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
}
.projects__search-btn {
  height: 38px;
  padding: 0 var(--kh-space-5);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary);
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
}
/* 登录态"我的项目"导流链接（轻量，跳个人中心 Tab） */
.projects__mine {
  display: inline-flex;
  align-items: center;
  padding: 0 var(--kh-space-4);
  height: 44px;
  border: 1px solid var(--kh-primary-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  white-space: nowrap;
  transition: background var(--kh-transition-fast);
}
.projects__mine:hover {
  background: var(--kh-primary);
  color: var(--kh-surface);
}

.projects__body {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: start;
}
.projects__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.projects__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--kh-space-5);
  flex-wrap: wrap;
  gap: var(--kh-space-3);
}
.projects__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.projects__filter-btn {
  padding: 6px 16px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.projects__filter-btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.projects__filter-btn.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.projects__sortwrap {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.projects__count {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.projects__count b {
  color: var(--kh-primary);
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
}
.projects__sort {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
}
.projects__sort-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius-pill);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.projects__sort-btn.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}

.projects__loading {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  justify-content: center;
  color: var(--kh-text-tertiary);
  padding: var(--kh-space-10) 0;
}
.projects__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--kh-space-5);
}
.projects__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-height: 320px;
}
.projects__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}

.projects__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.projects__stat-title {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
  margin-bottom: var(--kh-space-3);
}
.projects__stat-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.projects__stat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--kh-border-soft);
}
.projects__stat-row:last-child {
  border-bottom: none;
}
.projects__stat-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--kh-radius-sm);
  flex: none;
}
.projects__stat-label {
  flex: 1;
  font-size: 12px;
  color: var(--kh-text-secondary);
}
.projects__stat-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
  color: var(--kh-primary);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.projects__hot {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.projects__hot-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
}
.projects__hot-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 14px;
  color: var(--kh-text-tertiary);
  width: 18px;
  flex: none;
}
.projects__hot-no.is-top {
  color: var(--kh-warm);
}
.projects__hot-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.5;
}
.projects__hot-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
}

.projects__panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-6) 0;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.projects__panel-empty p {
  font-size: 12px;
}

@media (max-width: 1024px) {
  .projects__body {
    grid-template-columns: 1fr;
  }
  .projects__aside {
    position: static;
  }
}
</style>