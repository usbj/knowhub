<!--
  项目展示 /projects
  ------------------------------------------------------------------
  顶部统计卡（总数/在研/比赛/归档）+ 项目类型筛选 + 项目卡片网格 + 活跃度排序。
  只展示已发布项目（未发布一律不出现），故无状态筛选。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import { projects } from '@/mock/project'

/** 类型筛选（状态不暴露：未发布一律不展示） */
const typeFilter = ref<'ALL' | 'COMPETITION' | 'PRACTICE' | 'OPS'>('ALL')
const keyword = ref('')

/** 仅已发布项目参与统计与展示 */
const published = projects.filter((p) => p.status === 'PUBLISHED')

/** 统计 */
const stats = computed(() => [
  { label: '项目总数', value: published.length, icon: 'project' as const, tone: 'var(--kh-primary)' },
  { label: '在研项目', value: published.filter((p) => p.type === 'PRACTICE').length, icon: 'code' as const, tone: 'var(--kh-accent)' },
  { label: '比赛项目', value: published.filter((p) => p.type === 'COMPETITION').length, icon: 'trophy' as const, tone: 'var(--kh-warm)' },
  { label: '运维项目', value: published.filter((p) => p.type === 'OPS').length, icon: 'flask' as const, tone: 'var(--kh-text-tertiary)' },
])

/** 过滤 + 活跃度排序（仅在已发布集合内） */
const filteredProjects = computed(() => {
  let list = published
  if (typeFilter.value !== 'ALL') list = list.filter((p) => p.type === typeFilter.value)
  if (keyword.value.trim()) {
    const k = keyword.value.trim().toLowerCase()
    list = list.filter((p) => p.title.toLowerCase().includes(k) || p.summary.toLowerCase().includes(k))
  }
  return [...list].sort((a, b) => b.downloadCount - a.downloadCount)
})

const typeOptions: { key: typeof typeFilter.value; label: string }[] = [
  { key: 'ALL', label: '全部类型' },
  { key: 'COMPETITION', label: '比赛项目' },
  { key: 'PRACTICE', label: '练习项目' },
  { key: 'OPS', label: '运维项目' },
]
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
          <div class="projects__search">
            <el-icon class="projects__search-icon"><Search /></el-icon>
            <input v-model="keyword" class="projects__search-input" placeholder="搜索项目…" />
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
        <div class="projects__sort-hint">
          <KhIcon name="trending" :size="14" /> 按下载量排序
        </div>
      </div>

      <div v-if="filteredProjects.length" class="projects__grid">
        <ProjectCard v-for="p in filteredProjects" :key="p.projectId" :project="p" />
      </div>
      <KhCard v-else padding="lg" class="projects__empty">
        <KhIcon name="search" :size="40" :stroke="1.4" />
        <p>没有匹配的项目</p>
      </KhCard>

      <div class="projects__pager">
        <el-pagination layout="prev, pager, next" :total="filteredProjects.length" :page-size="9" background />
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
.projects__sort-hint {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--kh-warm);
  font-weight: 600;
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
