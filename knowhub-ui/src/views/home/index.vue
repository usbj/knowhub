<!--
  首页 / —— knowhub 前台定调页
  ------------------------------------------------------------------
  内容优先的博客论坛首页（非产品介绍页）：
    1. Hero（润色标语 + 加高搜索框 + 四类内容徽章 + 柔和渐变 blob 浮动 + 内容依次淡入）
    2. 公告轮播条
    3. 主体两栏：
       左主列内容 feed —— 最新笔记横条 / 活跃项目网格 / 资源推荐网格
       右侧栏 —— AI 日报 / 热门资源榜 / 热门标签
  无横向滚动区域。无活跃成员墙、无大标签云、无统计卡（按反馈移除）。
  动画均带 prefers-reduced-motion 降级。
-->
<script setup lang="ts">
import { Search, ArrowRight, Promotion } from '@element-plus/icons-vue'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import BlogRow from '@/components/blog/BlogRow.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import ResourceCard from '@/components/resource/ResourceCard.vue'
import ArticleCard from '@/components/article/ArticleCard.vue'

import { useRouter } from 'vue-router'
import { onMounted, ref } from 'vue'
import {
  recommendResourcesApi,
  searchResourcesApi,
} from '@/api/knowhub/resource-portal'
import { recommendBlogsApi, hotTagsApi } from '@/api/knowhub/blog'
import { recommendProjectsApi } from '@/api/knowhub/project-portal'
import { recommendArticlesApi } from '@/api/knowhub/article'
import { getPublicNoticesApi } from '@/api/system/notice-portal'
import type { NoticePortalRecord } from '@/types/api/notice-portal'
import type { BlogPortalRecord } from '@/types/api/knowhub/blog'
import type { ProjectPortalRecord } from '@/types/api/knowhub/project-portal'
import type { ArticlePortalRecord } from '@/types/api/knowhub/article'
import type { HotTagRecord } from '@/types/api/knowhub/tag'
import type { ResourcePortalRecord } from '@/types/api/knowhub/resource'
import { useNoticeStore } from '@/stores/notice'

const router = useRouter()
const noticeStore = useNoticeStore()

/** 最新笔记：博客推荐 feed recommendBlogsApi（登录用户按偏好，未登录走全局热门兜底） */
const latestBlogs = ref<BlogPortalRecord[]>([])
/** 活跃项目：项目推荐 feed recommendProjectsApi（全局热门兜底，无用户偏好源） */
const hotProjects = ref<ProjectPortalRecord[]>([])
/** 最新文档：文章推荐 feed recommendArticlesApi（全局热门兜底，6 条 2×3 网格） */
const latestArticles = ref<ArticlePortalRecord[]>([])

/** 公告滚动条：公开公告接口（群发+已发布，置顶优先+时间倒序），未登录访客也可读 */
const pinnedNotices = ref<NoticePortalRecord[]>([])

/** 资源推荐网格：真实接口 recommendResourcesApi（全局热门兜底，无用户偏好源） */
const featuredResources = ref<ResourcePortalRecord[]>([])

/** 热门资源榜：真实接口 search 带 sort=HOT（与侧栏榜同口径，按下载量/热度排序） */
const hotResourceRank = ref<ResourcePortalRecord[]>([])

/** 通知类型字典 code → 中文标签（与 AppHeader noticeTypeMap 同款内联映射，首页公开页未登录字典未拉，不引字典预加载） */
const noticeTypeMap: Record<string, string> = { NOTICE: '公告', NOTIFY: '通知', REMIND: '提醒' }
const resolveNoticeType = (code: string) => noticeTypeMap[code] ?? code

const fetchHomeNotices = async () => {
  try {
    const page = await getPublicNoticesApi({ pageNum: 1, pageSize: 8 }, { silentError: true })
    pinnedNotices.value = page.records ?? []
  } catch {
    pinnedNotices.value = []
  }
}

const fetchHomeBlogsAndProjects = async () => {
  try {
    const [blogs, projects, articles] = await Promise.all([
      recommendBlogsApi(5, undefined, { silentError: true }),
      recommendProjectsApi(4, undefined, { silentError: true }),
      recommendArticlesApi(6),
    ])
    latestBlogs.value = blogs.data ?? []
    hotProjects.value = projects.data ?? []
    latestArticles.value = articles.data ?? []
  } catch {
    latestBlogs.value = []
    hotProjects.value = []
    latestArticles.value = []
  }
}

