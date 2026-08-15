<!--
  <!--
  文章介绍页 /article/:id（文档详情）
  ------------------------------------------------------------------
  标题信息卡（封面色条 + 标题 + 摘要 + 作者/难度/章节/阅读/时间 + 标签 + 右"开始阅读"）。
  下方两栏：左文章简介正文（v-md-preview 渲染 summary 前言）/ 右章节大纲卡（点章节进阅读页对应章）。
  越级锁态：locked=true 时仍给元数据+章节大纲（章节大纲只含章节名不泄正文），章节正文接口也会锁态拒发；
  顶部弹出 lockReason 提示，"开始阅读"按钮置灰禁用（无权看正文）。
  返回按钮：回上一级 /articles（文档学习页）。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElImageViewer } from 'element-plus'
import { ArrowLeft, Reading } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhLoading from '@/components/common/KhLoading.vue'
import KhCommentList from '@/components/common/KhCommentList.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { getArticleDetailApi, likeArticleApi, collectArticleApi } from '@/api/knowhub/article'
import type { ArticlePortalDetailRecord } from '@/types/api/knowhub/article'
import { viewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import toast from '@/utils/toast'
import { Collection } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const docId = computed(() => Number(route.params.id))
const doc = ref<ArticlePortalDetailRecord | null>(null)
/** 文章简介正文 DOM ref：v-md-preview 渲染简介 markdown，配图点击放大委托该容器的 <img> */
const contentRef = ref<HTMLElement | null>(null)
/** 文章简介配图点击放大（与博客/项目/资源同款 el-image-viewer 全屏画廊） */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
/** 首屏取数加载态：loading 期间显 KhLoading 占位，替代 v-if="doc" 的纯空白帧 */
const loading = ref(true)
/** 点赞/收藏交互态：interacting 期间禁用按钮防重复点击 */
const interacting = ref(false)

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getArticleDetailApi(docId.value)
    const d = res.data
    if (!d) {
      // 文档不存在或已下架：跳专门 404 页（404 页文案自带描述，不再弹红条避免重复提示）
      router.replace({ name: 'not-found' })
      return
    }
    if (d.publishTime) d.publishTime = formatDateTime(d.publishTime) as string
    doc.value = d
    if (d.locked) {
      ElMessage.warning(d.lockReason ?? '当前内容需更高权限查看')
    }
  } finally {
    loading.value = false
  }
}

/** 开始阅读：跳章节内容页，缺省第一章 */
const startReading = (chapterId?: number) => {
  if (doc.value?.locked) return
  const ch = chapterId ?? doc.value?.chapterList?.[0]?.chapterId
  if (!ch) return
  router.push(`/article/${docId.value}/read/${ch}`)
}

/** 返回上一级：文档学习页 */
const goUp = () => router.push('/articles')

const displayTags = computed<string[]>(() => doc.value?.tagNames ?? [])

/** 是否否认当前文章作者本人（详情页评论区 isAuthor flag：作者可 inline 精选 + 删任意评论，照 blog isMyBlog 范式） */
const isMyArticle = computed(
  () => Boolean(doc.value?.authorId) && doc.value!.authorId === userStore.userInfo?.userId,
)

