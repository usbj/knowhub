<!--
  <!--
  文章章节内容页 /article/:id/read/:chapterId
  ------------------------------------------------------------------
  类 Vue 官方文档站：左章节目录（当前章高亮、可点切换）/ 中正文（v-md-preview 渲染章节 markdown）/
  右本章内容大纲卡（从正文 ## 标题提取）。越级锁态：locked=true 时正文不下发，显示锁态提示。
  章节目录来自文章详情接口的 chapterList（点章节切 URL + 拉对应章正文）；
  章节正文走 /portal/article/{id}/chapter/{chapterId} 单独拉取（达权才下发，越级 content 为 null）。
  底部上/下章导航。返回按钮回上一级（文章介绍页 /article/:id）。
-->
<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElImageViewer } from 'element-plus'
import { ArrowLeft, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhContentToc from '@/components/common/KhContentToc.vue'
import KhLoading from '@/components/common/KhLoading.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { useMarkdownCodeBlock } from '@/composables/useMarkdownCodeBlock'
import { getArticleDetailApi, getChapterContentApi } from '@/api/knowhub/article'
import type { ArticlePortalDetailRecord, ChapterContentRecord } from '@/types/api/knowhub/article'
import { useStickyBottom } from '@/utils/use-footer-visible'

const route = useRoute()
const router = useRouter()

/** sticky 侧栏底部预留量：sticky 父容器（.dr__layout）末端进视口时收紧 max-height，
 *  防 sticky aside 上推钻 header；footer 进视口也兜底收缩。 */
const footerVisible = useStickyBottom('.dr__layout')
const stickyBottom = computed(() => `${footerVisible.value}px`)

const docId = computed(() => Number(route.params.id))
const activeChapterId = computed(() => Number(route.params.chapterId))

/** 文章详情（含章节大纲 chapterList + 标题） */
const doc = ref<ArticlePortalDetailRecord | null>(null)
/** 当前章节正文 */
const chapter = ref<ChapterContentRecord | null>(null)
/**
 * 整页加载态：首屏 + 切文章时罩整页 KhLoading，等 doc 与 chapter 都就绪再统一撤走，避免 doc 先回章节目录冒出、
 * chapter 仍在跑且正文区被 KhLoading 盖住的"数据盖动画"割裂感。切章节（同文章）不触发，走 chapterLoading。
 */
const docLoading = ref(true)
/** 正文区加载态：切章节（同文章）时只盖正文，左栏目录保持不动，避免整页闪一下 */
const chapterLoading = ref(false)

/** 拉文章详情（章节目录来源，越级锁态时 chapterList 仍下发，目录照常可用） */
const fetchDoc = async () => {
  const res = await getArticleDetailApi(docId.value)
  doc.value = res.data ?? null
}

/** 拉当前章节正文 */
const fetchChapter = async () => {
  const res = await getChapterContentApi(docId.value, activeChapterId.value)
  chapter.value = res.data ?? null
}

/** 章节目录列表（来自文章详情的 chapterList） */
const chapters = computed(() => doc.value?.chapterList ?? [])

/** 当前章 index（用于上下章导航） */
const chapterIndex = computed(() =>
  chapters.value.findIndex((c) => c.chapterId === activeChapterId.value),
)
const isFirst = computed(() => chapterIndex.value <= 0)
const isLast = computed(() => chapterIndex.value >= chapters.value.length - 1)
const prevChapter = computed(() =>
  chapterIndex.value > 0 ? chapters.value[chapterIndex.value - 1] : undefined,
)
const nextChapter = computed(() => {
  const i = chapterIndex.value
  return i >= 0 && i < chapters.value.length - 1 ? chapters.value[i + 1] : undefined
})

/** 选章：切 URL 路径段，触发 watch 重拉正文 */
const selectChapter = (chapterId: number) => {
  if (chapterId === activeChapterId.value) return
  router.push(`/article/${docId.value}/read/${chapterId}`)
}

const goIntro = () => router.push(`/article/${docId.value}`)

/** 当前章内容大纲：从正文提取 ##/###/#### 标题（含层级），供右栏目录树展示（无标题则空；越级锁态时正文为空 → 目录空）。
 *  用 TocItem{level,text} 扁平按出现顺序入参，组件按 level 缩进渲染成 CSDN 风格目录树，覆盖 h2/h3/h4 全层级。 */
const chapterToc = computed<{ level: number; text: string }[]>(() => {
  const content = chapter.value?.content ?? ''
  const out: { level: number; text: string }[] = []
  for (const line of content.split('\n')) {
    const m = /^(#{2,4})\s+(.+)$/.exec(line)
    if (m && m[1] && m[2]) {
      out.push({ level: m[1].length, text: m[2].trim() })
    }
  }
  return out
})

const contentRef = ref<HTMLElement | null>(null)

/** 章节正文配图点击放大（与评论区/博客正文同款 el-image-viewer）：复用 contentRef，
 *  onContentClick 只 querySelectorAll('img')，与 handleTocSelect/computeActive 查 h2/h3/h4 正交不冲突。 */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
// 代码块增强（语言标签 + 复制按钮）：与配图放大/TOC scroll-spy 共用同一 contentRef，正交不冲突
useMarkdownCodeBlock(contentRef)

/** 点击目录项 i：取正文容器内第 i 个 h2/h3/h4（按 DOM 顺序与 toc 提取顺序一一对应）平滑滚动定位 */
const handleTocSelect = (idx: number) => {
  const root = contentRef.value
  if (!root) return
  nextTick(() => {
    const hs = root.querySelectorAll<HTMLElement>(':scope .github-markdown-body h2, :scope .github-markdown-body h3, :scope .github-markdown-body h4')
    hs[idx]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

/**
 * Scroll spy：监听 window scroll，取离视口顶部（留出 header 高度的偏移线）最近且已越过该线的标题，
 * 作为当前阅读标题序号 → 驱动 KhContentToc 高亮 + 自动展开祖先链。
 * - 偏移线 = header 高度 + 一点缓冲；标题在该线上方（getBoundingClientRect().top < 偏移线）就算"已读过"。
 * - 取最后一个已越过偏移线的标题（向下滚时高亮顺次往下）；向上滚同理，最近的已过线标题会变回上方，自然回退到上一个。
 * - 节流：rAF，避免 scroll 高频 setState。
 */
const activeTocIndex = ref(-1)
let spyRaf = 0
const SPY_OFFSET = () => {
  const v = getComputedStyle(document.documentElement).getPropertyValue('--kh-header-height').trim()
  return (parseFloat(v) || 64) + 24
}
const computeActive = () => {
  const root = contentRef.value
  if (!root) return
  const hs = root.querySelectorAll<HTMLElement>(':scope .github-markdown-body h2, :scope .github-markdown-body h3, :scope .github-markdown-body h4')
  if (!hs.length) { activeTocIndex.value = -1; return }
  const line = SPY_OFFSET()
  let idx = -1
  hs.forEach((h, i) => {
    if (h.getBoundingClientRect().top < line) idx = i
  })
  // 全部都在偏移线下方（页面顶部、未滚到任何标题）→ 高亮第一个
  if (idx === -1) idx = 0
  if (idx !== activeTocIndex.value) activeTocIndex.value = idx
}
const onScroll = () => {
  if (spyRaf) return
  spyRaf = requestAnimationFrame(() => {
    spyRaf = 0
    computeActive()
  })
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (spyRaf) cancelAnimationFrame(spyRaf)
})
onActivated(() => nextTick(computeActive))

/**
 * 整页加载：首屏 + 切文章时并发拉 doc 与 chapter，Promise.all 等两者都就绪再撤 docLoading，
 * 避免 doc 先回章节目录盖在 KhLoading 上的割裂感。失败也撤 docLoading（交由 v-if 兜底空态）。
 */
const loadAll = async () => {
  docLoading.value = true
  try {
    await Promise.all([fetchDoc(), fetchChapter()])
  } finally {
    docLoading.value = false
  }
}

/** 切文章时整页重载（doc + chapter 一起等齐）；切章节时只重拉正文（左栏不动） */
watch(docId, () => { void loadAll() })
watch(activeChapterId, () => {
  chapterLoading.value = true
  void fetchChapter()
    .then(() => nextTick(computeActive))
    .finally(() => { chapterLoading.value = false })
})

// 首屏整页加载：doc + chapter 都就绪再统一展示
void loadAll()
</script>

<template>
  <div class="dr">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide dr__crumb">
      <button class="dr__back" type="button" @click="goIntro">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/articles">文档学习</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink :to="`/article/${docId}`">{{ doc?.title }}</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="dr__crumb-current">{{ chapter?.chapterName }}</span>
    </div>

    <!-- 整页加载占位：首屏/切文章时罩整页 KhLoading，doc+chapter 都就绪再统一撤走（避免数据盖动画） -->
    <KhLoading v-if="docLoading" title="正在加载章节…" class="dr__page-loading" />

    <!-- 主体三栏 -->
    <div v-else class="kh-container kh-container--wide dr__layout">
      <!-- 左：章节目录 sticky（随 footer 出现而缩短，--kh-sticky-bottom 由 footer observer 驱动） -->
      <aside class="dr__toc" :style="{ '--kh-sticky-bottom': stickyBottom }">
        <div class="dr__toc-head"><KhIcon name="doc" :size="16" /> 章节</div>
        <ul class="dr__toc-list">
          <li
            v-for="(c, i) in chapters"
            :key="c.chapterId"
            class="dr__toc-item"
            :class="{ 'is-active': c.chapterId === activeChapterId }"
            @click="selectChapter(c.chapterId)"
          >
            <span class="dr__toc-no">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="dr__toc-text">{{ c.chapterName }}</span>
          </li>
        </ul>
        <div v-if="!chapters.length" class="dr__toc-empty">暂无章节</div>
      </aside>

      <!-- 中：正文 -->
      <article class="dr__main">
        <!-- 章节标题 -->
        <h1 class="dr__chapter-title">{{ chapter?.chapterName }}</h1>

        <!-- 越级锁态：整片锁定卡片（不展示预览正文，直接锁整片区域提示需更高权限） -->
        <div v-if="chapter?.locked" class="dr__locked">
          <span class="dr__lock-iconwrap"><KhIcon name="lock" :size="34" :stroke="1.5" /></span>
          <p class="dr__locked-title">{{ chapter.lockReason ?? '需更高权限查看完整内容' }}</p>
          <p class="dr__locked-hint">登录并拥有对应等级权限后可查看本章正文</p>
        </div>
        <!-- 正文：v-md-preview 渲染章节 markdown -->
        <div v-else-if="chapter?.content" ref="contentRef" class="dr__content" @click="onContentClick">
          <v-md-preview :text="chapter.content" />
        </div>
        <!-- 章节正文加载中（切章节时只盖正文，左栏不动） -->
        <div v-else-if="chapterLoading" class="dr__loading">
          <KhLoading title="正在加载章节…" />
        </div>
        <!-- 章节无正文（内容待补） -->
        <div v-else class="dr__placeholder">
          <KhIcon name="doc" :size="40" :stroke="1.4" />
          <p>该章节正文待补</p>
        </div>

        <!-- 上下章导航 -->
        <div class="dr__nav" :class="{ 'is-first': isFirst && !isLast, 'is-last': isLast && !isFirst }">
          <button v-if="prevChapter" class="dr__nav-btn dr__nav-btn--prev" type="button" @click="selectChapter(prevChapter.chapterId)">
            <el-icon class="dr__nav-arrow"><ArrowUp /></el-icon>
            <span class="dr__nav-body">
              <span class="dr__nav-label">上一章</span>
              <span class="dr__nav-title kh-line-clamp-1">{{ prevChapter.chapterName }}</span>
            </span>
          </button>
          <button v-if="nextChapter" class="dr__nav-btn dr__nav-btn--next" type="button" @click="selectChapter(nextChapter.chapterId)">
            <span class="dr__nav-body">
              <span class="dr__nav-label">下一章</span>
              <span class="dr__nav-title kh-line-clamp-1">{{ nextChapter.chapterName }}</span>
            </span>
            <el-icon class="dr__nav-arrow"><ArrowDown /></el-icon>
          </button>
        </div>
      </article>

      <!-- 右：本章目录卡（无小标题则不显示） -->
      <aside v-if="chapterToc.length" class="dr__subtoc" :style="{ '--kh-sticky-bottom': stickyBottom }">
        <KhContentToc :items="chapterToc" :active-index="activeTocIndex" title="目录" @select="handleTocSelect" />
      </aside>
    </div>
    <!-- 章节正文配图点击放大画廊（el-image-viewer，teleported 至 body 全屏，z-index 3000） -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIndex"
      :z-index="3000"
      hide-on-click-modal
      teleported
      @close="closeViewer"
    />
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
.dr__back:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.dr__crumb a { color: var(--kh-text-secondary); }
.dr__crumb a:hover { color: var(--kh-primary); }
.dr__crumb-sep { color: var(--kh-text-tertiary); }
.dr__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}

.dr__layout {
  display: grid;
  grid-template-columns: 240px 1fr 260px;
  gap: var(--kh-space-8);
  align-items: start;
  padding-bottom: var(--kh-space-12);
}

.dr__toc {
  position: sticky;
  /* 紧贴头导航底部（不留间隙，避免 sticky 上推时段被推到 header 内部被遮） */
  top: var(--kh-header-height);
  /* height 固定占满可用区（不随内容自适应缩），短内容时下半留白、长内容时 list 内滚，
     根除"刚点开比正常短"的视觉跳变 + footer 出现时底部平滑缩 */
  height: calc(100vh - var(--kh-header-height) - var(--kh-space-12) - var(--kh-sticky-bottom, 0px));
  display: flex;
  flex-direction: column;
  padding-top: var(--kh-space-3);
  /* 不加 transition：height 必须逐帧精确等于 (vh - header - space-12 - sticky_bottom)，
     否则过渡帧内 aside 底会超出 sticky 容器末端，触发 sticky bottom-pin（顶上移=抖动）。
     sticky_bottom 由 useStickyBottom 在每个 scroll 事件同步算出，本身已足够平滑。 */
}
.dr__toc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
  padding: 0 var(--kh-space-3) var(--kh-space-3);
  border-bottom: 1px solid var(--kh-border-soft);
  flex: none;
}
.dr__toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
}
.dr__toc-list::-webkit-scrollbar { width: 6px; }
.dr__toc-list::-webkit-scrollbar-thumb { background: var(--kh-border); border-radius: 3px; }
.dr__toc-item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.dr__toc-item:hover { background: var(--kh-surface-muted); }
.dr__toc-item.is-active { background: var(--kh-primary-soft); color: var(--kh-primary-strong); }
.dr__toc-no { font-family: var(--kh-font-mono); font-size: 11px; color: var(--kh-text-tertiary); flex: none; padding-top: 1px; }
.dr__toc-item.is-active .dr__toc-no { color: var(--kh-primary); }
.dr__toc-text { font-size: 13px; color: var(--kh-text-secondary); line-height: 1.4; }
.dr__toc-item.is-active .dr__toc-text { color: var(--kh-primary-strong); font-weight: 500; }
.dr__toc-empty { font-size: 12px; color: var(--kh-text-tertiary); padding: var(--kh-space-3); }

.dr__main { min-width: 0; padding: 0 var(--kh-space-2); }

/* 整页/切章加载占位：让 KhLoading 居中且撑满主体区，与正文区高度接近，避免数据浅入盖动画 */
.dr__page-loading {
  min-height: calc(80vh);
  padding-top: var(--kh-space-12);
}
.dr__loading {
  padding: var(--kh-space-12) 0;
}
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
.dr__content { color: var(--kh-text); }
.dr__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
/* 章节正文配图可点放大：cursor zoom-in 视觉提示，点击由 .dr__content @click 委托 onContentClick 开 el-image-viewer */
.dr__content :deep(.github-markdown-body img) {
  cursor: zoom-in;
}

/* 越级锁态：整片锁定卡片（不展示预览正文，直接锁整片区域）。
   实色卡片 + primary soft 浅底 + icon 圆圈 + 标题/提示居中，清晰体面不显残缺。 */
.dr__locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-12) var(--kh-space-6);
  margin: var(--kh-space-8) 0;
  background: var(--kh-primary-soft);
  border: 1px solid var(--kh-primary-border);
  border-radius: var(--kh-radius-lg);
  color: var(--kh-text-tertiary);
  text-align: center;
}
/* 锁 icon 圆圈底：放大 icon 并给实色圆底，U 形锁体完整可见 */
.dr__lock-iconwrap {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}
.dr__locked-title { font-size: var(--kh-font-size-lg); font-weight: 600; color: var(--kh-text-secondary); margin: 0; }
.dr__locked-hint { font-size: var(--kh-font-size-sm); margin: 0; }

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
.dr__placeholder p { margin: 0; font-size: var(--kh-font-size-sm); }