/** 热门标签：真实接口 hotTagsApi（跨 blog_tag+article_tag 热度聚合） */
const hotTags = ref<HotTagRecord[]>([])
const fetchHomeHotTags = async () => {
  try {
    const res = await hotTagsApi(8, { silentError: true })
    hotTags.value = res.data ?? []
  } catch {
    hotTags.value = []
  }
}

const fetchHomeResources = async () => {
  try {
    const [feat, hot] = await Promise.all([
      recommendResourcesApi(8, undefined, { silentError: true }),
      searchResourcesApi({ sort: 'HOT', pageSize: 6 }, { silentError: true }),
    ])
    featuredResources.value = feat.data ?? []
    hotResourceRank.value = hot.records ?? []
  } catch {
    featuredResources.value = []
    hotResourceRank.value = []
  }
}

/** 资源榜无封面时的占位图标按类型派生（有封面 linkIcon 时由卡片渲图，榜单此处仍用占位 icon） */
const rankCoverGradient = (r: ResourcePortalRecord) =>
  r.resourceType === 'LINK'
    ? 'linear-gradient(135deg,#2563eb,#0ea5e9)'
    : 'linear-gradient(135deg,#6366f1,#a5b4fc)'
const rankIcon = (r: ResourcePortalRecord) => (r.resourceType === 'LINK' ? 'link' : 'file')

onMounted(() => {
  void fetchHomeNotices()
  void fetchHomeBlogsAndProjects()
  void fetchHomeHotTags()
  void fetchHomeResources()
})

/** 热门标签榜首热度分，做热度条占比分母（hotScore 后端加权聚合分） */
const topTagScore = () => hotTags.value[0]?.hotScore ?? 1

/**
 * 标签名是否「过长」需循环滚动播放。
 * home__tag-name 容器固定宽 72px（13px 字号约容 5 个中文字 / 11 个英文字符），
 * 超过该宽度的标签名会撑成两行破坏榜单布局。此处按字符数粗判：
 * 中文等宽字符计 1、半角字符计 0.5，加权和 > 5 即视为过长，开横向 marquee 滚动
 * （仅滚名称，名后的热度条与名前的序号不动）。
 */
const isTagNameLong = (name: string): boolean => {
  if (!name) return false
  let weight = 0
  for (const ch of name) {
    // CJK 区与全角符号等按 1 计，半角 ASCII 按约 0.5 计
    weight += /[　-鿿＀-￯]/.test(ch) ? 1 : 0.5
  }
  return weight > 5
}

/** AI 日报：功能未实施，静态"即将上线"占位（不接接口） */
const aiDaily = {
  title: '知枢 AI 日报 · 即将上线',
  summary: 'AI 日报功能即将上线，每日自动生成当日知识库报道，敬请期待。',
  topics: ['即将上线'],
  comingSoon: true,
} as const

/** 字节大小 → B/KB/MB/GB，与资源卡 / 项目详情 formatSize 口径一致 */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 分区定义（带序号与图标点缀，增强层次） */
const sections = [
  { key: 'notes', no: '01', icon: 'blog', title: '最新笔记', tone: 'var(--kh-primary)' },
  { key: 'projects', no: '02', icon: 'project', title: '活跃项目', tone: 'var(--kh-accent)' },
  { key: 'articles', no: '03', icon: 'doc', title: '最新文档', tone: 'var(--kh-success)' },
  { key: 'resources', no: '04', icon: 'resource', title: '资源推荐', tone: 'var(--kh-warm)' },
] as const

/** Hero 内容类别徽章：标明系统里有什么资源 */
const contentKinds = [
  { key: 'blog', label: '博客笔记', desc: '沉淀与分享', icon: 'blog', tone: 'var(--kh-primary)', to: '/blogs' },
  { key: 'project', label: '项目展示', desc: '看见成果', icon: 'project', tone: 'var(--kh-accent)', to: '/projects' },
  { key: 'resource', label: '资源推荐', desc: '精选好物', icon: 'resource', tone: 'var(--kh-warm)', to: '/resources' },
  { key: 'doc', label: '文档学习', desc: '系统进阶', icon: 'doc', tone: 'var(--kh-success)', to: '/articles' },
] as const

