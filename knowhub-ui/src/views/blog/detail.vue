<!--
  博客详情 /blog/:id
  ------------------------------------------------------------------
  标题 + 作者/时间/标签/观看数/评分 + 点赞/收藏/分享 + 左侧目录锚点 + 正文（静态预渲染）+ 相关推荐 + 评论区占位。
-->
<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElImageViewer } from 'element-plus'
import {
  ArrowLeft,
  Share,
  Collection,
  EditPen,
} from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhSectionTitle from '@/components/common/KhSectionTitle.vue'
import KhContentToc from '@/components/common/KhContentToc.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhLoading from '@/components/common/KhLoading.vue'
import KhCommentList from '@/components/common/KhCommentList.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { getBlogDetailApi, relatedBlogsApi } from '@/api/knowhub/blog'
import { likeBlogApi, collectBlogApi } from '@/api/knowhub/authoring'
import type { BlogPortalDetailRecord, BlogPortalRecord } from '@/types/api/knowhub/blog'
import { useStickyBottom } from '@/utils/use-footer-visible'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** sticky 侧栏底部预留量：sticky 父容器（.bd__layout）末端进视口时收紧 max-height，
 *  防侧栏上推钻 header；footer 进视口也兜底收缩。 */
const footerVisible = useStickyBottom('.bd__layout')
const stickyBottom = computed(() => `${footerVisible.value}px`)

const blogId = computed(() => Number(route.params.id))
const blog = ref<BlogPortalDetailRecord | null>(null)
const related = ref<BlogPortalRecord[]>([])
/** 首屏取数加载态：loading 期间显 KhLoading 占位，替代 v-if="blog" 的纯空白帧 */
const loading = ref(true)

/** 是否认当前作者本人（详情页编辑按钮显示条件：前台公开 VO 无 isAuthor，靠 userId===authorId 比对） */
const isMyBlog = computed(
  () => Boolean(blog.value?.authorId) && blog.value!.authorId === userStore.userInfo?.userId,
)

/** 跳创作页编辑模式 */
const goEdit = () => {
  router.push(`/blog/create?id=${blogId.value}`)
}

/** 拉取详情 + 相关推荐 */
const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getBlogDetailApi(blogId.value)
    const b = res.data
    if (!b) {
      // 博客不存在或已下架：跳专门 404 页（404 页文案自带描述，不再弹红条避免重复提示）
      router.replace({ name: 'not-found' })
      return
    }
    if (b.publishTime) b.publishTime = formatDateTime(b.publishTime) as string
    blog.value = b
    // 锁态提示：越级访问只给元数据，正文不下发
    if (b.locked) {
      ElMessage.warning(b.lockReason ?? '当前内容需更高权限查看完整正文')
    }
    // 正文 v-md-preview 异步渲染，等下一帧再计算 scroll spy 初值
    nextTick(computeActive)
  } finally {
    loading.value = false
  }
}

const fetchRelated = async () => {
  const res = await relatedBlogsApi(blogId.value, 3)
  related.value = (res.data ?? []).map((r) => ({
    ...r,
    publishTime: r.publishTime ? (formatDateTime(r.publishTime) as string) : r.publishTime,
  }))
}

/** 从正文提取 ##/###/#### 标题作为目录锚点（含层级，供目录树渲染；锁态时 content 为 null，目录为空） */
const toc = computed(() => {
  const lines = (blog.value?.content ?? '').split('\n')
  const out: { level: number; text: string }[] = []
  for (const line of lines) {
    const m = /^(#{2,4})\s+(.+)$/.exec(line)
    if (m && m[1] && m[2]) {
      out.push({ level: m[1].length, text: m[2].trim() })
    }
  }
  return out
})

/** 正文容器 DOM 引用：v-md-preview 在其内渲染，目录跳转靠 querySel 取第 idx 个 h2/h3/h4 */
const contentRef = ref<HTMLElement | null>(null)

/** 正文配图点击放大（与评论区同款 el-image-viewer 全屏画廊）：复用上面的 contentRef——
 *  onContentClick 只 querySelectorAll('img')，与 handleTocSelect/computeActive 查 h2/h3/h4 正交不冲突。 */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)