.dr__nav {
  display: flex;
  justify-content: space-between;
  gap: var(--kh-space-4);
  margin-top: var(--kh-space-10);
  padding-top: var(--kh-space-5);
  border-top: 1px solid var(--kh-border-soft);
}
.dr__nav.is-first,
.dr__nav.is-last { justify-content: stretch; }
/* 第一章/最后一章单按钮：宽度占满整行（=其他章节两按钮总长），但内部内容居中显示 */
.dr__nav.is-first .dr__nav-btn,
.dr__nav.is-last .dr__nav-btn { flex: 1; max-width: none; justify-content: center; text-align: center; }
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
.dr__nav-btn:hover { border-color: var(--kh-primary-border); background: var(--kh-primary-soft); }
.dr__nav-btn--next { text-align: right; justify-content: flex-end; }
.dr__nav-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.dr__nav-arrow { flex: none; color: var(--kh-text-tertiary); }
.dr__nav-btn:hover .dr__nav-arrow { color: var(--kh-primary); }
.dr__nav-label { font-size: 11px; color: var(--kh-text-tertiary); flex: none; }
.dr__nav-title { font-size: var(--kh-font-size-sm); font-weight: 600; color: var(--kh-text); }

.dr__subtoc {
  position: sticky;
  /* 紧贴 header 底部（与左卡同口径） */
  top: var(--kh-header-height);
  padding-top: var(--kh-space-3);
  /* 目录卡固定大小（≈视口 40%，用户指定长度），目录超长时**列表内部滚动**；
     外层 aside 不滚（防"卡片整体滑动"）。长度固定不随 footer 收缩（用户要求目录卡定长不动）。 */
  --kh-toc-max-height: 40vh;
}

@media (max-width: 1280px) {
  .dr__layout { grid-template-columns: 240px 1fr; }
  .dr__subtoc { display: none; }
}
@media (max-width: 1024px) {
  .dr__layout { grid-template-columns: 1fr; }
  .dr__toc { position: static; max-height: none; border-bottom: 1px solid var(--kh-border-soft); padding-bottom: var(--kh-space-4); }
}
</style>