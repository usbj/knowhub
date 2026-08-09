<!--
  资源推荐 /resources
  ------------------------------------------------------------------
  真实接口驱动：主列表 searchResourcesApi（全文+类型/分类复合过滤+排序+分页），
  分类筛选用 resource_category_id 分类树（获自 getResourceCategoryTreeApi，-1=其他前端硬编码），
  类型筛选 FILE/LINK/全部；侧栏"热门下载榜"`sort=HOT`、"最近上传"`sort=LATEST`，均复用 search 接口。
  资源无标签体系、无 level 等级（与博客门户差异点）：SQL 铁律 PUBLISHED AND deleted=0。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElOption, ElSelect, ElPagination } from 'element-plus'
import KhCard from '@/components/common/KhCard.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import ResourceCard from '@/components/resource/ResourceCard.vue'
import { useRouter } from 'vue-router'
import { searchResourcesApi, getResourceCategoryTreeApi } from '@/api/knowhub/resource-portal'
import type {
  ResourcePortalRecord,
  ResourcePortalSearchQuery,
  ResourceCategoryTreeNode,
} from '@/types/api/knowhub/resource'
import { formatDate } from '@/utils/format'
import toast from '@/utils/toast'

const router = useRouter()

/** 排序维度（驱动主列表默认与切换；侧栏榜各自固定 HOT/LATEST 独立请求） */
const sortMode = ref<'HOT' | 'LATEST'>('HOT')
/** 分类多选过滤：number[]，空数组=全部。-1=其他前端硬编码追加为叶子选项 */
const categoryFilter = ref<number[]>([])
const typeFilter = ref<'ALL' | 'FILE' | 'LINK'>('ALL')
const keyword = ref('')
/** 搜索框当前输入态（与生效态 keyword 分离，做点击/回车搜索而非实时） */
const keywordInput = ref('')
const pageNum = ref(1)
const pageSize = ref(12)

const list = ref<ResourcePortalRecord[]>([])
const total = ref(0)
const loading = ref(false)

/** 分类树展平为叶子列表（含"其他"虚拟节点 -1）供下拉多选用 */
const categoryTree = ref<ResourceCategoryTreeNode[]>([])
const categorySelectOptions = computed(() => {
  const flatten = (nodes: ResourceCategoryTreeNode[]): { id: number; label: string }[] =>
    nodes.flatMap((n) => [
      { id: n.categoryId, label: n.categoryName },
      ...(n.children ? flatten(n.children) : []),
    ])
  return [...flatten(categoryTree.value), { id: -1, label: '其他' }]
})

/** 拉分类树（仅一次，上传/筛选共用数据源） */
const fetchCategoryTree = async () => {
  try {
    const res = await getResourceCategoryTreeApi()
    categoryTree.value = res.data ?? []
  } catch {
    categoryTree.value = []
  }
}