/** Hero 搜索框：跳全局搜索结果页，空关键词不跳 */
const searchValue = ref('')
const goSearch = () => {
  const kw = searchValue.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw } })
}
</script>

<template>
  <div class="home">
    <!-- —— Hero —— -->
    <section class="hero">
      <div class="hero__bg" aria-hidden="true">
        <div class="hero__blob hero__blob--1" />
        <div class="hero__blob hero__blob--2" />
      </div>
      <div class="kh-container kh-container--wide hero__inner">
        <div class="hero__text">
          <span class="hero__badge">
            <el-icon><Promotion /></el-icon> 实验室知识沉淀与项目展示
          </span>
          <h1 class="hero__title">
            学以<span class="hero__title-grad">记之</span>，研以<span class="hero__title-grad">展之</span>
          </h1>
          <p class="hero__subtitle">
            知枢 knowhub —— 高校实验室与中小组织的综合知识库。写博客、攒项目、传资源、学文档，让实验室的智慧被看见。
          </p>

          <div class="hero__search">
            <el-icon class="hero__search-icon"><Search /></el-icon>
            <input v-model="searchValue" class="hero__search-input" placeholder="搜索博客、项目、资源、文档…" @keyup.enter="goSearch" />
            <button class="hero__search-btn" type="button" @click="goSearch">搜索</button>
          </div>

          <!-- 内容类别徽章：标明系统里有什么资源 -->
          <div class="hero__kinds">
            <RouterLink v-for="k in contentKinds" :key="k.key" :to="k.to" class="hero__kind" :style="{ '--kind-tone': k.tone }">
              <span class="hero__kind-icon">
                <KhIcon :name="k.icon" :size="15" />
              </span>
              <span class="hero__kind-text">
                <span class="hero__kind-label">{{ k.label }}</span>
                <span class="hero__kind-desc">{{ k.desc }}</span>
              </span>
            </RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- —— 公告轮播条 —— -->
    <section class="kh-container kh-container--wide notice-bar">
      <div class="notice-bar__icon">
        <KhIcon name="megaphone" :size="18" />
      </div>
      <el-carousel
        v-if="pinnedNotices.length"
        height="44px"
        direction="vertical"
        :autoplay="true"
        indicator-position="none"
        arrow="never"
        class="notice-bar__carousel"
      >
        <el-carousel-item v-for="n in pinnedNotices" :key="n.noticeId">
          <button
            type="button"
            class="notice-bar__item"
            :title="`查看公告：${n.title}`"
            @click="noticeStore.openDetail(n)"
          >
            <KhTag
              size="sm"
              :type="n.noticeType === 'NOTIFY' ? 'warning' : n.noticeType === 'REMIND' ? 'primary' : 'warm'"
            >{{ resolveNoticeType(n.noticeType) }}</KhTag>
            <span class="notice-bar__title">{{ n.title }}</span>
            <span class="notice-bar__time">{{ n.publishTime }}</span>
          </button>
        </el-carousel-item>
      </el-carousel>
      <span v-else class="notice-bar__empty">暂无公告</span>
      <RouterLink to="/notices" class="notice-bar__more">全部公告 <el-icon><ArrowRight /></el-icon></RouterLink>
    </section>

    <!-- —— 主体两栏 —— -->
    <section class="kh-container kh-container--wide home__body">
      <!-- 左主列：内容 feed -->
      <div class="home__main">
        <!-- 最新笔记（横条列表） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[0]!.tone }">{{ sections[0]!.no }}</span>
              <KhIcon :name="sections[0]!.icon" :size="20" :style="{ color: sections[0]!.tone }" />
              <h2>{{ sections[0]!.title }}</h2>
            </div>
            <RouterLink to="/blogs" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__blog-list">
            <BlogRow v-for="b in latestBlogs" :key="b.blogId" :blog="b" />
          </div>
        </section>

        <!-- 活跃项目（网格，无横滚） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[1]!.tone }">{{ sections[1]!.no }}</span>
              <KhIcon :name="sections[1]!.icon" :size="20" :style="{ color: sections[1]!.tone }" />
              <h2>{{ sections[1]!.title }}</h2>
            </div>
            <RouterLink to="/projects" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__project-grid">
            <ProjectCard v-for="p in hotProjects" :key="p.projectId" :project="p" />
          </div>
        </section>

        <!-- 最新文档（网格，3 列 2 行） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[2]!.tone }">{{ sections[2]!.no }}</span>
              <KhIcon :name="sections[2]!.icon" :size="20" :style="{ color: sections[2]!.tone }" />
              <h2>{{ sections[2]!.title }}</h2>
            </div>
            <RouterLink to="/articles" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__article-grid">
            <ArticleCard v-for="a in latestArticles" :key="a.articleId" :doc="a" />
          </div>
        </section>

        <!-- 资源推荐（网格） -->
        <section class="feed-section">
          <header class="feed-section__head">
            <div class="feed-section__title">
              <span class="feed-section__no" :style="{ color: sections[3]!.tone }">{{ sections[3]!.no }}</span>
              <KhIcon :name="sections[3]!.icon" :size="20" :style="{ color: sections[3]!.tone }" />
              <h2>{{ sections[3]!.title }}</h2>
            </div>
            <RouterLink to="/resources" class="feed-section__more">
              查看更多 <el-icon><ArrowRight /></el-icon>
            </RouterLink>
          </header>
          <div class="home__res-grid">
            <ResourceCard v-for="r in featuredResources" :key="r.resourceId" :resource="r" />
          </div>
        </section>
      </div>

      <!-- 右侧栏 -->
      <aside class="home__aside">
        <!-- AI 日报 -->
        <!-- AI 日报（功能即将上线占位） -->
        <KhCard gradient padding="lg" class="ai-card">
          <div class="ai-card__head">
            <span class="ai-card__tag"><KhIcon name="sparkles" :size="14" /> AI 日报</span>
            <span class="ai-card__date">敬请期待</span>
          </div>
          <h3 class="ai-card__title">{{ aiDaily.title }}</h3>
          <p class="ai-card__summary kh-line-clamp-3">{{ aiDaily.summary }}</p>
          <div class="ai-card__topics">
            <span v-for="t in aiDaily.topics" :key="t">{{ t }}</span>
          </div>
          <button class="ai-card__more is-coming-soon" type="button" disabled title="功能即将上线">
            即将上线 <el-icon><ArrowRight /></el-icon>
          </button>
        </KhCard>

        <!-- 热门资源榜 + 热门标签：随滚动固定（sticky），AI 日报不固定、自然滚走 -->
        <div class="home__aside-sticky">
        <!-- 热门资源榜 -->
        <KhCard padding="md" class="home__rank">
          <div class="home__rank-head">
            <KhIcon name="trending" :size="16" />
            <h3>热门资源榜</h3>
          </div>
          <ol class="home__rank-list">
            <li
              v-for="(r, i) in hotResourceRank"
              :key="r.resourceId"
              class="home__rank-item"
              @click="router.push(`/resource/${r.resourceId}`)"
            >
              <span class="home__rank-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <div class="home__rank-icon" :style="{ background: rankCoverGradient(r) }">
                <KhIcon :name="rankIcon(r)" :size="14" />
              </div>
              <div class="home__rank-text">
                <div class="home__rank-title kh-line-clamp-1">{{ r.title }}</div>
                <div class="home__rank-meta">
                  <!-- 链接型无下载语义（不计下载量/无大小），显浏览；文件型显下载 + 大小 -->
                  <template v-if="r.resourceType === 'LINK'">{{ r.viewCount ?? 0 }} 浏览</template>
                  <template v-else>{{ r.downloadCount ?? 0 }} 下载 · {{ formatSize(r.contentLength) }}</template>
                </div>
              </div>
            </li>
          </ol>
        </KhCard>

        <!-- 热门标签（紧凑榜，非大标签云） -->
        <KhCard padding="md" class="home__rank">
          <div class="home__rank-head">
            <KhIcon name="tag" :size="16" />
            <h3>热门标签</h3>
          </div>
          <ol class="home__tag-list">
            <li
              v-for="(t, i) in hotTags"
              :key="t.tagId"
              class="home__tag-item"
              @click="router.push('/blogs')"
            >
              <span class="home__tag-no" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
              <span class="home__tag-name">
                <span class="home__tag-name-inner" :class="{ 'is-marquee': isTagNameLong(t.tagName) }">
                  <template v-if="isTagNameLong(t.tagName)">
                    <span class="home__tag-name-unit">{{ t.tagName }}</span>
                    <span class="home__tag-name-unit">{{ t.tagName }}</span>
                  </template>
                  <template v-else>{{ t.tagName }}</template>
                </span>
              </span>
              <span class="home__tag-bar">
                <span class="home__tag-bar-fill" :style="{ width: `${((t.hotScore ?? 0) / topTagScore()) * 100}%` }" />
              </span>
              <span class="home__tag-count">{{ t.hotScore }}</span>
            </li>
          </ol>
        </KhCard>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
