<!--
  文章章节内容页 /docs/:id/read
  ------------------------------------------------------------------
  类 Vue 官方文档站：左章节目录（当前章高亮、可点切换）/ 中正文（v-md-preview 渲染，空正文回退占位）/
  右本章内容大纲卡（从正文 ## 标题提取）。章节标题放大、下方留白加大。底部上/下章导航。
  返回按钮回上一级（文章介绍页 /docs/:id），非历史页。
-->
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhContentToc from '@/components/common/KhContentToc.vue'
import { getDocById, docs, type MockDocChapter } from '@/mock/doc'

const route = useRoute()
const router = useRouter()

const docId = computed(() => Number(route.params.id))
const doc = computed(() => getDocById(docId.value) ?? docs[0]!)

/** 当前章 id：URL query ?ch= 优先，缺省第一章 */
const activeChapterId = ref<number>(Number(route.query.ch) || (doc.value.chapters[0]?.id ?? 0))

// 切文档时重置当前章
watch(docId, () => {
  activeChapterId.value = Number(route.query.ch) || (doc.value.chapters[0]?.id ?? 0)
})

const activeChapter = computed<MockDocChapter | undefined>(() =>
  doc.value.chapters.find((c) => c.id === activeChapterId.value),
)

/** 选章：切本地状态 + 同步到 URL query，便于分享/刷新保持 */
const selectChapter = (c: MockDocChapter) => {
  activeChapterId.value = c.id
  router.replace({ query: { ...route.query, ch: String(c.id) } })
}

/** 上下章导航 */
const chapterIndex = computed(() => doc.value.chapters.findIndex((c) => c.id === activeChapterId.value))
const isFirst = computed(() => chapterIndex.value <= 0)
const isLast = computed(() => chapterIndex.value >= doc.value.chapters.length - 1)
const prevChapter = computed(() => (chapterIndex.value > 0 ? doc.value.chapters[chapterIndex.value - 1] : undefined))
const nextChapter = computed(() => {
  const i = chapterIndex.value
  return i >= 0 && i < doc.value.chapters.length - 1 ? doc.value.chapters[i + 1] : undefined
})

const goIntro = () => router.push(`/docs/${docId.value}`)

