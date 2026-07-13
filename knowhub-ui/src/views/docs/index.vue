<!--
  文档学习 /docs（总览）
  ------------------------------------------------------------------
  定位：推荐系统学习文档（章节集合型）+ 专门文档搜索。
  总览页：顶部搜索栏 + 难度筛选 + 文档推荐卡片网格 + 右侧统计/热门。
  左侧目录树不在此页出现，仅文档详情展示时才显示。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import DocCard from '@/components/doc/DocCard.vue'
import { docs } from '@/mock/doc'

const keyword = ref('')
const levelFilter = ref<'ALL' | '入门' | '进阶' | '高级'>('ALL')

const filteredDocs = computed(() => {
  let list = [...docs]
  if (levelFilter.value !== 'ALL') list = list.filter((d) => d.level === levelFilter.value)
  if (keyword.value.trim()) {
    const k = keyword.value.trim().toLowerCase()
    list = list.filter((d) => d.title.toLowerCase().includes(k) || d.summary.toLowerCase().includes(k))
  }
  return list.sort((a, b) => b.readCount - a.readCount)
})

const levelOptions: { key: typeof levelFilter.value; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: '入门', label: '入门' },
  { key: '进阶', label: '进阶' },
  { key: '高级', label: '高级' },
]

/** 阅读量最高的文档（侧栏热门） */
const hotDocs = [...docs].sort((a, b) => b.readCount - a.readCount).slice(0, 6)

/** 统计 */
const totalChapters = docs.reduce((s, d) => s + d.chapterCount, 0)
const totalReads = docs.reduce((s, d) => s + d.readCount, 0).toLocaleString()
</script>

<template>
  <div class="docs">
    <section class="docs__hero">
      <div class="kh-container kh-container--wide">
        <h1 class="docs__title">文档学习</h1>
        <p class="docs__subtitle">系统化的章节式学习文档 · 照文档站结构组织，支持专门搜索</p>
        <div class="docs__search">
          <el-icon class="docs__search-icon"><Search /></el-icon>
          <input v-model="keyword" class="docs__search-input" placeholder="搜索文档标题或摘要…" />
          <div class="docs__search-filter">
            <button
              v-for="o in levelOptions"
              :key="o.key"
              class="docs__level-btn"
              :class="{ 'is-active': levelFilter === o.key }"
              type="button"
              @click="levelFilter = o.key"
            >{{ o.label }}</button>
          </div>
        </div>
      </div>
    </section>

    <section class="kh-container kh-container--wide docs__body">
      <!-- 主：文档卡网格 -->
      <div class="docs__main">
        <KhSectionTitle title="推荐学习文档" :subtitle="`共 ${filteredDocs.length} 篇 · 按阅读量排序`" />
        <div v-if="filteredDocs.length" class="docs__grid">
          <DocCard v-for="d in filteredDocs" :key="d.id" :doc="d" />
        </div>
        <KhCard v-else padding="lg" class="docs__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的文档</p>
        </KhCard>

        <div class="docs__pager">
          <el-pagination layout="prev, pager, next" :total="filteredDocs.length" :page-size="9" background />
        </div>
      </div>

      <!-- 侧栏：统计 + 热门 -->
      <aside class="docs__aside">
        <KhCard padding="md" class="docs__stat">
          <div class="docs__stat-title">文档库统计</div>
          <div class="docs__stat-row">
            <span>文档总数</span><b>{{ docs.length }}</b>
          </div>
          <div class="docs__stat-row">
            <span>章节总数</span><b>{{ totalChapters }}</b>
          </div>
          <div class="docs__stat-row">
            <span>累计阅读</span><b>{{ totalReads }}</b>
          </div>
        </KhCard>

        <KhCard padding="md" class="docs__aside-panel">
          <KhSectionTitle title="热门文档" subtitle="按阅读量" />
          <ol class="docs__hot">
            <li v-for="(d, i) in hotDocs" :key="d.id" class="docs__hot-item">
              <span class="docs__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="docs__hot-text">
                <div class="docs__hot-title kh-line-clamp-1">{{ d.title }}</div>
                <div class="docs__hot-meta">{{ d.readCount.toLocaleString() }} 阅读 · {{ d.chapterCount }} 章</div>
              </div>
            </li>
          </ol>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.docs__hero {
  padding: var(--kh-space-12) 0 var(--kh-space-10);
  background: var(--kh-gradient-hero);
}
.docs__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
}
.docs__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.docs__search {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-5);
  height: 48px;
  padding: 0 var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  max-width: 760px;
}
.docs__search-icon {
  color: var(--kh-text-tertiary);
}
.docs__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
}
.docs__search-filter {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--kh-bg-soft);
  border-radius: var(--kh-radius-pill);
}
.docs__level-btn {
  padding: 5px 12px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius-pill);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.docs__level-btn.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}

.docs__body {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: start;
}
.docs__main {
  min-width: 0;
}
.docs__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--kh-space-5);
}
.docs__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}
.docs__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}

.docs__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.docs__stat-title {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
  margin-bottom: var(--kh-space-3);
}
.docs__stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  color: var(--kh-text-secondary);
  border-bottom: 1px dashed var(--kh-border-soft);
}
.docs__stat-row:last-child {
  border-bottom: none;
}
.docs__stat-row b {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
  color: var(--kh-primary);
  font-weight: 700;
}

.docs__hot {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.docs__hot-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.docs__hot-item:hover {
  background: var(--kh-surface-muted);
}
.docs__hot-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  flex: none;
}
.docs__hot-no.is-top {
  color: var(--kh-warm);
}
.docs__hot-text {
  flex: 1;
  min-width: 0;
}
.docs__hot-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.5;
}
.docs__hot-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
  font-family: var(--kh-font-mono);
}

@media (max-width: 1024px) {
  .docs__body {
    grid-template-columns: 1fr;
  }
  .docs__aside {
    position: static;
  }
}
</style>