/**
 * 点击目录项 i：在正文容器内取第 i 个 h2/h3/h4（v-md-preview github 主题不给 heading 加 id，
 * 故靠 .github-markdown-body 下 querySelectorAll('h2,h3,h4') 按 DOM 顺序取第 i 个）。
 * toc 提取与渲染顺序一一对应，索引一致。nextTick 等首屏渲染完成（v-md-preview 异步解析）。
 */
const handleTocSelect = (idx: number) => {
  const root = contentRef.value
  if (!root) return
  nextTick(() => {
    const hs = root.querySelectorAll<HTMLElement>(':scope .github-markdown-body h2, :scope .github-markdown-body h3, :scope .github-markdown-body h4')
    hs[idx]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

/**
 * Scroll spy：监听 window scroll，取离视口顶部最近且已越过偏移线（header 高度+缓冲）的标题序号，
 * 驱动 KhContentToc 高亮 + 自动展开祖先链。rAF 节流。
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

/** 展示用标签名：tagNames 优先（后端当前置空），无则留空数组 */
const displayTags = computed<string[]>(() => blog.value?.tagNames ?? [])

const goBack = () => router.back()

/** 点赞/收藏交互态：interacting 期间禁用按钮防重复点击。状态来自详情 VO hasLiked/hasCollected（登录态回填）。 */
const interacting = ref(false)
const isLoggedIn = computed(() => userStore.isAuthenticated)
const requireAuth = (): boolean => {
  if (!isLoggedIn.value) {
    toast('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}
const handleLike = async () => {
  if (!blog.value || !requireAuth()) return
  interacting.value = true
  try {
    const liked = !blog.value.hasLiked
    await likeBlogApi(blogId.value, liked)
    blog.value.hasLiked = liked
    blog.value.likeCount = Math.max(0, (blog.value.likeCount ?? 0) + (liked ? 1 : -1))
  } finally {
    interacting.value = false
  }
}
const handleCollect = async () => {
  if (!blog.value || !requireAuth()) return
  interacting.value = true
  try {
    const collected = !blog.value.hasCollected
    await collectBlogApi(blogId.value, collected)
    blog.value.hasCollected = collected
    blog.value.collectCount = Math.max(0, (blog.value.collectCount ?? 0) + (collected ? 1 : -1))
  } finally {
    interacting.value = false
  }
}

onMounted(() => {
  void fetchDetail()
  void fetchRelated()
})
watch(blogId, () => {
  void fetchDetail()
  void fetchRelated()
})
</script>

<template>
  <div class="bd">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide bd__crumb">
      <button class="bd__back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="bd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/blogs">笔记导航</RouterLink>
      <el-icon class="bd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="bd__crumb-current">{{ blog?.title }}</span>
    </div>

    <KhLoading v-if="loading" title="正在加载博客…" />
    <div v-else-if="blog" class="kh-container kh-container--wide bd__layout">
      <!-- 主体 -->
      <article class="bd__main">
        <!-- 头部信息 -->
        <header class="bd__head">
          <h1 class="bd__title">{{ blog.title }}</h1>
          <div class="bd__tags">
            <KhTag v-for="t in displayTags" :key="t" type="primary">{{ t }}</KhTag>
          </div>
          <div class="bd__meta">
            <div class="bd__author">
              <KhAvatar :item="{ label: blog.authorNickname ?? '' }" :size="36" />
              <div>
                <div class="bd__author-name">{{ blog.authorNickname }}</div>
                <div class="bd__author-time">发布于 {{ blog.publishTime }}</div>
              </div>
            </div>
            <div class="bd__meta-stats">
              <button v-if="isMyBlog" class="bd__edit-btn" type="button" @click="goEdit">
                <el-icon><EditPen /></el-icon> 编辑
              </button>
              <KhStatPill icon="eye" :value="blog.viewCount ?? 0" label="阅读" />
              <KhStatPill icon="heart" :value="blog.likeCount ?? 0" label="赞" />
              <KhStatPill icon="bookmark" :value="blog.collectCount ?? 0" label="收藏" />
            </div>
          </div>
        </header>

        <!-- 正文：越级锁态时 content 为 null，显示锁态提示而非正文 -->
        <div v-if="blog.locked" class="bd__locked">
          <KhIcon name="lock" :size="40" :stroke="1.4" />
          <p class="bd__locked-title">{{ blog.lockReason ?? '需更高权限查看完整正文' }}</p>
          <p class="bd__locked-hint">登录并拥有对应等级权限后可查看完整内容</p>
        </div>
        <div v-else ref="contentRef" class="bd__content" @click="onContentClick">
          <v-md-preview :text="blog.content ?? ''" />
        </div>

        <!-- 底部操作 -->
        <div class="bd__actions">
          <button class="bd__action" :class="{ 'is-active': blog.hasLiked }" type="button" :disabled="interacting" @click="handleLike">
            <KhIcon name="heart" :size="14" /> {{ blog.likeCount ?? 0 }}
          </button>
          <button class="bd__action" :class="{ 'is-active': blog.hasCollected }" type="button" :disabled="interacting" @click="handleCollect">
            <el-icon><Collection /></el-icon> 收藏
          </button>
          <button class="bd__action" type="button">
            <el-icon><Share /></el-icon> 分享
          </button>
        </div>

        <!-- 评论区：KhCommentList 内置发表条/列表/回复/作者 inline 精选，未登录只读已「同意展示」评论 -->
        <KhCard padding="lg" class="bd__comments">
          <KhCommentList
            biz-type="BLOG"
            :biz-id="blogId"
            :comment-enabled="blog.commentEnabled"
            :comment-curated="blog.commentCurated"
            :is-author="isMyBlog"
          />
        </KhCard>
      </article>

      <!-- 侧栏：目录（复用 KhContentToc，与文档阅读页同款）+ 相关推荐（作者卡已移除：目前无作者主页等可跳链的承载页） -->
      <aside class="bd__aside" :style="{ '--kh-sticky-bottom': stickyBottom }">
        <KhContentToc :items="toc" :active-index="activeTocIndex" @select="handleTocSelect" />

        <KhCard padding="md" class="bd__related">
          <KhSectionTitle title="相关推荐" />
          <ul class="bd__related-list">
            <li v-for="r in related" :key="r.blogId" class="bd__related-item" @click="router.push(`/blog/${r.blogId}`)">
              <div class="bd__related-cover" :style="{ background: r.coverUrl ?? 'linear-gradient(135deg,#2563eb,#0ea5e9)' }">
                <KhIcon name="blog" :size="16" />
              </div>
              <div class="bd__related-text">
                <div class="bd__related-title kh-line-clamp-2">{{ r.title }}</div>
                <div class="bd__related-meta">{{ r.viewCount ?? 0 }} 阅读</div>
              </div>
            </li>
            <li v-if="!related.length" class="bd__related-empty">暂无相关推荐</li>
          </ul>
        </KhCard>
      </aside>
    </div>
    <!-- 正文配图点击放大画廊（与评论区同款 el-image-viewer，teleported 至 body 全屏） -->
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
.bd__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.bd__back {
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
.bd__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.bd__crumb a {
  color: var(--kh-text-secondary);
}
.bd__crumb a:hover {
  color: var(--kh-primary);
}
.bd__crumb-sep {
  color: var(--kh-text-tertiary);
}
.bd__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.bd__layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--kh-space-8);
  align-items: start;
  padding-bottom: var(--kh-space-12);
}
.bd__main {
  min-width: 0;
}
.bd__head {
  padding-bottom: var(--kh-space-6);
  border-bottom: 1px solid var(--kh-border-soft);
}
.bd__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--kh-space-3);
}
.bd__title {
  font-size: var(--kh-font-size-4xl);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
}
.bd__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-4);
  margin-top: var(--kh-space-5);
  flex-wrap: wrap;
}
.bd__author {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bd__author-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.bd__author-time {
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.bd__meta-stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-4);
}
.bd__edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 var(--kh-space-4);
  border: 1px solid var(--kh-primary-border);
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.bd__edit-btn:hover {
  background: var(--kh-primary);
  color: #fff;
}

/* 正文 —— v-md-preview 渲染区
   v-md-preview 的 prop 名是 text（不是 modelValue），见上方模板注释；
   github 主题给 preview 根类设的类名是 github-markdown-body（不是
   vuepress-markdown-body，后台 rookie-ui MarkdownPreview.vue 注释写错类名，
   覆盖样式务必命中 github-markdown-body 才生效）。 */
.bd__content {
  padding: var(--kh-space-8) 0;
}
.bd__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0; /* 清掉主题给 body 的左右内边距，正文与上方标题左对齐 */
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
.bd__content :deep(.github-markdown-body h1),
.bd__content :deep(.github-markdown-body h2) {
  border-bottom: none; /* 去掉 github 主题给一二级标题自带的下横线 */
}
/* 目录 scrollIntoView 落点留出头导航高度，否则 sticky 顶栏会遮住滚到顶的标题 */
.bd__content :deep(.github-markdown-body h2),
.bd__content :deep(.github-markdown-body h3) {
  scroll-margin-top: calc(var(--kh-header-height) + var(--kh-space-4));
}
/* 正文配图可点放大：cursor zoom-in 作视觉提示，点击由 .bd__content @click 委托 onContentClick 开 el-image-viewer */
.bd__content :deep(.github-markdown-body img) {
  cursor: zoom-in;
}

/* 锁态：越级访问只给元数据，正文不下发，展示锁态提示 */
.bd__locked {
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
.bd__locked-title {
  font-size: var(--kh-font-size-lg);
  font-weight: 600;
  color: var(--kh-text-secondary);
}
.bd__locked-hint {
  font-size: var(--kh-font-size-sm);
}

.bd__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--kh-space-3);
  padding: var(--kh-space-5) 0;
  border-top: 1px solid var(--kh-border-soft);
  border-bottom: 1px solid var(--kh-border-soft);
}
.bd__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
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
.bd__action:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.bd__action.is-active {
  background: var(--kh-primary);
  border-color: var(--kh-primary);
  color: #fff;
}

.bd__comments {
  margin-top: var(--kh-space-6);
}

/* 侧栏 */
.bd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  /* 紧贴 header 底部（不留间隙，避免上推时段钻入 header 被遮） */
  top: var(--kh-header-height);
  padding-top: var(--kh-space-3);
  /* 外层 aside 不滚（防"卡片整体滑动"）：目录卡列表内部滚（KhContentToc .kh-toc__root 限高），
     相关推荐卡在下方正常排。目录卡长度固定 40vh 不随 footer 收缩（用户要求目录卡定长不动）。 */
  --kh-toc-max-height: 40vh;
}
.bd__related-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.bd__related-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 6px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.bd__related-item:hover {
  background: var(--kh-surface-muted);
}
.bd__related-cover {
  width: 44px;
  height: 44px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.92);
  flex: none;
}
.bd__related-text {
  min-width: 0;
  flex: 1;
}
.bd__related-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
  line-height: 1.4;
}
.bd__related-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 3px;
  font-family: var(--kh-font-mono);
}
.bd__related-empty {
  text-align: center;
  padding: var(--kh-space-5);
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}

@media (max-width: 1024px) {
  .bd__layout {
    grid-template-columns: 1fr;
  }
  .bd__aside {
    position: static;
  }
}
</style>
