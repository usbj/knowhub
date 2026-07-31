<!--
  文档学习 /docs（总览）
  ------------------------------------------------------------------
  定位：系统化章节式学习文档（文章）+ 专门搜索（覆盖标题/简介/章节内容，命中章节标出）。
  照搬笔记导航 /notes 范式：搜索 + 标签云/排行（复用 /portal/tag/hot，与笔记导航共用）+
  推荐侧栏（/portal/article/recommend）+ 文档卡片网格 + 排序栏 + 分页。
  标签热度与笔记导航共用同一 /portal/tag/hot（跨博客+文章综合，文章分支维度已与博客对齐）。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import DocCard from '@/components/doc/DocCard.vue'
import { searchArticlesApi, recommendArticlesApi } from '@/api/knowhub/article'
import { hotTagsApi } from '@/api/knowhub/blog'
import type { ArticlePortalRecord, ArticlePortalSearchQuery } from '@/types/api/knowhub/article'
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

const toggleTag = (tagId: number) => {
  const idx = selectedTagIds.value.indexOf(tagId)
  if (idx >= 0) selectedTagIds.value.splice(idx, 1)
  else selectedTagIds.value.push(tagId)
}

/** 文档列表（真实接口分页） */
const docList = ref<ArticlePortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 9
const loading = ref(false)

/** 侧栏推荐文档（/portal/article/recommend 兜底全局热门，取 6 条） */
const hotDocs = ref<ArticlePortalRecord[]>([])

/** 概览卡数据（从真实标签榜+列表 total 派生） */
const publishedDocCount = computed(() => total.value)
const tagCount = computed(() => hotTags.value.length)
const totalChapters = computed(() => docList.value.reduce((s, d) => s + (d.chapterCount ?? 0), 0))

/** 拉取文档列表（搜索/标签/排序/分页变化时触发） */
const fetchDocs = async () => {
  loading.value = true
  try {
    const sortApi = sortOptions.find((o) => o.key === sortKey.value)?.toApi ?? 'RELEVANCE'
    const query: ArticlePortalSearchQuery = {
      keyword: keyword.value.trim() || undefined,
      tagIds: selectedTagIds.value.length ? selectedTagIds.value : undefined,
      sort: sortApi,
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

/** 翻页 */
const onPageChange = (p: number) => {
  pageNum.value = p
  void fetchDocs()
}

/** 拉取标签云 + 侧栏推荐 */
const fetchTagsAndHot = async () => {
  const [tagRes, hotRes] = await Promise.all([
    hotTagsApi(50),
    recommendArticlesApi(6),
  ])
  hotTags.value = tagRes.data ?? []
  hotDocs.value = (hotRes.data ?? []).map((d) => ({
    ...d,
    publishTime: d.publishTime ? (formatDateTime(d.publishTime) as string) : d.publishTime,
  }))
}

/** 搜索/标签/排序变化时回到第一页重新拉取 */
watch([keyword, selectedTagIds, sortKey], () => {
  pageNum.value = 1
  void fetchDocs()
})

onMounted(() => {
  void fetchTagsAndHot()
  void fetchDocs()
})
</script>

<template>
  <div class="docs">
    <section class="docs__hero">
      <div class="kh-container kh-container--wide">
        <h1 class="docs__title">文档学习</h1>
        <p class="docs__subtitle">系统化的章节式学习文档 · 支持标题 / 简介 / 章节内容全检索，命中章节一键直达</p>
        <div class="docs__search">
          <el-icon class="docs__search-icon"><Search /></el-icon>
          <input
            v-model="keyword"
            class="docs__search-input"
            placeholder="搜索文档标题、简介或章节内容…"
            @keyup.enter="pageNum = 1; fetchDocs()"
          />
          <button class="docs__search-btn" type="button" @click="pageNum = 1; fetchDocs()">搜索</button>
        </div>

        <!-- 标签云（公开 /portal/tag/hot，与笔记导航共用同一榜单） -->
        <div class="docs__tagcloud">
          <span class="docs__tagcloud-label"><KhIcon name="tag" :size="14" /> 全部标签</span>
          <button
            v-for="t in hotTags"
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
        </div>
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
          <DocCard v-for="d in docList" :key="d.articleId" :doc="d" />
        </div>
        <KhCard v-else-if="!loading" padding="lg" class="docs__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的文档，换个关键词或标签试试</p>
        </KhCard>

        <div v-if="docList.length && total > pageSize" class="docs__pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="pageNum"
            background
            @current-change="onPageChange"
          />
        </div>
      </div>

      <!-- 侧栏：统计 + 标签排行 + 热门文档 -->
      <aside class="docs__aside">
        <KhCard padding="md" class="docs__stat">
          <div class="docs__stat-title">文档库统计</div>
          <div class="docs__stat-row"><span>已发布文档</span><b>{{ publishedDocCount }}</b></div>
          <div class="docs__stat-row"><span>本章页章节数</span><b>{{ totalChapters }}</b></div>
          <div class="docs__stat-row"><span>收录标签</span><b>{{ tagCount }}</b></div>
        </KhCard>

        <KhCard padding="md" class="docs__panel">
          <KhSectionTitle title="标签排行" />
          <ol v-if="tagRanking.length" class="docs__rank">
            <li v-for="(t, i) in tagRanking" :key="t.tagId" class="docs__rank-item" @click="toggleTag(t.tagId)">
              <span class="docs__rank-no">{{ i + 1 }}</span>
              <span class="docs__rank-name">{{ t.tagName }}</span>
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
            <li v-for="(d, i) in hotDocs" :key="d.articleId" class="docs__hot-item" @click="$router.push(`/docs/${d.articleId}`)">
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
.docs__stat-title { font-size: var(--kh-font-size-sm); font-weight: 600; color: var(--kh-text); margin-bottom: var(--kh-space-3); }
.docs__stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  color: var(--kh-text-secondary);
  border-bottom: 1px dashed var(--kh-border-soft);
}
.docs__stat-row:last-child { border-bottom: none; }
.docs__stat-row b { font-family: var(--kh-font-display); font-size: var(--kh-font-size-md); color: var(--kh-primary); font-weight: 700; }

/* 标签排行 */
.docs__rank { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--kh-space-3); }
.docs__rank-item { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 0; }
.docs__rank-no { width: 20px; font-family: var(--kh-font-display); font-weight: 700; font-size: 13px; color: var(--kh-text-tertiary); text-align: center; }
.docs__rank-item:nth-child(-n + 3) .docs__rank-no { color: var(--kh-warm); }
.docs__rank-name { width: 72px; font-size: 13px; font-weight: 500; color: var(--kh-text); }
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
  .docs__body { grid-template-columns: 1fr; }
  .docs__aside { position: static; }
}
</style>