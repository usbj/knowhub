<!--
  <!--
  博客导航 /blogs
  ------------------------------------------------------------------
  标签云（最多标签展示地）+ 内容搜索 + 博客卡片网格 + 侧栏标签排行/热门博客 + 排序栏。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search, ArrowDown } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhPagination from '@/components/common/KhPagination.vue'
import BlogRow from '@/components/blog/BlogRow.vue'
import { searchBlogsApi, recommendBlogsApi, hotTagsApi, listEnabledTagsApi, getBlogStatsApi } from '@/api/knowhub/blog'
import type { BlogPortalRecord, BlogPortalSearchQuery, PortalBlogStatsRecord } from '@/types/api/knowhub/blog'
import type { HotTagRecord, TagRecord } from '@/types/api/knowhub/tag'
import type { NormalizedPageResult } from '@/types/api/common'
import { formatDateTime } from '@/utils/format'

/** 排序选项（rating 项已废弃：后端无评分字段，改为最新/最热/相关度） */
type SortKey = 'latest' | 'hot' | 'relevance'
const sortKey = ref<SortKey>('latest')
const sortOptions: { key: SortKey; label: string; toApi: BlogPortalSearchQuery['sort'] }[] = [
  { key: 'latest', label: '最新', toApi: 'LATEST' },
  { key: 'hot', label: '最热', toApi: 'HOT' },
  { key: 'relevance', label: '相关度', toApi: 'RELEVANCE' },
]

/** 选中的标签 id 列表（多选，后端按 tagIds 精确过滤） */
const selectedTagIds = ref<number[]>([])
const keyword = ref('')

/** 标签云 + 标签排行（公开 /portal/tag/hot，统计 blog_tag+article_tag） */
const hotTags = ref<HotTagRecord[]>([])
const tagRanking = computed(() => hotTags.value.slice(0, 10))

/** 全部启用标签（/portal/tag/list，不受热度/已发布内容限制，含暂无内容的标签） */
const allTags = ref<TagRecord[]>([])
/** 热度计数映射：tagId → contentCount（blog+article 合计，来自 hotTags 榜），供标签云计数徽标回填 */
const hotCountMap = computed(() => {
  const m = new Map<number, number>()
  for (const t of hotTags.value) m.set(t.tagId, t.contentCount ?? 0)
  return m
})

/** 标签云最大展示数：超出折叠进"更多"下拉菜单（按 sort 顺序，全部启用标签） */
const TAG_LIMIT = 12
/** 标签云展示项：只取前 TAG_LIMIT 个内联展示，超出进"更多"下拉菜单（不撑高 hero） */
const visibleTags = computed(() => allTags.value.slice(0, TAG_LIMIT))
const hasMoreTags = computed(() => allTags.value.length > TAG_LIMIT)
/** 标签云"更多"下拉里的剩余项 */
const moreTags = computed(() => allTags.value.slice(TAG_LIMIT))
/** el-dropdown 无 v-model:visible（死绑定），收起只能走 ref.handleClose()。
 *  见 memory el-dropdown-vmodel-visible-dead-binding。 */
const moreDropdownRef = ref<{ handleClose?: () => void } | null>(null)
/** 下拉点标签后：切换选中 + 手动收起 popper */
const onMoreTagCommand = (tagId: number) => {
  toggleTag(tagId)
  moreDropdownRef.value?.handleClose?.()
}

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

/** 博客列表（真实接口分页） */
const blogList = ref<BlogPortalRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(9)
const loading = ref(false)

/** 侧栏热门笔记（推荐 feed 兜底全局热门，取 5 条） */
const hotNotes = ref<BlogPortalRecord[]>([])

/** 概览卡数据（独立接口 /portal/blog/stats，固定口径，不随搜索/翻页/标签过滤变动） */
const stats = ref<PortalBlogStatsRecord>({ publishedBlogCount: 0, totalReads: 0, tagCount: 0 })
const publishedBlogCount = computed(() => stats.value.publishedBlogCount)
const tagCount = computed(() => stats.value.tagCount)
const totalReads = computed(() => stats.value.totalReads.toLocaleString())