/** 当前章内容大纲：从正文提取 ## 二级标题，供右栏大纲卡展示（无标题则空） */
const chapterToc = computed<string[]>(() => {
  const content = activeChapter.value?.content ?? ''
  return content
    .split('\n')
    .filter((l) => l.startsWith('## '))
    .map((l) => l.replace(/^##\s+/, '').trim())
    .slice(0, 10)
})

/** 正文容器 DOM 引用：目录跳转靠 querySel 取第 idx 个 h2 */
const contentRef = ref<HTMLElement | null>(null)

/**
 * 点击目录项 i：取正文容器内第 i 个 h2 平滑滚动定位。
 * 同博客详情页：v-md-preview github 主题不给 heading 加 id，靠 .github-markdown-body 下
 * querySelectorAll('h2') 按 DOM 顺序取第 i 个，序号与 chapterToc 提取顺序一一对齐。
 */
const handleTocSelect = (idx: number) => {
  const root = contentRef.value
  if (!root) return
  nextTick(() => {
    const hs = root.querySelectorAll<HTMLElement>(':scope .github-markdown-body h2')
    hs[idx]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
</script>

<template>
  <div class="dr">
    <!-- 精简面包屑：返回按钮回上一级（文章介绍页 /docs/:id），非历史页 -->
    <div class="kh-container kh-container--wide dr__crumb">
      <button class="dr__back" type="button" @click="goIntro">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/docs">文档学习</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink :to="`/docs/${docId}`">{{ doc.title }}</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="dr__crumb-current">{{ activeChapter?.title }}</span>
    </div>

    <!-- 主体：左章节目录 + 中正文 + 右章节内容大纲 -->
    <div class="kh-container kh-container--wide dr__layout">
      <!-- 左：章节目录 sticky -->
      <aside class="dr__toc">
        <div class="dr__toc-head"><KhIcon name="doc" :size="16" /> 章节</div>
        <ul class="dr__toc-list">
          <li
            v-for="c in doc.chapters"
            :key="c.id"
            class="dr__toc-item"
            :class="{ 'is-active': c.id === activeChapterId }"
            @click="selectChapter(c)"
          >
            <span class="dr__toc-no">{{ String(c.id).padStart(2, '0') }}</span>
            <span class="dr__toc-text">{{ c.title }}</span>
          </li>
        </ul>
      </aside>

      <!-- 中：正文 -->
      <article class="dr__main">
        <!-- 章节标题（再放大一档，下方留白加大） -->
        <h1 class="dr__chapter-title">{{ activeChapter?.title }}</h1>

        <!-- 正文：v-md-preview 渲染 markdown；空正文回退占位 -->
        <div v-if="activeChapter?.content" ref="contentRef" class="dr__content">
          <v-md-preview :text="activeChapter.content" />
        </div>
        <div v-else class="dr__placeholder">
          <KhIcon name="doc" :size="40" :stroke="1.4" />
          <p>该章节正文待补 · 章节接口接入后渲染实际内容</p>
          <p class="dr__placeholder-hint">当前文档仅模拟章节结构，正文以首个 Spring Boot 文档为完整示例。</p>
        </div>

        <!-- 上下章导航：上一章朝上箭头 / 下一章朝下箭头；端章（首/末）单按钮居中 -->
        <div class="dr__nav" :class="{ 'is-first': isFirst && !isLast, 'is-last': isLast && !isFirst }">
          <button v-if="prevChapter" class="dr__nav-btn dr__nav-btn--prev" type="button" @click="selectChapter(prevChapter)">
            <el-icon class="dr__nav-arrow"><ArrowUp /></el-icon>
            <span class="dr__nav-body">
              <span class="dr__nav-label">上一章</span>
              <span class="dr__nav-title kh-line-clamp-1">{{ prevChapter.title }}</span>
            </span>
          </button>
          <button v-if="nextChapter" class="dr__nav-btn dr__nav-btn--next" type="button" @click="selectChapter(nextChapter)">
            <span class="dr__nav-body">
              <span class="dr__nav-label">下一章</span>
              <span class="dr__nav-title kh-line-clamp-1">{{ nextChapter.title }}</span>
            </span>
            <el-icon class="dr__nav-arrow"><ArrowDown /></el-icon>
          </button>
        </div>
      </article>

      <!-- 右：本章目录卡（复用 KhContentToc，与博客详情目录同款；无小标题则不显示） -->
      <aside v-if="chapterToc.length" class="dr__subtoc">
        <KhContentToc :items="chapterToc" title="目录" @select="handleTocSelect" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.dr__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.dr__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  margin-right: var(--kh-space-3);
  font-size: 12px;
}
.dr__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.dr__crumb a {
  color: var(--kh-text-secondary);
}
.dr__crumb a:hover {
  color: var(--kh-primary);
}
.dr__crumb-sep {
  color: var(--kh-text-tertiary);
}
.dr__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}

/* 主体三栏：左章节目录 / 中正文 / 右本章大纲 */
.dr__layout {
  display: grid;
  grid-template-columns: 240px 1fr 220px;
  gap: var(--kh-space-8);
  align-items: start;
  padding-bottom: var(--kh-space-12);
}

/* 左：章节目录 sticky */
.dr__toc {
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
  max-height: calc(100vh - var(--kh-header-height) - var(--kh-space-8));
  overflow-y: auto;
}
.dr__toc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
  padding: 0 var(--kh-space-3);
}
.dr__toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dr__toc-item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.dr__toc-item:hover {
  background: var(--kh-surface-muted);
}
.dr__toc-item.is-active {
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.dr__toc-no {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
  padding-top: 1px;
}
.dr__toc-item.is-active .dr__toc-no {
  color: var(--kh-primary);
}
.dr__toc-text {
  font-size: 13px;
  color: var(--kh-text-secondary);
  line-height: 1.4;
}
.dr__toc-item.is-active .dr__toc-text {
  color: var(--kh-primary-strong);
  font-weight: 500;
}

/* 右：正文 */
.dr__main {
  min-width: 0;
  padding: 0 var(--kh-space-2);
}
/* 章节标题放大：比介绍页卡头标题更大一档，作正文页主标 */
.dr__chapter-title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 800;
  letter-spacing: -0.01em;
  line-height: 1.25;
  margin-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-6);
  border-bottom: 1px solid var(--kh-border-soft);
  margin-bottom: var(--kh-space-8);
}
/* v-md-preview github 主题根类是 github-markdown-body，清掉左右内边距对齐正文、去一二级标题下横线 */
.dr__content {
  color: var(--kh-text);
}
.dr__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
.dr__content :deep(.github-markdown-body h1),
.dr__content :deep(.github-markdown-body h2) {
  border-bottom: none;
}
/* 目录 scrollIntoView 落点留出头导航高度，否则 sticky 顶栏会遮住滚到顶的标题 */
.dr__content :deep(.github-markdown-body h2),
.dr__content :deep(.github-markdown-body h3) {
  scroll-margin-top: calc(var(--kh-header-height) + var(--kh-space-4));
}

.dr__placeholder {
  text-align: center;
  color: var(--kh-text-tertiary);
  padding: var(--kh-space-12) var(--kh-space-5);
  background: var(--kh-surface-muted);
  border: 1px dashed var(--kh-border);
  border-radius: var(--kh-radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
}
.dr__placeholder p {
  margin: 0;
  font-size: var(--kh-font-size-sm);
}
.dr__placeholder-hint {
  color: var(--kh-text-tertiary);
  font-size: 12px;
}

/* 上下章导航：上一章朝上 / 下一章朝下；端章单按钮居中 */
.dr__nav {
  display: flex;
  justify-content: space-between;
  gap: var(--kh-space-4);
  margin-top: var(--kh-space-10);
  padding-top: var(--kh-space-5);
  border-top: 1px solid var(--kh-border-soft);
}
/* 端章：只剩一个按钮时，让其居中而非贴边 */
.dr__nav.is-first,
.dr__nav.is-last {
  justify-content: center;
}
.dr__nav.is-first .dr__nav-btn,
.dr__nav.is-last .dr__nav-btn {
  flex: 0 1 auto;
  max-width: 60%;
}
.dr__nav-btn {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex: 1;
  min-width: 0;
  padding: var(--kh-space-4) var(--kh-space-5);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius);
  background: var(--kh-surface);
  cursor: pointer;
  text-align: left;
  transition: all var(--kh-transition-fast);
}
.dr__nav-btn:hover {
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
.dr__nav-btn--next {
  text-align: right;
  justify-content: flex-end;
}
.dr__nav-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.dr__nav-arrow {
  flex: none;
  color: var(--kh-text-tertiary);
}
.dr__nav-btn:hover .dr__nav-arrow {
  color: var(--kh-primary);
}
.dr__nav-label {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
}
.dr__nav-title {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}

/* 右：本章内容大纲卡 sticky */
.dr__subtoc {
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
  max-height: calc(100vh - var(--kh-header-height) - var(--kh-space-8));
  overflow-y: auto;
}

@media (max-width: 1280px) {
  /* 中等屏去掉右栏目录，回两栏 */
  .dr__layout {
    grid-template-columns: 240px 1fr;
  }
  .dr__subtoc {
    display: none;
  }
}
@media (max-width: 1024px) {
  .dr__layout {
    grid-template-columns: 1fr;
  }
  .dr__toc {
    position: static;
    max-height: none;
    border-bottom: 1px solid var(--kh-border-soft);
    padding-bottom: var(--kh-space-4);
  }
}
</style>