/* —— Hero（含生动微动画） —— */
.hero {
  position: relative;
  overflow: hidden;
  padding: var(--kh-space-16) 0 var(--kh-space-12);
  background: var(--kh-gradient-hero);
}
.hero__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.hero__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.4;
  will-change: transform;
}
.hero__blob--1 {
  width: 320px;
  height: 320px;
  background: rgba(37, 99, 235, 0.3);
  top: -100px;
  right: 6%;
}
.hero__blob--2 {
  width: 260px;
  height: 260px;
  background: rgba(245, 158, 11, 0.22);
  bottom: -80px;
  left: 8%;
}
.hero__inner {
  position: relative;
  max-width: 760px;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  color: var(--kh-text-secondary);
  font-size: 12px;
  font-weight: 500;
  box-shadow: var(--kh-shadow-xs);
}
.hero__title {
  margin-top: var(--kh-space-5);
  font-size: var(--kh-font-size-hero);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--kh-text);
  line-height: 1.12;
}
.hero__title-grad {
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-accent) 50%, var(--kh-warm), var(--kh-primary));
  background-size: 220% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: kh-grad-flow 7s linear infinite;
}
.hero__subtitle {
  margin-top: var(--kh-space-4);
  font-size: var(--kh-font-size-md);
  color: var(--kh-text-secondary);
  line-height: 1.7;
}
.hero__search {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-6);
  height: 60px;
  padding: 0 6px 0 var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-pill);
  box-shadow: var(--kh-shadow-sm);
  transition: border-color var(--kh-transition), box-shadow var(--kh-transition);
}
.hero__search:focus-within {
  border-color: var(--kh-primary-border);
  box-shadow: var(--kh-focus-ring);
}
.hero__search-icon {
  color: var(--kh-text-tertiary);
  font-size: 20px;
}
.hero__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--kh-font-size-md);
  color: var(--kh-text);
}
.hero__search-btn {
  height: 48px;
  padding: 0 var(--kh-space-6);
  border: none;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  font-weight: 600;
  font-size: var(--kh-font-size-md);
  cursor: pointer;
  transition: transform var(--kh-transition-fast), box-shadow var(--kh-transition-fast);
}
.hero__search-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--kh-shadow-primary);
}