/** 拉取博客列表（搜索/标签/排序/分页变化时触发） */
const fetchBlogs = async () => {
  loading.value = true
  try {
    const sortApi = sortOptions.find((o) => o.key === sortKey.value)?.toApi ?? 'RELEVANCE'
    const query: BlogPortalSearchQuery = {
      keyword: keyword.value.trim() || undefined,
      tagIds: selectedTagIds.value.length ? selectedTagIds.value : undefined,
      sort: sortApi,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const res: NormalizedPageResult<BlogPortalRecord> = await searchBlogsApi(query)
    blogList.value = (res.records ?? []).map((b) => ({
      ...b,
      publishTime: b.publishTime ? formatDateTime(b.publishTime) : b.publishTime,
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
  void fetchBlogs()
}

/** 拉取标签云 + 侧栏热门 + 概览统计（三个独立口径，进页面拉一次，不随搜索/翻页变）。
 *  hotTags 只含热度前 N（已发布内容多的标签）；allTags 取全部启用标签供"全部标签"云展示，
 *  云计数徽标用 hotCountMap 回填（不在热度榜的标签显示 0）。 */
const fetchTagsHotAndStats = async () => {
  const [hotRes, allRes, hotNotesRes, statsRes] = await Promise.all([
    hotTagsApi(50),
    listEnabledTagsApi(),
    recommendBlogsApi(5),
    getBlogStatsApi(),
  ])
  hotTags.value = hotRes.data ?? []
  allTags.value = allRes.data ?? []
  hotNotes.value = (hotNotesRes.data ?? []).map((b) => ({
    ...b,
    publishTime: b.publishTime ? formatDateTime(b.publishTime) : b.publishTime,
  }))
  if (statsRes.data) stats.value = statsRes.data
}

/** 搜索（按钮/回车触发，非实时输入触发）：回第一页重新拉取 */
const onSearch = () => {
  pageNum.value = 1
  void fetchBlogs()
}

/** 标签/排序变化时回到第一页重新拉取（搜索走显式按钮/回车触发，不实时监听 keyword） */
watch([selectedTagIds, sortKey], () => {
  pageNum.value = 1
  void fetchBlogs()
})

onMounted(() => {
  void fetchTagsHotAndStats()
  void fetchBlogs()
})
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
            <input
              v-model="keyword"
              class="notes__search-input"
              placeholder="模糊搜索博客标题或摘要…"
              @keyup.enter="onSearch"
            />
            <button class="notes__search-btn" type="button" @click="onSearch">搜索</button>
          </div>

          <!-- 标签云：全部启用标签（/portal/tag/list，不受热度/已发布内容限制）；
               超出 TAG_LIMIT 折叠进"更多"下拉菜单；计数徽标由 hotCountMap 回填 -->
          <div class="notes__tagcloud">
            <span class="notes__tagcloud-label">
              <KhIcon name="tag" :size="14" /> 全部标签
            </span>
            <button
              v-for="t in visibleTags"
              :key="t.tagId"
              class="notes__tagchip"
              :class="{ 'is-active': selectedTagIds.includes(t.tagId) }"
              type="button"
              @click="toggleTag(t.tagId)"
            >
              {{ t.tagName }}
              <span class="notes__tagchip-count">{{ hotCountMap.get(t.tagId) ?? 0 }}</span>
            </button>
            <span v-if="!allTags.length" class="notes__tagcloud-empty">暂无标签</span>
            <el-dropdown
              v-if="hasMoreTags"
              ref="moreDropdownRef"
              trigger="click"
              placement="bottom-start"
              popper-class="notes__tagcloud-popper"
              @command="onMoreTagCommand"
            >
              <button class="notes__tag-more" type="button">
                更多 ({{ allTags.length - TAG_LIMIT }})
                <el-icon><ArrowDown /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="t in moreTags"
                    :key="t.tagId"
                    :command="t.tagId"
                    :class="{ 'is-active': selectedTagIds.includes(t.tagId) }"
                  >
                    {{ t.tagName }}
                    <span class="notes__tagchip-count">{{ hotCountMap.get(t.tagId) ?? 0 }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
              <div class="notes__overview-value">{{ tagCount }}</div>
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
          <div class="notes__count">共 <b>{{ total }}</b> 篇笔记</div>
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

        <div v-if="blogList.length" class="notes__list">
          <BlogRow v-for="b in blogList" :key="b.blogId" :blog="b" />
        </div>
        <KhCard v-else-if="!loading" padding="lg" class="notes__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的笔记，换个标签或关键词试试</p>
        </KhCard>

        <div v-if="blogList.length" class="notes__pager">
          <KhPagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total" @change="onPageChange" />
        </div>
      </div>

      <!-- 侧栏 -->
      <aside class="notes__aside">
        <KhCard padding="md" class="notes__panel">
          <KhSectionTitle title="标签排行" />
          <ol v-if="tagRanking.length" class="notes__rank">
            <li v-for="(t, i) in tagRanking" :key="t.tagId" class="notes__rank-item" @click="toggleTag(t.tagId)">
              <span class="notes__rank-no">{{ i + 1 }}</span>
              <span class="notes__rank-name">
                <span class="notes__rank-name-inner" :class="{ 'is-marquee': isTagNameLong(t.tagName) }">
                  <template v-if="isTagNameLong(t.tagName)">
                    <span class="notes__rank-name-unit">{{ t.tagName }}</span>
                    <span class="notes__rank-name-unit">{{ t.tagName }}</span>
                  </template>
                  <template v-else>{{ t.tagName }}</template>
                </span>
              </span>
              <span class="notes__rank-bar">
                <span class="notes__rank-bar-fill" :style="{ width: `${((t.contentCount ?? 0) / (tagRanking[0]?.contentCount ?? 1)) * 100}%` }" />
              </span>
              <span class="notes__rank-count">{{ t.contentCount ?? 0 }}</span>
            </li>
          </ol>
          <div v-else class="notes__panel-empty">
            <KhIcon name="tag" :size="28" :stroke="1.4" />
            <p>暂无标签数据</p>
          </div>
        </KhCard>

        <KhCard padding="md" class="notes__panel">
          <KhSectionTitle title="近期热门" />
          <ul v-if="hotNotes.length" class="notes__hot">
            <li v-for="(b, i) in hotNotes" :key="b.blogId" class="notes__hot-item" @click="$router.push(`/blog/${b.blogId}`)">
              <span class="notes__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="notes__hot-text">
                <div class="notes__hot-title kh-line-clamp-2">{{ b.title }}</div>
                <div class="notes__hot-meta">{{ b.viewCount ?? 0 }} 阅读<span v-if="b.authorNickname"> · {{ b.authorNickname }}</span></div>
              </div>
            </li>
          </ul>
          <div v-else class="notes__panel-empty">
            <KhIcon name="blog" :size="28" :stroke="1.4" />
            <p>暂无热门笔记</p>
          </div>
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
.notes__tagcloud-empty {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  padding: 4px 0;
}
.notes__tag-more {
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
.notes__tag-more:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }

.notes__body {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  margin-top: var(--kh-space-10);
  align-items: stretch;
}
.notes__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
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
  justify-content: center;
  gap: var(--kh-space-3);
  /* 空态撑满左侧主体到与右侧侧栏齐高（grid align-items:stretch + main flex 列） */
  flex: 1;
  min-height: 320px;
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
  flex: none;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.notes__rank-name-inner {
  display: inline-block;
  white-space: nowrap;
}
/* 长标签名循环滚动（照首页范式）：两个 unit 各带 4em 间隔横向平移 -50% 无缝循环。
   mask 仅挂滚动态——短名不滚，左右清晰不虚化。hover 暂停便于看清。 */
.notes__rank-name-inner.is-marquee {
  animation: kh-notes-rank-marquee 14s linear infinite;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
}
.notes__rank-name-inner.is-marquee:hover {
  animation-play-state: paused;
}
.notes__rank-name-unit {
  margin-right: 4em;
}
@keyframes kh-notes-rank-marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
@media (prefers-reduced-motion: reduce) {
  .notes__rank-name-inner.is-marquee { animation: none; }
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

/* 侧栏空态：标签排行/近期热门无数据时占位 */
.notes__panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-6) 0;
  color: var(--kh-text-tertiary);
  text-align: center;
}
.notes__panel-empty p {
  font-size: 12px;
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

<!-- 非 scoped：el-dropdown popper 被 teleport 到 body，scoped 属性选择器打不到，需全局样式 -->
<style>
.notes__tagcloud-popper {
  max-height: 320px;
  overflow-y: auto;
  padding: 6px 0;
}
.notes__tagcloud-popper .el-dropdown-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  border: none;
  box-shadow: none;
  background: transparent;
}
.notes__tagcloud-popper .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 180px;
  padding: 6px 14px;
  font-size: 13px;
  border-radius: var(--kh-radius-sm);
}
.notes__tagcloud-popper .el-dropdown-menu__item.is-active {
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
  font-weight: 600;
}
.notes__tagcloud-popper .notes__tagchip-count {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.notes__tagcloud-popper .el-dropdown-menu__item.is-active .notes__tagchip-count {
  color: var(--kh-primary);
}
</style>