/** 点赞/收藏：未登录跳登录 + redirect 回填来源；登录态乐观更新 hasLiked/hasCollected + 计数（对齐 resource 详情范式） */
const requireAuth = (): boolean => {
  if (!userStore.isAuthenticated) {
    toast('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}
const handleLike = async () => {
  if (!doc.value || !requireAuth()) return
  interacting.value = true
  try {
    const liked = !doc.value.hasLiked
    await likeArticleApi(docId.value, liked)
    doc.value.hasLiked = liked
    doc.value.likeCount = Math.max(0, (doc.value.likeCount ?? 0) + (liked ? 1 : -1))
  } finally {
    interacting.value = false
  }
}
const handleCollect = async () => {
  if (!doc.value || !requireAuth()) return
  interacting.value = true
  try {
    const collected = !doc.value.hasCollected
    await collectArticleApi(docId.value, collected)
    doc.value.hasCollected = collected
    doc.value.collectCount = Math.max(0, (doc.value.collectCount ?? 0) + (collected ? 1 : -1))
  } finally {
    interacting.value = false
  }
}

onMounted(() => void fetchDetail())
watch(docId, () => void fetchDetail())
</script>

<template>
  <div class="di">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide di__crumb">
      <button class="di__back" type="button" @click="goUp">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="di__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/articles">文档学习</RouterLink>
      <el-icon class="di__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="di__crumb-current">{{ doc?.title }}</span>
    </div>

    <!-- 首屏取数加载占位：loading 期间显 KhLoading，替代头卡"加载中…"文案 + 主体空白帧 -->
    <KhLoading v-if="loading" title="正在加载文档…" />

    <!-- 标题信息卡 -->
    <div v-else class="kh-container kh-container--wide">
      <KhCard v-if="doc" padding="none" class="di__head">
        <div class="di__head-cover" :style="{ background: doc.coverUrl ? `url(${doc.coverUrl}) center/cover` : 'linear-gradient(135deg,#2563eb,#0ea5e9)' }">
          <KhIcon name="doc" :size="32" class="di__head-cover-icon" />
        </div>
        <div class="di__head-body">
          <div class="di__head-main">
            <h1 class="di__title">{{ doc.title }}</h1>
            <p class="di__head-summary">{{ (doc.summary ?? '').replace(/[#>*|`]/g, '').replace(/\n+/g, ' ').trim().slice(0, 80) }}…</p>
            <div class="di__head-meta">
              <div class="di__head-author">
                <KhAvatar :item="{ label: doc.authorNickname ?? '' }" :size="28" />
                <span>{{ doc.authorNickname ?? '匿名' }}</span>
              </div>
              <KhTag v-if="doc.level" :type="viewLevelTagType[doc.level as 1 | 2 | 3] ?? 'neutral'" size="sm">{{ getViewLevelLabel(doc.level) }}</KhTag>
              <KhStatPill icon="file" :value="doc.chapterCount ?? 0" label="章" />
              <KhStatPill icon="eye" :value="doc.viewCount ?? 0" label="阅读" />
              <span v-if="doc.publishTime" class="di__head-time"><KhIcon name="clock" :size="12" /> {{ doc.publishTime }}</span>
            </div>
            <div class="di__head-tags">
              <KhTag v-for="t in displayTags" :key="t" size="sm" type="primary">{{ t }}</KhTag>
            </div>
          </div>

          <!-- 右：开始阅读（越级锁态置灰禁用） + 点赞/收藏 -->
          <div class="di__head-action">
            <button class="di__read-btn" type="button" :disabled="doc.locked" @click="startReading()">
              <el-icon><Reading /></el-icon> 开始阅读
            </button>
            <span class="di__read-hint">共 {{ doc.chapterCount ?? 0 }} 章</span>
            <div class="di__interact">
              <button class="di__action" :class="{ 'is-active': doc.hasLiked }" type="button" :disabled="interacting" @click="handleLike">
                <KhIcon name="heart" :size="14" /> {{ doc.likeCount ?? 0 }}
              </button>
              <button class="di__action" :class="{ 'is-active': doc.hasCollected }" type="button" :disabled="interacting" @click="handleCollect">
                <el-icon><Collection /></el-icon> 收藏
              </button>
            </div>
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体两栏：左简介正文 + 右章节大纲 -->
    <div v-if="doc" class="kh-container kh-container--wide di__layout">
      <article class="di__main">
        <KhCard padding="lg" class="di__intro-card">
          <KhSectionTitle title="文章简介" />
          <!-- summary 是 TEXT，用 v-md-preview 渲染承载长文；短 summary 时也按 markdown body 排版，不空 -->
          <div ref="contentRef" class="di__intro" @click="onContentClick">
            <v-md-preview :text="doc.summary ?? ''" />
          </div>
        </KhCard>
      </article>

      <aside class="di__aside">
        <!-- 章节大纲卡 -->
        <KhCard padding="md" class="di__outline-card">
          <div class="di__outline-head">
            <KhIcon name="doc" :size="16" />
            <h3>章节大纲</h3>
          </div>
          <p class="di__outline-sub">共 {{ doc.chapterList?.length ?? 0 }} 章 · 点章节进入阅读</p>
          <ol v-if="doc.chapterList?.length" class="di__outline-list">
            <li
              v-for="(c, i) in (doc.chapterList ?? [])"
              :key="c.chapterId"
              class="di__outline-item"
              @click="startReading(c.chapterId)"
            >
              <span class="di__outline-no">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="di__outline-title kh-line-clamp-2">{{ c.chapterName }}</span>
              <el-icon class="di__outline-arrow"><ArrowLeft /></el-icon>
            </li>
          </ol>
          <div v-else class="di__outline-empty">
            {{ doc.locked ? (doc.lockReason ?? '需更高权限查看完整内容') : '暂无章节' }}
          </div>

          <button class="di__read-btn di__read-btn--block" type="button" :disabled="doc.locked" @click="startReading()">
            <el-icon><Reading /></el-icon> 开始阅读
          </button>
        </KhCard>
      </aside>
    </div>

    <!-- 评论区：全宽独立区块，KhCommentList 内置发表条/列表/回复/作者 inline 精选 -->
    <div v-if="doc" class="kh-container kh-container--wide di__comments">
      <KhCard padding="lg">
        <KhCommentList
          biz-type="ARTICLE"
          :biz-id="docId"
          :comment-enabled="doc.commentEnabled"
          :comment-curated="doc.commentCurated"
          :is-author="isMyArticle"
        />
      </KhCard>
    </div>
    <!-- 文章简介配图点击放大画廊（el-image-viewer，teleported 至 body 全屏，z-index 3000） -->
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
.di__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.di__back {
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
.di__back:hover { border-color: var(--kh-primary-border); color: var(--kh-primary); }
.di__crumb a { color: var(--kh-text-secondary); }
.di__crumb a:hover { color: var(--kh-primary); }
.di__crumb-sep { color: var(--kh-text-tertiary); }
.di__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.di__head { overflow: hidden; }
.di__head-cover {
  height: 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: var(--kh-space-6);
}
.di__head-cover-icon { color: rgba(255, 255, 255, 0.9); position: relative; top: 14px; }
.di__head-body {
  display: flex;
  align-items: center;
  gap: var(--kh-space-6);
  padding: var(--kh-space-5) var(--kh-space-6);
}
.di__head-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--kh-space-3); }
.di__title { font-size: var(--kh-font-size-3xl); font-weight: 700; letter-spacing: -0.01em; }
.di__head-summary { color: var(--kh-text-secondary); font-size: var(--kh-font-size-md); line-height: 1.7; }
.di__head-meta { display: flex; align-items: center; gap: var(--kh-space-4); flex-wrap: wrap; font-size: 12px; color: var(--kh-text-tertiary); }
.di__head-author { display: flex; align-items: center; gap: 6px; color: var(--kh-text-secondary); font-weight: 500; }
.di__head-time { display: inline-flex; align-items: center; gap: 4px; }
.di__head-tags { display: flex; flex-wrap: wrap; gap: 6px; }

.di__head-action { display: flex; flex-direction: column; align-items: center; gap: var(--kh-space-2); flex: none; }
.di__read-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  cursor: pointer;
  box-shadow: var(--kh-shadow-primary);
  transition: transform var(--kh-transition-fast);
  white-space: nowrap;
}
.di__read-btn:hover:not(:disabled) { transform: translateY(-1px); }
.di__read-btn:disabled { opacity: 0.5; cursor: not-allowed; box-shadow: none; }
.di__read-btn--block { width: 100%; margin-top: var(--kh-space-4); }
.di__read-hint { font-size: 11px; color: var(--kh-text-tertiary); }
/* 点赞/收藏按钮（与博客详情 .bd__action 同款 pill，复用主色 is-active 高亮） */
.di__interact { display: flex; gap: 8px; margin-top: var(--kh-space-1); }
.di__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.di__action:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.di__action.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}
.di__action:disabled { opacity: 0.6; cursor: not-allowed; }

.di__layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
}
.di__main { min-width: 0; }
.di__intro-card { display: flex; flex-direction: column; gap: var(--kh-space-4); }
.di__intro :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.95;
  color: var(--kh-text);
}
.di__intro :deep(.github-markdown-body h1),
.di__intro :deep(.github-markdown-body h2) {
  border-bottom: none;
  margin-top: var(--kh-space-6);
}
/* 文章简介配图可点放大：cursor zoom-in 视觉提示，点击由 .di__intro @click 委托 onContentClick 开 el-image-viewer */
.di__intro :deep(.github-markdown-body img) {
  cursor: zoom-in;
}

.di__aside { display: flex; flex-direction: column; gap: var(--kh-space-5); position: sticky; top: calc(var(--kh-header-height) + var(--kh-space-4)); }
.di__outline-card { display: flex; flex-direction: column; }
.di__outline-head { display: flex; align-items: center; gap: 8px; color: var(--kh-text-secondary); margin-bottom: 2px; }
.di__outline-head h3 { font-size: var(--kh-font-size-md); font-weight: 600; color: var(--kh-text); }
.di__outline-sub { font-size: 11px; color: var(--kh-text-tertiary); margin: 0 0 var(--kh-space-3); }
/* 章节大纲列表最大高度封顶 + 卡内滚动，章节数太多时不撑爆侧栏 */
.di__outline-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--kh-space-2); max-height: 60vh; overflow-y: auto; scrollbar-width: thin; }
.di__outline-list::-webkit-scrollbar { width: 6px; }
.di__outline-list::-webkit-scrollbar-thumb { background: var(--kh-border); border-radius: 3px; }
.di__outline-item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-3);
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.di__outline-item:hover { background: var(--kh-primary-soft); }
.di__outline-no { font-family: var(--kh-font-mono); font-size: 12px; font-weight: 700; color: var(--kh-primary); flex: none; width: 24px; }
.di__outline-title { flex: 1; min-width: 0; font-size: var(--kh-font-size-sm); font-weight: 500; color: var(--kh-text); line-height: 1.4; }
.di__outline-arrow { transform: rotate(180deg); color: var(--kh-text-tertiary); flex: none; }
.di__outline-item:hover .di__outline-arrow { color: var(--kh-primary); }
.di__outline-empty {
  font-size: 12px;
  color: var(--kh-text-tertiary);
  text-align: center;
  padding: var(--kh-space-4);
}

/* 评论区：全宽独立区块（在两栏 layout 之后），与 blog/resource 详情同款口径 */
.di__comments {
  padding-bottom: var(--kh-space-12);
}

@media (max-width: 1024px) {
  .di__layout { grid-template-columns: 1fr; }
  .di__aside { position: static; }
}
@media (max-width: 768px) {
  .di__head-body { flex-direction: column; align-items: stretch; gap: var(--kh-space-4); }
  .di__head-action { flex-direction: row; justify-content: space-between; }
}
</style>