<!--
  笔记导航 /notes
  ------------------------------------------------------------------
  标签云（最多标签展示地）+ 内容搜索 + 博客卡片网格 + 侧栏标签排行/热门笔记 + 排序栏。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import BlogRow from '@/components/blog/BlogRow.vue'
import { blogs, type MockBlog } from '@/mock/blog'
import { tags } from '@/mock/tag'

/** 排序选项 */
type SortKey = 'latest' | 'hot' | 'rating'
const sortKey = ref<SortKey>('latest')
const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'latest', label: '最新' },
  { key: 'hot', label: '最热' },
  { key: 'rating', label: '评分优先' },
]

/** 选中的标签（多选） */
const selectedTags = ref<string[]>([])
const keyword = ref('')

/** 切换标签选中 */
const toggleTag = (name: string) => {
  const idx = selectedTags.value.indexOf(name)
  if (idx >= 0) selectedTags.value.splice(idx, 1)
  else selectedTags.value.push(name)
}

/** 过滤 + 排序后的博客列表 */
const filteredBlogs = computed<MockBlog[]>(() => {
  let list = blogs.filter((b) => b.status === 'PUBLISHED')
  if (selectedTags.value.length) {
    list = list.filter((b) => b.tags.some((t) => selectedTags.value.includes(t)))
  }
  if (keyword.value.trim()) {
    const k = keyword.value.trim().toLowerCase()
    list = list.filter(
      (b) => b.title.toLowerCase().includes(k) || b.summary.toLowerCase().includes(k),
    )
  }
  if (sortKey.value === 'latest') list = [...list].sort((a, b) => b.publishTime.localeCompare(a.publishTime))
  else if (sortKey.value === 'hot') list = [...list].sort((a, b) => b.viewCount - a.viewCount)
  else list = [...list].sort((a, b) => b.rating - a.rating)
  return list
})

/** 标签排行（按博客数） */
const tagRanking = [...tags].sort((a, b) => b.count - a.count).slice(0, 10)

/** 热门笔记（侧栏） */
const hotNotes = [...blogs].filter((b) => b.status === 'PUBLISHED').sort((a, b) => b.viewCount - a.viewCount).slice(0, 5)

/** Hero 右侧概览卡数据 */
const publishedBlogCount = blogs.filter((b) => b.status === 'PUBLISHED').length
const totalReads = blogs.reduce((s, b) => s + b.viewCount, 0).toLocaleString()
</script>