/* 内容类别徽章：标明系统有什么资源 */
.hero__kinds {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--kh-space-3);
  margin-top: var(--kh-space-6);
  max-width: 680px;
}
.hero__kind {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-4);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  box-shadow: var(--kh-shadow-xs);
  cursor: pointer;
  transition:
    transform var(--kh-transition-fast),
    box-shadow var(--kh-transition-fast),
    border-color var(--kh-transition-fast);
}
.hero__kind:hover {
  transform: translateY(-3px);
  box-shadow: var(--kh-shadow-sm);
  border-color: var(--kind-tone, var(--kh-border-strong));
}
.hero__kind-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  flex: none;
  color: var(--kind-tone, var(--kh-primary));
  background: color-mix(in srgb, var(--kind-tone, var(--kh-primary)) 12%, transparent);
}
.hero__kind-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}
.hero__kind-label {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.hero__kind-desc {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 1px;
}

/* —— Hero 微动效 ——
   1) blob 缓慢漂浮（错峰，纯 transform，不触发重排）
   2) 标题渐变流光（background-position 循环）
   3) Hero 内容依次淡入上浮（一次性，forwards 锁定终态）
*/
.hero__blob--1 {
  animation: kh-blob-1 16s ease-in-out infinite;
}
.hero__blob--2 {
  animation: kh-blob-2 20s ease-in-out infinite;
}
.hero__badge,
.hero__title,
.hero__subtitle,
.hero__search,
.hero__kinds {
  opacity: 0;
  transform: translateY(12px);
  animation: kh-hero-in 0.6s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.hero__badge { animation-delay: 0.08s; }
.hero__title { animation-delay: 0.18s; }
.hero__subtitle { animation-delay: 0.30s; }
.hero__search { animation-delay: 0.42s; }
.hero__kinds { animation-delay: 0.54s; }

@keyframes kh-blob-1 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(-18px, 22px, 0) scale(1.06); }
}
@keyframes kh-blob-2 {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(20px, -16px, 0) scale(1.05); }
}
@keyframes kh-grad-flow {
  0% { background-position: 0% 0; }
  100% { background-position: 220% 0; }
}
@keyframes kh-hero-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 动画无障碍降级 */
@media (prefers-reduced-motion: reduce) {
  .hero__blob--1,
  .hero__blob--2,
  .hero__title-grad {
    animation: none;
  }
  .hero__badge,
  .hero__title,
  .hero__subtitle,
  .hero__search,
  .hero__kinds {
    opacity: 1;
    transform: none;
    animation: none;
  }
}

