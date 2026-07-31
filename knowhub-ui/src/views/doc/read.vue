<!--
  文章章节内容页 /docs/:id/read/:chapterId
  ------------------------------------------------------------------
  类 Vue 官方文档站：左章节目录（当前章高亮、可点切换）/ 中正文（v-md-preview 渲染章节 markdown）/
  右本章内容大纲卡（从正文 ## 标题提取）。越级锁态：locked=true 时正文不下发，显示锁态提示。
  章节目录来自文章详情接口的 chapterList（点章节切 URL + 拉对应章正文）；
  章节正文走 /portal/article/{id}/chapter/{chapterId} 单独拉取（达权才下发，越级 content 为 null）。
  底部上/下章导航。返回按钮回上一级（文章介绍页 /docs/:id）。
-->
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhContentToc from '@/components/common/KhContentToc.vue'
import { getArticleDetailApi, getChapterContentApi } from '@/api/knowhub/article'
import type { ArticlePortalDetailRecord, ChapterContentRecord } from '@/types/api/knowhub/article'

const route = useRoute()
const router = useRouter()

const docId = computed(() => Number(route.params.id))
const activeChapterId = computed(() => Number(route.params.chapterId))

/** 文章详情（含章节大纲 chapterList + 标题） */
const doc = ref<ArticlePortalDetailRecord | null>(null)
/** 当前章节正文 */
const chapter = ref<ChapterContentRecord | null>(null)
const loading = ref(false)

/** 拉文章详情（章节目录来源，越级锁态时 chapterList 仍下发，目录照常可用） */
const fetchDoc = async () => {
  const res = await getArticleDetailApi(docId.value)
  doc.value = res.data ?? null
}

/** 拉当前章节正文 */
const fetchChapter = async () => {
  loading.value = true
  try {
    const res = await getChapterContentApi(docId.value, activeChapterId.value)
    chapter.value = res.data ?? null
  } finally {
    loading.value = false
  }
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
  router.push(`/docs/${docId.value}/read/${chapterId}`)
}

const goIntro = () => router.push(`/docs/${docId.value}`)

/** 当前章内容大纲：从正文提取 ## 二级标题，供右栏大纲卡展示（无标题则空；越级锁态时正文为空 → 目录空） */
const chapterToc = computed<string[]>(() => {
  const content = chapter.value?.content ?? ''
  return content
    .split('\n')
    .filter((l) => l.startsWith('## '))
    .map((l) => l.replace(/^##\s+/, '').trim())
    .slice(0, 10)
})

const contentRef = ref<HTMLElement | null>(null)

/** 点击目录项 i：取正文容器内第 i 个 h2 平滑滚动定位 */
const handleTocSelect = (idx: number) => {
  const root = contentRef.value
  if (!root) return
  nextTick(() => {
    const hs = root.querySelectorAll<HTMLElement>(':scope .github-markdown-body h2')
    hs[idx]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

/** 切文章时重拉详情 + 正文；切章节时只重拉正文 */
watch(docId, () => {
  void fetchDoc()
  void fetchChapter()
})
watch(activeChapterId, () => {
  void fetchChapter()
})

// 首次：先拉详情（得章节目录），再拉当前章正文
fetchDoc().then(() => fetchChapter())
</script>

<template>
  <div class="dr">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide dr__crumb">
      <button class="dr__back" type="button" @click="goIntro">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/docs">文档学习</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink :to="`/docs/${docId}`">{{ doc?.title }}</RouterLink>
      <el-icon class="dr__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="dr__crumb-current">{{ chapter?.chapterName }}</span>
    </div>

    <!-- 主体三栏 -->
    <div class="kh-container kh-container--wide dr__layout">
      <!-- 左：章节目录 sticky -->
      <aside class="dr__toc">
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

        <!-- 越级锁态：正文不下发，显示锁态提示 -->
        <div v-if="chapter?.locked" class="dr__locked">
          <KhIcon name="lock" :size="40" :stroke="1.4" />
          <p class="dr__locked-title">{{ chapter.lockReason ?? '需更高权限查看完整内容' }}</p>
          <p class="dr__locked-hint">登录并拥有对应等级权限后可查看本章正文</p>
        </div>
        <!-- 正文：v-md-preview 渲染章节 markdown -->
        <div v-else-if="chapter?.content" ref="contentRef" class="dr__content">
          <v-md-preview :text="chapter.content" />
        </div>
        <!-- 章节无正文（内容待补） -->
        <div v-else-if="!loading" class="dr__placeholder">
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
  grid-template-columns: 240px 1fr 220px;
  gap: var(--kh-space-8);
  align-items: start;
  padding-bottom: var(--kh-space-12);
}

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
.dr__toc-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
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
.dr__content :deep(.github-markdown-body h1),
.dr__content :deep(.github-markdown-body h2) { border-bottom: none; }
.dr__content :deep(.github-markdown-body h2),
.dr__content :deep(.github-markdown-body h3) {
  scroll-margin-top: calc(var(--kh-header-height) + var(--kh-space-4));
}

/* 越级锁态：正文不下发，显示锁态提示 */
.dr__locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-12) var(--kh-space-6);
  margin: var(--kh-space-8) 0;
  border: 1px dashed var(--kh-border);
  border-radius: var(--kh-radius-lg);
  color: var(--kh-text-tertiary);
  text-align: center;
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
.dr__nav.is-last { justify-content: center; }
.dr__nav.is-first .dr__nav-btn,
.dr__nav.is-last .dr__nav-btn { flex: 0 1 auto; max-width: 60%; }
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
  top: calc(var(--kh-header-height) + var(--kh-space-4));
  max-height: calc(100vh - var(--kh-header-height) - var(--kh-space-8));
  overflow-y: auto;
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