<template>
  <div class="notes">
    <!-- 页头 -->
    <section class="notes__hero">
      <div class="kh-container kh-container--wide notes__hero-grid">
        <!-- 左：标题 + 搜索 + 标签云 -->
        <div class="notes__hero-main">
          <h1 class="notes__title">笔记导航</h1>
          <p class="notes__subtitle">按标签探索实验室沉淀的每一篇博客 · 这里是标签最全的地方</p>

          <div class="notes__search">
            <el-icon class="notes__search-icon"><Search /></el-icon>
            <input v-model="keyword" class="notes__search-input" placeholder="模糊搜索博客标题或摘要…" />
            <button class="notes__search-btn" type="button">搜索</button>
          </div>

          <!-- 标签云（最全） -->
          <div class="notes__tagcloud">
            <span class="notes__tagcloud-label">
              <KhIcon name="tag" :size="14" /> 全部标签
            </span>
            <button
              v-for="t in tags"
              :key="t.id"
              class="notes__tagchip"
              :class="[`notes__tagchip--${t.tone}`, { 'is-active': selectedTags.includes(t.name) }]"
              type="button"
              @click="toggleTag(t.name)"
            >
              {{ t.name }}
              <span class="notes__tagchip-count">{{ t.count }}</span>
            </button>
          </div>
        </div>

        <!-- 右：笔记概览卡（填补右侧空白，不与侧栏排行重复） -->
        <aside class="notes__overview">
          <div class="notes__overview-head">
            <KhIcon name="blog" :size="16" />
            <span>笔记概览</span>
          </div>
          <div class="notes__overview-stats">
            <div class="notes__overview-stat">
              <div class="notes__overview-value">{{ publishedBlogCount }}</div>
              <div class="notes__overview-label">已发布博客</div>
            </div>
            <div class="notes__overview-stat">
              <div class="notes__overview-value">{{ tags.length }}</div>
              <div class="notes__overview-label">收录标签</div>
            </div>
            <div class="notes__overview-stat">
              <div class="notes__overview-value">{{ totalReads }}</div>
              <div class="notes__overview-label">累计阅读</div>
            </div>
          </div>
          <div class="notes__overview-hint">
            <KhIcon name="search" :size="13" />
            <span>用关键词或标签缩小范围，快速找到需要的笔记</span>
          </div>
        </aside>
      </div>
    </section>

    <!-- 主体：左侧列表 + 右侧栏 -->
    <section class="kh-container kh-container--wide notes__body">
      <div class="notes__main">
        <div class="notes__toolbar">
          <div class="notes__count">共 <b>{{ filteredBlogs.length }}</b> 篇笔记</div>
          <div class="notes__sort">
            <button
              v-for="o in sortOptions"
              :key="o.key"
              class="notes__sort-btn"
              :class="{ 'is-active': sortKey === o.key }"
              type="button"
              @click="sortKey = o.key"
            >
              {{ o.label }}
            </button>
          </div>
        </div>

        <div v-if="filteredBlogs.length" class="notes__list">
          <BlogRow v-for="b in filteredBlogs" :key="b.blogId" :blog="b" />
        </div>
        <KhCard v-else padding="lg" class="notes__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的笔记，换个标签或关键词试试</p>
        </KhCard>

        <div class="notes__pager">
          <el-pagination layout="prev, pager, next" :total="filteredBlogs.length" :page-size="9" background />
        </div>
      </div>

      <!-- 侧栏 -->
      <aside class="notes__aside">
        <KhCard padding="md" class="notes__panel">
          <KhSectionTitle title="标签排行" />
          <ol class="notes__rank">
            <li v-for="(t, i) in tagRanking" :key="t.id" class="notes__rank-item" @click="toggleTag(t.name)">
              <span class="notes__rank-no">{{ i + 1 }}</span>
              <span class="notes__rank-name">{{ t.name }}</span>
              <span class="notes__rank-bar">
                <span class="notes__rank-bar-fill" :style="{ width: `${(t.count / (tagRanking[0]?.count ?? 1)) * 100}%` }" />
              </span>
              <span class="notes__rank-count">{{ t.count }}</span>
            </li>
          </ol>
        </KhCard>

        <KhCard padding="md" class="notes__panel">
          <KhSectionTitle title="近期热门" />
          <ul class="notes__hot">
            <li v-for="(b, i) in hotNotes" :key="b.blogId" class="notes__hot-item" @click="$router.push(`/blog/${b.blogId}`)">
              <span class="notes__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="notes__hot-text">
                <div class="notes__hot-title kh-line-clamp-2">{{ b.title }}</div>
                <div class="notes__hot-meta">{{ b.viewCount }} 阅读 · {{ b.authorNickname }}</div>
              </div>
            </li>
          </ul>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.notes__hero {
  position: relative;
  padding: var(--kh-space-12) 0 var(--kh-space-10);
  background: var(--kh-gradient-hero);
  overflow: hidden;
}
.notes__hero-grid {
  display: grid;
  grid-template-columns: 1fr 240px;
  gap: var(--kh-space-8);
  align-items: center;
}
.notes__hero-main {
  min-width: 0;
}
.notes__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.notes__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.notes__search {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-5);
  height: 48px;
  padding: 0 6px 0 var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
}
.notes__search-icon {
  color: var(--kh-text-tertiary);
}
.notes__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
}
.notes__search-btn {
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

/* —— 右侧概览卡 —— */
.notes__overview {
  position: relative;
  padding: var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  box-shadow: var(--kh-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.notes__overview::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--kh-radius-lg);
  background: var(--kh-gradient-card);
  pointer-events: none;
}
.notes__overview-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
}
.notes__overview-stats {
  position: relative;
  display: flex;
  gap: var(--kh-space-3);
}
.notes__overview-stat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--kh-space-4) 4px;
  border-radius: var(--kh-radius);
  background: var(--kh-bg-soft);
  text-align: center;
}
.notes__overview-value {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-xl);
  font-weight: 700;
  color: var(--kh-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.notes__overview-label {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  white-space: nowrap;
}
.notes__overview-hint {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--kh-space-3);
  border-radius: var(--kh-radius);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
  font-size: 12px;
  line-height: 1.5;
}

.notes__tagcloud {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-5);
  max-width: 1000px;
}
.notes__tagcloud-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  margin-right: var(--kh-space-2);
  font-weight: 600;
}
.notes__tagchip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.notes__tagchip:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.notes__tagchip.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.notes__tagchip.is-active .notes__tagchip-count {
  color: rgba(255, 255, 255, 0.8);
}
.notes__tagchip-count {
  font-family: var(--kh-font-mono);
  font-size: 10px;
  color: var(--kh-text-tertiary);
}

.notes__body {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: start;
}
.notes__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--kh-space-5);
}
.notes__count {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
}
.notes__count b {
  color: var(--kh-primary);
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
}
.notes__sort {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
}
.notes__sort-btn {
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
.notes__sort-btn.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}
.notes__list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.notes__empty {
  text-align: center;
  color: var(--kh-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
}
.notes__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--kh-space-8);
}

.notes__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.notes__rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.notes__rank-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 0;
}
.notes__rank-no {
  width: 20px;
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.notes__rank-item:nth-child(-n + 3) .notes__rank-no {
  color: var(--kh-warm);
}
.notes__rank-name {
  width: 72px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.notes__rank-bar {
  flex: 1;
  height: 6px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  overflow: hidden;
}
.notes__rank-bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(90deg, var(--kh-primary), var(--kh-accent));
}
.notes__rank-count {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  width: 24px;
  text-align: right;
}
.notes__hot {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.notes__hot-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
}
.notes__hot-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 14px;
  color: var(--kh-text-tertiary);
  width: 18px;
  flex: none;
}
.notes__hot-no.is-top {
  color: var(--kh-warm);
}
.notes__hot-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.5;
}
.notes__hot-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--kh-text-tertiary);
}

@media (max-width: 1024px) {
  .notes__hero-grid {
    grid-template-columns: 1fr;
  }
  .notes__body {
    grid-template-columns: 1fr;
  }
  .notes__aside {
    position: static;
  }
}
@media (max-width: 640px) {
  .notes__overview-stat {
    flex: 1 1 40%;
  }
}
</style>
