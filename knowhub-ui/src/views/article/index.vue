<!--
  <!--
  文档学习 /articles（总览）
  ------------------------------------------------------------------
  定位：系统化章节式学习文档（文章）+ 专门搜索（覆盖标题/简介/章节内容，命中章节标出）。
  照搬博客导航 /blogs 范式：hero 左搜索+标签云 / 右文库统计卡 + 主体文档卡片网格 + 排序栏 + 分页。
  分页走后端 PageHelper（PageUtil.startPage 从请求参数读 pageNum/pageSize），前端显式带参翻页真生效。
  标签云设最大展示数（TAG_LIMIT），超出折叠进"更多"（按标签排行 contentCount 降序，与侧栏标签排行同序）。
  标签热度与博客导航共用同一 /portal/tag/hot（跨博客+文章综合，文章分支维度已与博客对齐）。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import ArticleCard from '@/components/article/ArticleCard.vue'
import { searchArticlesApi, recommendArticlesApi, getArticleStatsApi } from '@/api/knowhub/article'
import { hotTagsApi } from '@/api/knowhub/blog'
import type { ArticlePortalRecord, ArticlePortalSearchQuery, PortalArticleStatsRecord } from '@/types/api/knowhub/article'
import type { HotTagRecord } from '@/types/api/knowhub/tag'
import type { NormalizedPageResult } from '@/types/api/common'
import { formatDateTime } from '@/utils/format'

/** 排序选项：相关度（命中标题/简介/章节内容相关度）/ 热度 / 最新 */
type SortKey = 'latest' | 'hot' | 'relevance'
const sortKey = ref<SortKey>('latest')
const sortOptions: { key: SortKey; label: string; toApi: ArticlePortalSearchQuery['sort'] }[] = [
  { key: 'latest', label: '最新', toApi: 'LATEST' },
  { key: 'hot', label: '最热', toApi: 'HOT' },
  { key: 'relevance', label: '相关度', toApi: 'RELEVANCE' },
]

/** 选中的标签 id 列表（多选，后端按 tagIds 精确过滤，走 article_tag join） */
const selectedTagIds = ref<number[]>([])
const keyword = ref('')

/** 标签云 + 标签排行（公开 /portal/tag/hot，统计 blog_tag+article_tag，与笔记导航共用） */
const hotTags = ref<HotTagRecord[]>([])
const tagRanking = computed(() => hotTags.value.slice(0, 10))

/** 标签云最大展示数：超出折叠进"更多"（按标签排行 contentCount 降序，与侧栏排行同序） */
const TAG_LIMIT = 12
const tagExpanded = ref(false)
/** 标签云默认展示项：折叠/展开都只取前 TAG_LIMIT 个，展开时剩余进固定高度滚动区（不撑高 hero） */
const visibleTags = computed(() => hotTags.value.slice(0, TAG_LIMIT))
const hasMoreTags = computed(() => hotTags.value.length > TAG_LIMIT)

/**
 * 标签名是否「过长」需循环滚动播放（照首页 isTagNameLong 范式）。
 * rank-name 容器固定宽 72px（13px 字号约容 5 个中文字 / 11 个英文字符），
 * 按字符数粗判：中文等宽字符计 1、半角字符计 0.5，加权和 > 5 即视为过长，开横向 marquee。
 */
const isTagNameLong = (name: string): boolean => {
  if (!name) return false
  let weight = 0
  for (const ch of name) {
    weight += /[　-鿿＀-￯]/.test(ch) ? 1 : 0.5
  }
  return weight > 5
}

/**
 * 切换标签选中（按 tagId）。
 * 重新赋值新数组而非 splice/push 原地改——ref<number[]> 的 watch 默认不 deep，原地改不触发；
 * 赋新数组让引用变化，watch([selectedTagIds,...]) 才能捕获，点击标签即触发筛选。
 */
const toggleTag = (tagId: number) => {
  const idx = selectedTagIds.value.indexOf(tagId)
  selectedTagIds.value = idx >= 0
    ? selectedTagIds.value.filter((id) => id !== tagId)
    : [...selectedTagIds.value, tagId]
}

/** 文档列表（真实接口分页：后端 PageUtil.startPage 从请求参数读 pageNum/pageSize） */
const docList = ref<ArticlePortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(9)
const loading = ref(false)

/** 侧栏推荐文档（/portal/article/recommend 兜底全局热门，取 6 条） */
const hotDocs = ref<ArticlePortalRecord[]>([])

/** 文库统计（独立接口 /portal/article/stats，固定口径，不随搜索/翻页/标签过滤变动） */
const stats = ref<PortalArticleStatsRecord>({ publishedDocCount: 0, totalChapters: 0, tagCount: 0 })
const publishedDocCount = computed(() => stats.value.publishedDocCount)
const totalChapters = computed(() => stats.value.totalChapters)
const tagCount = computed(() => stats.value.tagCount)

