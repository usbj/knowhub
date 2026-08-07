<!--
  项目展示 /projects
  ------------------------------------------------------------------
  顶部统计卡（总数/比赛/练习/运维）+ 项目类型筛选 + 关键字搜索 + 项目卡片网格。
  数据源：后端 /portal/project/search（搜索时）+ /portal/project/recommend（默认 feed，登录用户排除已浏览）。
  权限：未登录默认 L1（后端 resolveUserViewLevel Math.max(1, view) 兜底），L2/L3 项目永不下发前台。
  项目无标签体系，故无标签云 / 分类热度栏（与笔记导航差异点）。
  统计卡数量是基于后端列表分页 total + type 额外拉取（轻量：每类各查一次取 total）——首版采用本地当前页推导，避免多次重请求。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import { searchProjectsApi, recommendProjectsApi } from '@/api/knowhub/project-portal'
import type { ProjectPortalRecord, ProjectPortalSearchQuery } from '@/types/api/knowhub/project-portal'
import type { NormalizedPageResult } from '@/types/api/common'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 类型筛选（状态不暴露：未发布一律不下发前台） */
const typeFilter = ref<'ALL' | 'COMPETITION' | 'PRACTICE' | 'OPS'>('ALL')
const sortKey = ref<'HOT' | 'LATEST'>('HOT')
const keyword = ref('')

const list = ref<ProjectPortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 9
const loading = ref(false)

/** 统计：基于当前 list（首版本地推导，召回不足或筛 type 时非全量，可接受——后端无聚合统计接口）
 *  注：后端列表只回 PUBLISHED 且 level<=userViewLevel 的（分级开关关时只 L1），统计为"当前可见项目"。 */
const stats = computed(() => [
  { label: '可见项目', value: list.value.length, icon: 'project' as const, tone: 'var(--kh-primary)' },
  { label: '练习项目', value: list.value.filter((p) => p.type === 'PRACTICE').length, icon: 'code' as const, tone: 'var(--kh-accent)' },
  { label: '比赛项目', value: list.value.filter((p) => p.type === 'COMPETITION').length, icon: 'trophy' as const, tone: 'var(--kh-warm)' },
  { label: '运维项目', value: list.value.filter((p) => p.type === 'OPS').length, icon: 'flask' as const, tone: 'var(--kh-text-tertiary)' },
])

const typeOptions: { key: typeof typeFilter.value; label: string }[] = [
  { key: 'ALL', label: '全部类型' },
  { key: 'COMPETITION', label: '比赛项目' },
  { key: 'PRACTICE', label: '练习项目' },
  { key: 'OPS', label: '运维项目' },
]

/** 拉取列表：search 走关键词/type/排序分页，recommend 走全局热门 feed（分页由 PageHelper 接管） */
const fetchList = async () => {
  loading.value = true
  try {
    const hasQuery = !!keyword.value.trim() || typeFilter.value !== 'ALL'
    if (hasQuery) {
      // 搜索走 /portal/project/search（LIKE title + type 过滤 + 排序 + 分页）
      const query: ProjectPortalSearchQuery & { pageNum: number; pageSize: number } = {
        keyword: keyword.value.trim() || undefined,
        type: typeFilter.value !== 'ALL' ? typeFilter.value : undefined,
        sort: sortKey.value,
        pageNum: pageNum.value,
        pageSize,
      }
      const res: NormalizedPageResult<ProjectPortalRecord> = await searchProjectsApi(query)
      list.value = res.records ?? []
      total.value = res.total ?? 0
    } else {
      // 无筛选用推荐 feed（登录用户排除已浏览），分页用 limit 由 size 控制
      const size = pageNum.value === 1 ? pageSize : pageSize
      const res = await recommendProjectsApi(size)
      list.value = res.data ?? []
      // recommend 走 LIMIT 无 total，前端按 list 长度兜底（不足以再翻页时弱化分页）
      total.value = list.value.length < pageSize ? (pageNum.value - 1) * pageSize + list.value.length : pageNum.value * pageSize + pageSize
    }
  } finally {
    loading.value = false
  }
}

const onPageChange = (p: number) => {
  pageNum.value = p
  void fetchList()
}

/** 搜索/标签/排序变化时回到第一页重拉 */
watch([keyword, typeFilter, sortKey], () => {
  pageNum.value = 1
  void fetchList()
})

onMounted(() => {
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
            <input v-model="keyword" class="projects__search-input" placeholder="搜索项目标题…" />
          </div>
          <!-- 登录态导流：公开展示页只列 PUBLISHED，自己的草稿/审核中项目在"我的项目" —— 轻量链接导流，不 inline 拉避免展示页变管理页 -->
          <RouterLink v-if="userStore.isAuthenticated" to="/profile?tab=project" class="projects__mine">
            我的项目 →
          </RouterLink>
        </div>
        </div>

        <!-- 统计卡 -->
        <div class="projects__stats">
          <KhCard v-for="s in stats" :key="s.label" padding="md" class="projects__stat">
            <div class="projects__stat-icon" :style="{ color: s.tone, background: `${s.tone}1a` }">
              <KhIcon :name="s.icon" :size="22" />
            </div>
            <div>
              <div class="projects__stat-value">{{ s.value }}</div>
              <div class="projects__stat-label">{{ s.label }}</div>
            </div>
          </KhCard>
        </div>
      </div>
    </section>

    <!-- 筛选 + 列表 -->
    <section class="kh-container kh-container--wide projects__body">
      <div class="projects__filter">
        <div class="projects__filter-group">
          <span class="projects__filter-label">类型</span>
          <button
            v-for="o in typeOptions"
            :key="o.key"
            class="projects__filter-btn"
            :class="{ 'is-active': typeFilter === o.key }"
            type="button"
            @click="typeFilter = o.key"
          >{{ o.label }}</button>
        </div>
        <div class="projects__sort">
          <span class="projects__filter-label">排序</span>
          <button class="projects__filter-btn" :class="{ 'is-active': sortKey === 'HOT' }" type="button" @click="sortKey = 'HOT'">热度</button>
          <button class="projects__filter-btn" :class="{ 'is-active': sortKey === 'LATEST' }" type="button" @click="sortKey = 'LATEST'">最新</button>
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
        <p>没有匹配的项目</p>
      </KhCard>

      <div class="projects__pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          background
          @current-change="onPageChange"
        />
      </div>
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
}
.projects__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.projects__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  min-width: 280px;
}
.projects__search-icon {
  color: var(--kh-text-tertiary);
}
.projects__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-sm);
}
.projects__hero-actions {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex-wrap: wrap;
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
.projects__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--kh-space-5);
  margin-top: var(--kh-space-10);
}
.projects__stat {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.projects__stat-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--kh-radius);
  flex: none;
}
.projects__stat-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
  font-variant-numeric: tabular-nums;
}
.projects__stat-label {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}

.projects__body {
  margin-top: var(--kh-space-10);
}
.projects__filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-6);
  margin-bottom: var(--kh-space-6);
  padding: var(--kh-space-4) var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
}
.projects__filter-group {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
}
.projects__filter-label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  font-weight: 600;
}
.projects__filter-btn {
  padding: 5px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
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
.projects__sort {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
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
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--kh-space-5);
}
.projects__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}
.projects__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}

@media (max-width: 768px) {
  .projects__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>