/* —— 公告条 —— */
.notice-bar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  margin-top: -28px;
  padding: 10px var(--kh-space-5);
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  box-shadow: var(--kh-shadow-sm);
  position: relative;
  z-index: 2;
}
.notice-bar__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--kh-radius-sm);
  background: var(--kh-warm-soft);
  color: var(--kh-warm);
  flex: none;
}
.notice-bar__carousel {
  flex: 1;
  overflow: hidden;
}
.notice-bar__item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  height: 100%;
  /* 轮播条项现在是 <button>：清掉默认 button 样式，让它读起来像文字链接 */
  border: none;
  background: transparent;
  padding: 0;
  width: 100%;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: color var(--kh-transition-fast);
}
.notice-bar__item:hover .notice-bar__title {
  color: var(--kh-primary);
}
.notice-bar__title {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.notice-bar__time {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  flex: none;
}
.notice-bar__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-primary);
  font-weight: 500;
  cursor: pointer;
  flex: none;
}
.notice-bar__empty {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  flex: 1;
}

/* —— 主体两栏 —— */
.home__body {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-10);
  padding-bottom: var(--kh-space-12);
}
.home__main {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-10);
  min-width: 0;
}

/* 内容分区（带序号+图标头，增强层次） */
.feed-section {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
}
.feed-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--kh-space-3);
  border-bottom: 1px solid var(--kh-border-soft);
}
.feed-section__title {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.feed-section__no {
  font-family: var(--kh-font-mono);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  opacity: 0.7;
}
.feed-section__title h2 {
  font-size: var(--kh-font-size-2xl);
  font-weight: 700;
  color: var(--kh-text);
  letter-spacing: -0.01em;
}
.feed-section__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-primary);
  font-weight: 500;
  cursor: pointer;
  transition: gap var(--kh-transition-fast);
}
.feed-section__more:hover {
  gap: 8px;
}

.home__blog-list {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-4);
}
.home__project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--kh-space-5);
}
.home__article-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--kh-space-5);
}
.home__res-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--kh-space-4);
}

/* —— 右侧栏 —— */
.home__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  /* 拉满 grid 行高，让内部 sticky 子块相对 aside 可滚动固定（aside 与左主列等高） */
  align-self: stretch;
}
/* 热门资源榜 + 热门标签：相对 aside 固定（随滚动），AI 日报在它之前自然滚走 */
.home__aside-sticky {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}

/* AI 日报卡 */
.ai-card {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.ai-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-card__tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(120deg, var(--kh-warm), #fbbf24);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.ai-card__date {
  font-family: var(--kh-font-mono);
  font-size: 12px;
  color: var(--kh-text-tertiary);
}
.ai-card__title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  line-height: 1.5;
}
.ai-card__summary {
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-secondary);
  line-height: 1.7;
}
.ai-card__topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ai-card__topics span {
  font-size: 11px;
  padding: 2px 9px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-primary-soft);
  color: var(--kh-primary-strong);
}
.ai-card__more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: var(--kh-primary);
  font-weight: 600;
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  align-self: flex-start;
  transition: gap var(--kh-transition-fast);
}
.ai-card__more:hover {
  gap: 10px;
}
.ai-card__more.is-coming-soon {
  opacity: 0.7;
  cursor: not-allowed;
}
.ai-card__more.is-coming-soon:hover {
  gap: 6px;
}