/** 拉主列表（search，分页+复合过滤+排序） */
const fetchList = async () => {
  loading.value = true
  try {
    const query: ResourcePortalSearchQuery = {
      sort: sortMode.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    if (keyword.value.trim()) query.keyword = keyword.value.trim()
    if (typeFilter.value !== 'ALL') query.resourceType = typeFilter.value
    if (categoryFilter.value.length) query.resourceCategoryIds = categoryFilter.value
    const res = await searchResourcesApi(query)
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 侧栏榜：独立请求，固定 sort + pageSize，不带 keyword/分类（纯热度 feed） */
const hotList = ref<ResourcePortalRecord[]>([])
const recentList = ref<ResourcePortalRecord[]>([])
const fetchSidebars = async () => {
  try {
    const [hot, recent] = await Promise.all([
      searchResourcesApi({ sort: 'HOT', pageSize: 6 }),
      searchResourcesApi({ sort: 'LATEST', pageSize: 5 }),
    ])
    hotList.value = hot.records ?? []
    recentList.value = recent.records ?? []
  } catch {
    hotList.value = []
    recentList.value = []
  }
}

/** 分类多选选中态改变（el-select @change）：回第一页重拉 */
const onCategoryChange = () => {
  pageNum.value = 1
  void fetchList()
}
const switchType = (t: 'ALL' | 'FILE' | 'LINK') => {
  if (typeFilter.value === t) return
  typeFilter.value = t
  pageNum.value = 1
  void fetchList()
}
const switchSort = (s: 'HOT' | 'LATEST') => {
  if (sortMode.value === s) return
  sortMode.value = s
  pageNum.value = 1
  void fetchList()
}

/**
 * 正常点击搜索（用户明确要求不要实时）：
 * - 点搜索按钮 or 回车：把 keywordInput 提交到 keyword，触发拉取；回第一页。
 * - keywordInput 与 keyword 分离让 input 不即时驱动请求；只在提交时同步。
 */
const submitSearch = () => {
  const next = keywordInput.value.trim()
  if (next === keyword.value) return
  keyword.value = next
  pageNum.value = 1
  void fetchList()
}
const onSearchEnter = () => submitSearch()
/** 清空搜索：keywordInput/keyword 同清，重新拉不命中 keyword 列表 */
const onSearchClear = () => {
  keywordInput.value = ''
  keyword.value = ''
  pageNum.value = 1
  void fetchList()
}

const onPageChange = (p: number) => {
  pageNum.value = p
  void fetchList()
}
const goDetail = (id: number) => router.push(`/resource/${id}`)

/** 字节大小 → B/KB/MB/GB，与 ResourceCard / 资源详情 formatSize 口径一致 */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}
const hotIcon = (r: ResourcePortalRecord) => (r.resourceType === 'LINK' ? 'link' : 'file')
const hotCover = (r: ResourcePortalRecord) =>
  r.resourceType === 'LINK' ? 'linear-gradient(135deg,#2563eb,#0ea5e9)' : 'linear-gradient(135deg,#6366f1,#a5b4fc)'

/** 已选分类标签展示前缀（多少个分类） */
const hasCategoryFilter = computed(() => categoryFilter.value.length > 0)

onMounted(() => {
  void fetchCategoryTree()
  void fetchList()
  void fetchSidebars()
})

// 侧栏榜取数与主列表独立，主列表过滤变化不重拉侧栏（与原 mock 行为保持一致）
void formatDate
void toast
</script>

<template>
  <div class="resources">
    <section class="res__hero">
      <div class="kh-container kh-container--wide">
        <h1 class="res__title">资源推荐</h1>
        <p class="res__subtitle">软件、脚本、文档、工具网站链接 · 实验室精选优秀资源</p>

        <!-- 搜索：点按钮/回车搜索，不做实时 -->
        <div class="res__search">
          <el-icon class="res__search-icon"><Search /></el-icon>
          <input
            v-model="keywordInput"
            class="res__search-input"
            placeholder="搜索资源标题、摘要、详细说明…"
            @keyup.enter="onSearchEnter"
          />
          <button v-if="keywordInput" class="res__search-clear" type="button" aria-label="清空" @click="onSearchClear">×</button>
          <button class="res__search-btn" type="button" @click="submitSearch">搜索</button>
        </div>
      </div>
    </section>

    <section class="kh-container kh-container--wide res__body">
      <!-- 主体 -->
      <div class="res__main">
        <div class="res__toolbar">
          <!-- 分类多选下拉：分类后续可增，下拉比 chip 列更稳；-1=其他前端硬编码叶子 -->
          <ElSelect
            v-model="categoryFilter"
            multiple
            collapse-tags
            collapse-tags-tooltip
            filterable
            clearable
            placeholder="全部分类"
            class="res__category-select"
            @change="onCategoryChange"
          >
            <ElOption
              v-for="c in categorySelectOptions"
              :key="c.id"
              :label="c.label"
              :value="c.id"
            />
          </ElSelect>

          <div class="res__filter-group">
            <button
              class="res__filter-btn"
              :class="{ 'is-active': typeFilter === 'ALL' }"
              type="button"
              @click="switchType('ALL')"
            >全部类型</button>
            <button
              class="res__filter-btn"
              :class="{ 'is-active': typeFilter === 'FILE' }"
              type="button"
              @click="switchType('FILE')"
            ><KhIcon name="file" :size="13" /> 文件</button>
            <button
              class="res__filter-btn"
              :class="{ 'is-active': typeFilter === 'LINK' }"
              type="button"
              @click="switchType('LINK')"
            ><KhIcon name="link" :size="13" /> 链接</button>
          </div>
        </div>

        <div class="res__sortbar">
          <button
            class="res__sort-btn"
            :class="{ 'is-active': sortMode === 'HOT' }"
            type="button"
            @click="switchSort('HOT')"
          >🔥 热度</button>
          <button
            class="res__sort-btn"
            :class="{ 'is-active': sortMode === 'LATEST' }"
            type="button"
            @click="switchSort('LATEST')"
          >⏱ 最近上传</button>
          <div class="res__count">
            共 <b>{{ total }}</b> 个资源<span v-if="hasCategoryFilter"> · 已筛 {{ categoryFilter.length }} 个分类</span>
          </div>
        </div>

        <div v-if="loading" class="res__empty">
          <p>加载中…</p>
        </div>
        <div v-else-if="list.length" class="res__grid">
          <ResourceCard v-for="r in list" :key="r.resourceId" :resource="r" />
        </div>
        <KhCard v-else padding="lg" class="res__empty">
          <KhIcon name="search" :size="40" :stroke="1.4" />
          <p>没有匹配的资源</p>
        </KhCard>

        <!-- 分页：始终展示，便于用户翻页（即使当前页数=1组件内部会自适应） -->
        <div class="res__pager">
          <ElPagination
            layout="prev, pager, next, total"
            :current-page="pageNum"
            :page-size="pageSize"
            :total="total"
            :hide-on-single-page="false"
            background
            @current-change="onPageChange"
          />
        </div>
      </div>

      <!-- 侧栏 -->
      <aside class="res__aside">
        <KhCard padding="md" class="res__panel">
          <KhSectionTitle title="热门下载榜" />
          <ol class="res__hot">
            <li v-for="(r, i) in hotList" :key="r.resourceId" class="res__hot-item" @click="goDetail(r.resourceId)">
              <span class="res__hot-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="res__hot-icon" :style="{ background: hotCover(r) }">
                <KhIcon :name="hotIcon(r)" :size="14" />
              </div>
              <div class="res__hot-text">
                <div class="res__hot-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="res__hot-meta">{{ r.downloadCount ?? 0 }} 下载 · {{ formatSize(r.contentLength) }}</div>
              </div>
            </li>
          </ol>
        </KhCard>

        <KhCard padding="md" class="res__panel">
          <KhSectionTitle title="最近上传" />
          <ul class="res__recent">
            <li v-for="r in recentList" :key="r.resourceId" class="res__recent-item" @click="goDetail(r.resourceId)">
              <div class="res__recent-dot" :style="{ background: hotCover(r) }" />
              <div class="res__recent-text">
                <div class="res__recent-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="res__recent-time">{{ formatDate(r.publishTime) }} · {{ r.authorNickname || '匿名' }}</div>
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
  padding: 0 6px 0 var(--kh-space-4);
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
.res__search-clear {
  border: none;
  background: transparent;
  color: var(--kh-text-tertiary);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
}
.res__search-clear:hover {
  background: var(--kh-surface-muted);
  color: var(--kh-text);
}
.res__search-btn {
  height: 38px;
  padding: 0 var(--kh-space-5);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary);
  color: #fff;
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.res__search-btn:hover {
  background: var(--kh-primary-strong);
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
  margin-bottom: var(--kh-space-4);
  flex-wrap: wrap;
  gap: var(--kh-space-3);
}
.res__category-select {
  min-width: 240px;
  flex: 1;
  max-width: 480px;
}
.res__filter-group {
  display: flex;
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
.res__sortbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--kh-space-5);
}
.res__sort-btn {
  padding: 5px 12px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.res__sort-btn.is-active {
  border-color: var(--kh-primary);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.res__count {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  margin-left: auto;
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
@media (max-width: 640px) {
  .res__toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .res__category-select {
    max-width: none;
  }
}
</style>