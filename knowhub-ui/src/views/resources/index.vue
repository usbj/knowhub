<!--
  资源推荐 /resources
  ------------------------------------------------------------------
  分类筛选（网站/软件/脚本/文档/工具，单一入口）+ 资源卡片网格 + 侧栏热门下载榜 + 最近上传。
  只保留主体工具栏的紧凑标签筛选，避免与顶部重复。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import ResourceCard from '@/components/resource/ResourceCard.vue'
import { resources, resourceCategories } from '@/mock/resource'

const categoryFilter = ref<'ALL' | string>('ALL')
const keyword = ref('')

/** 分类选项（含"全部"）—— 唯一筛选入口 */
const categoryOptions = computed(() => [
  { key: 'ALL', label: '全部', icon: 'sparkles', color: 'var(--kh-primary)' },
  ...resourceCategories.map((c) => ({ key: c.key, label: c.label, icon: c.linkIcon, color: c.color })),
])

const filteredResources = computed(() => {
  let list = resources.filter((r) => r.status !== 'ARCHIVED')
  if (categoryFilter.value !== 'ALL') list = list.filter((r) => r.category === categoryFilter.value)
  if (keyword.value.trim()) {
    const k = keyword.value.trim().toLowerCase()
    list = list.filter((r) => r.title.toLowerCase().includes(k) || r.description.toLowerCase().includes(k))
  }
  return list
})

/** 热门下载榜（文件类） */
const hotDownload = [...resources]
  .filter((r) => r.category !== 'WEBSITE' && r.category !== 'TOOL')
  .sort((a, b) => b.downloadCount - a.downloadCount)
  .slice(0, 6)

/** 最近上传 */
const recentUpload = [...resources].sort((a, b) => b.createTime.localeCompare(a.createTime)).slice(0, 5)

/** 字节大小 → B/KB/MB/GB，与资源卡 / 项目详情 formatSize 口径一致 */
const formatSize = (len?: number) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<template>
  <div class="resources">
    <section class="res__hero">
      <div class="kh-container kh-container--wide">
        <h1 class="res__title">资源推荐</h1>
        <p class="res__subtitle">软件、脚本、文档、工具网站链接 · 实验室精选优秀资源</p>

        <div class="res__search">
          <el-icon class="res__search-icon"><Search /></el-icon>
          <input v-model="keyword" class="res__search-input" placeholder="搜索资源…" />
        </div>
      </div>
    </section>

    <section class="kh-container kh-container--wide res__body">
      <!-- 主体 -->
      <div class="res__main">
        <div class="res__toolbar">
          <div class="res__filter-tags">
            <button
              v-for="c in categoryOptions"
              :key="c.key"
              class="res__filter-btn"
              :class="{ 'is-active': categoryFilter === c.key }"
              type="button"
              @click="categoryFilter = c.key"
            >
              <KhIcon :name="c.icon" :size="13" />
              {{ c.label }}
              <span class="res__filter-count">
                {{ c.key === 'ALL' ? resources.filter((r) => r.status !== 'ARCHIVED').length : resources.filter((r) => r.category === c.key && r.status !== 'ARCHIVED').length }}
              </span>
            </button>
          </div>
          <div class="res__count">共 <b>{{ filteredResources.length }}</b> 个资源</div>
        </div>

        <div v-if="filteredResources.length" class="res__grid">
          <ResourceCard v-for="r in filteredResources" :key="r.resourceId" :resource="r" />
        </div>
        <KhCard v-else padding="lg" class="res__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的资源</p>
        </KhCard>

        <div class="res__pager">
          <el-pagination layout="prev, pager, next" :total="filteredResources.length" :page-size="10" background />
        </div>
      </div>

      <!-- 侧栏 -->
      <aside class="res__aside">
        <KhCard padding="md" class="res__panel">
          <KhSectionTitle title="热门下载榜" />
          <ol class="res__hot">
            <li v-for="(r, i) in hotDownload" :key="r.resourceId" class="res__hot-item">
              <span class="res__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="res__hot-icon" :style="{ background: r.cover }">
                <KhIcon :name="r.linkIcon" :size="14" />
              </div>
              <div class="res__hot-text">
                <div class="res__hot-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="res__hot-meta">{{ r.downloadCount }} 下载 · {{ formatSize(r.contentLength) }}</div>
              </div>
            </li>
          </ol>
        </KhCard>

        <KhCard padding="md" class="res__panel">
          <KhSectionTitle title="最近上传" />
          <ul class="res__recent">
            <li v-for="r in recentUpload" :key="r.resourceId" class="res__recent-item">
              <div class="res__recent-dot" :style="{ background: r.cover }" />
              <div class="res__recent-text">
                <div class="res__recent-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="res__recent-time">{{ r.createTime }} · {{ r.authorNickname }}</div>
              </div>
            </li>
          </ul>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.res__hero {
  padding: var(--kh-space-12) 0 var(--kh-space-10);
  background: var(--kh-gradient-hero);
}
.res__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
}
.res__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.res__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  max-width: 560px;
  margin-top: var(--kh-space-5);
}
.res__search-icon {
  color: var(--kh-text-tertiary);
}
.res__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
}

.res__body {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: start;
}
.res__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--kh-space-5);
  flex-wrap: wrap;
  gap: var(--kh-space-3);
}
.res__filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.res__filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.res__filter-btn:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.res__filter-btn.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.res__filter-btn.is-active .res__filter-count {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.res__filter-count {
  font-family: var(--kh-font-mono);
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  color: var(--kh-text-tertiary);
}
.res__count {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.res__count b {
  color: var(--kh-primary);
  font-family: var(--kh-font-display);
}
.res__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(248px, 1fr));
  gap: var(--kh-space-5);
}
.res__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}
.res__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}

.res__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.res__hot {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.res__hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.res__hot-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  text-align: center;
  flex: none;
}
.res__hot-no.is-top {
  color: var(--kh-warm);
}
.res__hot-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
}
.res__hot-text {
  min-width: 0;
  flex: 1;
}
.res__hot-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.res__hot-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
  font-family: var(--kh-font-mono);
}
.res__recent {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.res__recent-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
}
.res__recent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex: none;
}
.res__recent-text {
  flex: 1;
  min-width: 0;
}
.res__recent-title {
  font-size: 13px;
  color: var(--kh-text);
  line-height: 1.4;
}
.res__recent-time {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}

@media (max-width: 1024px) {
  .res__body {
    grid-template-columns: 1fr;
  }
  .res__aside {
    position: static;
  }
}
</style>