/* 侧栏面板头 */
.home__rank-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--kh-space-4);
  color: var(--kh-text-secondary);
}
.home__rank-head h3 {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text);
}

/* 热门资源榜 */
.home__rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.home__rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.home__rank-item:hover {
  background: var(--kh-surface-muted);
}
.home__rank-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  text-align: center;
  flex: none;
}
.home__rank-no.is-top {
  color: var(--kh-warm);
}
.home__rank-icon {
  width: 30px;
  height: 30px;
  border-radius: var(--kh-radius-sm);
  display: grid;
  place-items: center;
  color: #fff;
  flex: none;
}
.home__rank-text {
  flex: 1;
  min-width: 0;
}
.home__rank-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.home__rank-meta {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
  font-family: var(--kh-font-mono);
}

/* 热门标签榜（竖排紧凑榜，非大标签云） */
.home__tag-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
.home__tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: var(--kh-radius-sm);
  transition: background var(--kh-transition-fast);
}
.home__tag-item:hover {
  background: var(--kh-surface-muted);
}
.home__tag-no {
  font-family: var(--kh-font-display);
  font-weight: 700;
  font-size: 13px;
  color: var(--kh-text-tertiary);
  width: 18px;
  text-align: center;
  flex: none;
}
.home__tag-no.is-top {
  color: var(--kh-warm);
}
.home__tag-name {
  /* 名称容器固定宽，超出该宽的标签名在内部横滚，序号与热度条不动 */
  width: 72px;
  flex: none;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-text);
}
.home__tag-name-inner {
  display: inline-block;
  white-space: nowrap;
}
/* 长标签名循环滚动：两个相同 unit（各带 margin-right 间隔）横向平移 -50% 实现无缝循环
   （hover 暂停便于看清）。unit 间留 4em 间隔，避免副本首尾粘连。
   mask 仅挂在滚动态 inner 上——短名不滚，左右清晰不虚化。 */
.home__tag-name-inner.is-marquee {
  animation: kh-tag-name-marquee 14s linear infinite;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
}
.home__tag-name-inner.is-marquee:hover {
  animation-play-state: paused;
}
.home__tag-name-unit {
  /* 每个 unit 后留 4em 间隔，-50% 平移正好滚一个 unit+间隔，两份副本首尾衔接处也有间隔 */
  margin-right: 4em;
}
@keyframes kh-tag-name-marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
.home__tag-bar {
  flex: 1;
  height: 6px;
  border-radius: var(--kh-radius-pill);
  background: var(--kh-bg-soft);
  overflow: hidden;
}
.home__tag-bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--kh-radius-pill);
  background: linear-gradient(90deg, var(--kh-primary), var(--kh-accent));
}
.home__tag-count {
  font-family: var(--kh-font-mono);
  font-size: 11px;
  color: var(--kh-text-tertiary);
  width: 24px;
  text-align: right;
  flex: none;
}
@media (prefers-reduced-motion: reduce) {
  .home__tag-name-inner.is-marquee {
    animation: none;
  }
}

/* —— 响应式 —— */
@media (max-width: 1024px) {
  .home__body {
    grid-template-columns: 1fr;
  }
  .home__project-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .home__article-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 768px) {
  .hero {
    padding: var(--kh-space-10) 0 var(--kh-space-8);
  }
  .hero__title {
    font-size: var(--kh-font-size-4xl);
  }
  .hero__search {
    height: 52px;
    padding-left: var(--kh-space-4);
  }
  .hero__search-btn {
    height: 40px;
    padding: 0 var(--kh-space-5);
    font-size: var(--kh-font-size-sm);
  }
  .hero__kinds {
    grid-template-columns: repeat(2, 1fr);
  }
  .home__project-grid {
    grid-template-columns: 1fr;
  }
  .home__article-grid {
    grid-template-columns: 1fr;
  }
  .notice-bar__more {
    display: none;
  }
}
</style>