/** 拉取文档列表（搜索/标签/排序/分页变化时触发，显式带 pageNum/pageSize 让后端分页真生效） */
const fetchDocs = async () => {
  loading.value = true
  try {
    const sortApi = sortOptions.find((o) => o.key === sortKey.value)?.toApi ?? 'RELEVANCE'
    const query: ArticlePortalSearchQuery = {
      keyword: keyword.value.trim() || undefined,
      tagIds: selectedTagIds.value.length ? selectedTagIds.value : undefined,
      sort: sortApi,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const res: NormalizedPageResult<ArticlePortalRecord> = await searchArticlesApi(query)
    docList.value = (res.records ?? []).map((d) => ({
      ...d,
      publishTime: d.publishTime ? (formatDateTime(d.publishTime) as string) : d.publishTime,
    }))
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

/** 翻页 / 切每页条数：切 size 时组件已把页码置 1 抛回 */
const onPageChange = (p: number, sz: number) => {
  pageNum.value = p
  pageSize.value = sz
  void fetchDocs()
}

/** 拉取标签云 + 侧栏推荐 + 文库统计（三个独立口径的数据，进页面拉一次，不随搜索/翻页变） */
const fetchTagsHotAndStats = async () => {
  const [tagRes, hotRes, statsRes] = await Promise.all([
    hotTagsApi(50),
    recommendArticlesApi(6),
    getArticleStatsApi(),
  ])
  hotTags.value = tagRes.data ?? []
  hotDocs.value = (hotRes.data ?? []).map((d) => ({
    ...d,
    publishTime: d.publishTime ? (formatDateTime(d.publishTime) as string) : d.publishTime,
  }))
  if (statsRes.data) stats.value = statsRes.data
}

/** 搜索（按钮/回车触发，非实时）：回第一页重新拉取 */
const onSearch = () => {
  pageNum.value = 1
  void fetchDocs()
}

/** 标签/排序变化时回到第一页重新拉取（搜索走显式按钮触发，不实时） */
watch([selectedTagIds, sortKey], () => {
  pageNum.value = 1
  void fetchDocs()
})

onMounted(() => {
  void fetchTagsHotAndStats()
  void fetchDocs()
})
</script>

<template>
  <div class="docs">
    <!-- hero：左标题+搜索+标签云 / 右文库统计（对标笔记导航概览卡） -->
    <section class="docs__hero">
      <div class="kh-container kh-container--wide docs__hero-grid">
        <div class="docs__hero-main">
          <h1 class="docs__title">文档学习</h1>
          <p class="docs__subtitle">系统化的章节式学习文档 · 支持标题 / 简介 / 章节内容全检索，命中章节一键直达</p>
          <div class="docs__search">
            <el-icon class="docs__search-icon"><Search /></el-icon>
            <input
              v-model="keyword"
              class="docs__search-input"
              placeholder="搜索文档标题、简介或章节内容…"
              @keyup.enter="onSearch"
            />
            <button class="docs__search-btn" type="button" @click="onSearch">搜索</button>
          </div>

          <!-- 标签云：最多展示 TAG_LIMIT 个，超出折叠进"更多"（按标签排行 contentCount 降序） -->
          <div class="docs__tagcloud">
            <span class="docs__tagcloud-label"><KhIcon name="tag" :size="14" /> 全部标签</span>
            <button
              v-for="t in visibleTags"
              :key="t.tagId"
              class="docs__tagchip"
              :class="{ 'is-active': selectedTagIds.includes(t.tagId) }"
              type="button"
              @click="toggleTag(t.tagId)"
            >
              {{ t.tagName }}
              <span class="docs__tagchip-count">{{ t.contentCount ?? 0 }}</span>
            </button>
            <span v-if="!hotTags.length" class="docs__tagcloud-empty">暂无标签，待文档发布后收录</span>
            <button
              v-if="hasMoreTags"
              class="docs__tag-more"
              type="button"
              @click="tagExpanded = !tagExpanded"
            >
              {{ tagExpanded ? '收起' : `更多 (${hotTags.length - TAG_LIMIT})` }}
              <el-icon><ArrowDown v-if="!tagExpanded" /><ArrowUp v-else /></el-icon>
            </button>
          </div>
        </div>

        <!-- 右：文库统计卡（竖列样式，对标博客导航概览卡布局位置，保留原有竖列数据呈现） -->
        <aside class="docs__overview">
          <div class="docs__overview-head">
            <KhIcon name="doc" :size="16" />
            <span>文库统计</span>
          </div>
          <div class="docs__overview-rows">
            <div class="docs__overview-row"><span>已发布文档</span><b>{{ publishedDocCount }}</b></div>
            <div class="docs__overview-row"><span>本章页章节</span><b>{{ totalChapters }}</b></div>
            <div class="docs__overview-row"><span>收录标签</span><b>{{ tagCount }}</b></div>
          </div>
          <div class="docs__overview-hint">
            <KhIcon name="search" :size="13" />
            <span>用关键词或标签缩小范围，快速定位章节式文档</span>
          </div>
        </aside>
      </div>
    </section>

    <section class="kh-container kh-container--wide docs__body">
      <!-- 主：文档卡网格 -->
      <div class="docs__main">
        <div class="docs__toolbar">
          <div class="docs__count">共 <b>{{ total }}</b> 篇文档</div>
          <div class="docs__sort">
            <button
              v-for="o in sortOptions"
              :key="o.key"
              class="docs__sort-btn"
              :class="{ 'is-active': sortKey === o.key }"
              type="button"
              @click="sortKey = o.key"
            >{{ o.label }}</button>
          </div>
        </div>

        <div v-if="docList.length" class="docs__grid">
          <ArticleCard v-for="d in docList" :key="d.articleId" :doc="d" />
        </div>
        <KhCard v-else-if="!loading" padding="lg" class="docs__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的文档，换个关键词或标签试试</p>
        </KhCard>

        <div v-if="docList.length" class="docs__pager">
          <KhPagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total" @change="onPageChange" />
        </div>
      </div>

      <!-- 侧栏：标签排行 + 热门文档（统计已挪到 hero 右侧） -->
      <aside class="docs__aside">
        <KhCard padding="md" class="docs__panel">
          <KhSectionTitle title="标签排行" />
          <ol v-if="tagRanking.length" class="docs__rank">
            <li v-for="(t, i) in tagRanking" :key="t.tagId" class="docs__rank-item" @click="toggleTag(t.tagId)">
              <span class="docs__rank-no">{{ i + 1 }}</span>
              <span class="docs__rank-name">
                <span class="docs__rank-name-inner" :class="{ 'is-marquee': isTagNameLong(t.tagName) }">
                  <template v-if="isTagNameLong(t.tagName)">
                    <span class="docs__rank-name-unit">{{ t.tagName }}</span>
                    <span class="docs__rank-name-unit">{{ t.tagName }}</span>
                  </template>
                  <template v-else>{{ t.tagName }}</template>
                </span>
              </span>
              <span class="docs__rank-bar">
                <span class="docs__rank-bar-fill" :style="{ width: `${((t.contentCount ?? 0) / (tagRanking[0]?.contentCount ?? 1)) * 100}%` }" />
              </span>
              <span class="docs__rank-count">{{ t.contentCount ?? 0 }}</span>
            </li>
          </ol>
          <div v-else class="docs__panel-empty">
            <KhIcon name="tag" :size="28" :stroke="1.4" />
            <p>暂无标签数据</p>
          </div>
        </KhCard>

        <KhCard padding="md" class="docs__panel">
          <KhSectionTitle title="近期热门" subtitle="推荐文档" />
          <ul v-if="hotDocs.length" class="docs__hot">
            <li v-for="(d, i) in hotDocs" :key="d.articleId" class="docs__hot-item" @click="$router.push(`/article/${d.articleId}`)">
              <span class="docs__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="docs__hot-text">
                <div class="docs__hot-title kh-line-clamp-2">{{ d.title }}</div>
                <div class="docs__hot-meta">{{ d.viewCount ?? 0 }} 阅读 · {{ d.chapterCount ?? 0 }} 章</div>
              </div>
            </li>
          </ul>
          <div v-else class="docs__panel-empty">
            <KhIcon name="doc" :size="28" :stroke="1.4" />
            <p>暂无热门文档</p>
          </div>
        </KhCard>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.docs__hero {
  position: relative;
  padding: var(--kh-space-12) 0 var(--kh-space-10);
  background: var(--kh-gradient-hero);
  overflow: hidden;
}
.docs__hero-grid {
  display: grid;
  grid-template-columns: 1fr 240px;
  gap: var(--kh-space-8);
  align-items: center;
}
.docs__hero-main {
  min-width: 0;
}
.docs__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.docs__subtitle {
  margin-top: var(--kh-space-3);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
}
.docs__search {
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
.docs__search-icon { color: var(--kh-text-tertiary); }
.docs__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
}
.docs__search-btn {
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

/* —— 右侧文库统计卡（竖列数据样式，保留原有呈现） —— */
.docs__overview {
  position: relative;
  padding: var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  box-shadow: var(--kh-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.docs__overview::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--kh-radius-lg);
  background: var(--kh-gradient-card);
  pointer-events: none;
}
.docs__overview-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-2);
}
.docs__overview-rows {
  position: relative;
  display: flex;
  flex-direction: column;
}
.docs__overview-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  color: var(--kh-text-secondary);
  border-bottom: 1px dashed var(--kh-border-soft);
}
.docs__overview-row:last-child {
  border-bottom: none;
}
.docs__overview-row b {
  font-family: var(--kh-font-display);
  font-size: var(--kh-font-size-md);
  color: var(--kh-primary);
  font-weight: 700;
}
.docs__overview-hint {
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

/* 标签云 */
.docs__tagcloud {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-5);
  max-width: 1000px;
}
.docs__tagcloud-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--kh-text-tertiary);
  margin-right: var(--kh-space-2);
  font-weight: 600;
}
.docs__tagchip {
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
.docs__tagchip:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.docs__tagchip.is-active { background: var(--kh-primary); border-color: var(--kh-primary); color: #fff; }
.docs__tagchip.is-active .docs__tagchip-count { color: rgba(255, 255, 255, 0.8); }
.docs__tagchip-count { font-family: var(--kh-font-mono); font-size: 10px; color: var(--kh-text-tertiary); }
.docs__tagcloud-empty { font-size: 12px; color: var(--kh-text-tertiary); padding: 4px 0; }
.docs__tag-more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 12px;
  border: 1px dashed var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: transparent;
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.docs__tag-more:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.docs__tag-more .el-icon { font-size: 11px; }

.docs__body {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: stretch;
}
.docs__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.docs__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--kh-space-5);
}
.docs__count { font-size: var(--kh-font-size-sm); color: var(--kh-text-secondary); }
.docs__count b { color: var(--kh-primary); font-family: var(--kh-font-display); font-size: var(--kh-font-size-md); }
.docs__sort {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
}
.docs__sort-btn {
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
.docs__sort-btn.is-active { background: var(--kh-surface); color: var(--kh-primary); box-shadow: var(--kh-shadow-xs); }
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
  justify-content: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-height: 320px;
}
.docs__pager { display: flex; justify-content: center; margin-top: var(--kh-space-8); }

.docs__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}

/* 标签排行 */
.docs__rank { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--kh-space-3); }
.docs__rank-item { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 0; }
.docs__rank-no { width: 20px; font-family: var(--kh-font-display); font-weight: 700; font-size: 13px; color: var(--kh-text-tertiary); text-align: center; }
.docs__rank-item:nth-child(-n + 3) .docs__rank-no { color: var(--kh-warm); }
.docs__rank-name {
  width: 72px;
  flex: none;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.docs__rank-name-inner {
  display: inline-block;
  white-space: nowrap;
}
/* 长标签名循环滚动（照首页/博客页范式）：两个 unit 各带 4em 间隔横向平移 -50% 无缝循环。
   mask 仅挂滚动态——短名不滚，左右清晰不虚化。hover 暂停便于看清。 */
.docs__rank-name-inner.is-marquee {
  animation: kh-docs-rank-marquee 14s linear infinite;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
}
.docs__rank-name-inner.is-marquee:hover {
  animation-play-state: paused;
}
.docs__rank-name-unit {
  margin-right: 4em;
}
@keyframes kh-docs-rank-marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
@media (prefers-reduced-motion: reduce) {
  .docs__rank-name-inner.is-marquee { animation: none; }
}
.docs__rank-bar { flex: 1; height: 6px; border-radius: var(--kh-radius-pill); background: var(--kh-bg-soft); overflow: hidden; }
.docs__rank-bar-fill { display: block; height: 100%; border-radius: var(--kh-radius-pill); background: linear-gradient(90deg, var(--kh-primary), var(--kh-accent)); }
.docs__rank-count { font-family: var(--kh-font-mono); font-size: 11px; color: var(--kh-text-tertiary); width: 24px; text-align: right; }

.docs__hot { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--kh-space-4); }
.docs__hot-item { display: flex; gap: 10px; cursor: pointer; }
.docs__hot-no { font-family: var(--kh-font-display); font-weight: 700; font-size: 14px; color: var(--kh-text-tertiary); width: 18px; flex: none; }
.docs__hot-no.is-top { color: var(--kh-warm); }
.docs__hot-title { font-size: 13px; font-weight: 500; color: var(--kh-text); line-height: 1.5; }
.docs__hot-meta { margin-top: 4px; font-size: 11px; color: var(--kh-text-tertiary); font-family: var(--kh-font-mono); }

.docs__panel-empty { display: flex; flex-direction: column; align-items: center; gap: var(--kh-space-2); padding: var(--kh-space-6) 0; color: var(--kh-text-tertiary); text-align: center; }
.docs__panel-empty p { font-size: 12px; }

@media (max-width: 1024px) {
  .docs__hero-grid { grid-template-columns: 1fr; }
  .docs__body { grid-template-columns: 1fr; }
  .docs__aside { position: static; }
}
</style>